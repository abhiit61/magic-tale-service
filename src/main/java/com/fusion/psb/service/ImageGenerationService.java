package com.fusion.psb.service;

import com.fusion.psb.entity.ImageCache;
import com.fusion.psb.repository.ImageCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ImageGenerationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageGenerationService.class);

  private final ImageModel imageModel;
  private final ImageCacheRepository imageCacheRepository;

  @Autowired
  public ImageGenerationService(@Autowired(required = false) ImageModel imageModel,
                                ImageCacheRepository imageCacheRepository) {
    this.imageModel = imageModel;
    this.imageCacheRepository = imageCacheRepository;
  }

  /**
   * Returns a storybook image for the given description.
   * Checks the database cache first; calls DALL-E only on a cache miss.
   * Returns null if generation is unavailable or fails.
   */
  public byte[] generateStorybookImage(String description) {
    String hash = sha256(description);

    // Cache lookup
    Optional<ImageCache> cached = imageCacheRepository.findByDescriptionHash(hash);
    if (cached.isPresent()) {
      LOGGER.info("Image cache hit for description hash {}", hash);
      return cached.get().getImageData();
    }

    // Cache miss — generate via AI
    if (imageModel == null) {
      LOGGER.warn("ImageModel not available — skipping image generation. Set image.model=openai and ensure OPENAI_API_KEY is set.");
      return null;
    }

    try {
      String prompt = "Children's storybook illustration, colorful cartoon style, warm friendly tones, " +
          "soft watercolor look, suitable for young children: " + description;

      ImageResponse response = imageModel.call(new ImagePrompt(prompt));
      String b64 = response.getResult().getOutput().getB64Json();

      if (b64 == null || b64.isBlank()) {
        LOGGER.warn("Image generation returned empty response for hash {}", hash);
        return null;
      }

      byte[] imageBytes = Base64.getDecoder().decode(b64);

      // Persist to cache
      ImageCache entry = new ImageCache();
      entry.setDescriptionHash(hash);
      entry.setDescription(description);
      entry.setImageData(imageBytes);
      entry.setCreatedAt(LocalDateTime.now());
      imageCacheRepository.save(entry);
      LOGGER.info("Image generated and cached for description hash {}", hash);

      return imageBytes;

    } catch (Exception e) {
      LOGGER.warn("Image generation failed for hash '{}': {}", hash, e.getMessage());
      return null;
    }
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      // Fallback: use hashCode as hex (extremely unlikely to hit)
      return Integer.toHexString(input.hashCode());
    }
  }
}
