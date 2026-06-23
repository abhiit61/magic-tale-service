package com.fusion.psb.service;

import com.fusion.psb.entity.ImageCache;
import com.fusion.psb.repository.ImageCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ImageGenerationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageGenerationService.class);

  private static final String STORYBOOK_PROMPT_PREFIX =
      "Children's storybook illustration, colorful cartoon style, warm friendly tones, " +
      "soft watercolor look, suitable for young children: ";

  @Value("${image.model}")
  private String imageModelConfig;

  private final ImageModel           imageModel;
  private final LeonardoImageService leonardoImageService;
  private final ImageCacheRepository imageCacheRepository;
  private final RestTemplate         restTemplate;

  @Autowired
  public ImageGenerationService(@Autowired(required = false) ImageModel imageModel,
                                LeonardoImageService leonardoImageService,
                                ImageCacheRepository imageCacheRepository,
                                RestTemplate restTemplate) {
    this.imageModel           = imageModel;
    this.leonardoImageService = leonardoImageService;
    this.imageCacheRepository = imageCacheRepository;
    this.restTemplate         = restTemplate;
  }

  public byte[] generateStorybookImage(String description) {
    String hash = sha256(description);

    // Cache lookup — shared across all providers
    Optional<ImageCache> cached = imageCacheRepository.findByDescriptionHash(hash);
    if (cached.isPresent()) {
      LOGGER.info("Image cache hit for hash {}", hash);
      return cached.get().getImageData();
    }

    String prompt     = STORYBOOK_PROMPT_PREFIX + description;
    byte[] imageBytes = switch (imageModelConfig.toLowerCase()) {
      case "openai"    -> generateViaOpenAi(prompt, hash);
      case "leonardo"  -> leonardoImageService.generateImage(prompt);
      default          -> {
        LOGGER.info("Image generation disabled (image.model={}).", imageModelConfig);
        yield null;
      }
    };

    if (imageBytes != null) {
      ImageCache entry = new ImageCache();
      entry.setDescriptionHash(hash);
      entry.setDescription(description);
      entry.setImageData(imageBytes);
      entry.setCreatedAt(LocalDateTime.now());
      imageCacheRepository.save(entry);
      LOGGER.info("Image generated and cached — provider={}, hash={}", imageModelConfig, hash);
    }

    return imageBytes;
  }

  // ── OpenAI (Spring AI ImageModel) ────────────────────────────────────────

  private byte[] generateViaOpenAi(String prompt, String hash) {
    if (imageModel == null) {
      LOGGER.warn("OpenAI ImageModel not available — check OPENAI_API_KEY.");
      return null;
    }
    try {
      ImageResponse response   = imageModel.call(new ImagePrompt(prompt));
      byte[]        imageBytes = extractImageBytes(response, hash);
      return imageBytes;
    } catch (Exception e) {
      LOGGER.warn("OpenAI image generation failed for hash '{}': {}", hash, e.getMessage());
      return null;
    }
  }

  private byte[] extractImageBytes(ImageResponse response, String hash) {
    String b64 = response.getResult().getOutput().getB64Json();
    if (b64 != null && !b64.isBlank()) {
      return Base64.getDecoder().decode(b64);
    }
    String url = response.getResult().getOutput().getUrl();
    if (url != null && !url.isBlank()) {
      LOGGER.info("Downloading OpenAI image from URL for hash {}", hash);
      return restTemplate.getForObject(url, byte[].class);
    }
    LOGGER.warn("OpenAI response contained neither b64 nor URL for hash {}", hash);
    return null;
  }

  // ── Shared ────────────────────────────────────────────────────────────────

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      return Integer.toHexString(input.hashCode());
    }
  }
}
