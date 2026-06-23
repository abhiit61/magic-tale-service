package com.fusion.psb.service;

import com.fusion.psb.entity.ImageCache;
import com.fusion.psb.repository.ImageCacheRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import java.util.*;

@Service
public class ImageGenerationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageGenerationService.class);

  private static final String STORYBOOK_PROMPT_PREFIX =
      "Children's storybook illustration, colorful cartoon style, warm friendly tones, " +
      "soft watercolor look, suitable for young children: ";

  private final ImageModel           imageModel;
  private final LeonardoImageService leonardoImageService;
  private final ImageCacheRepository imageCacheRepository;
  private final RestTemplate         restTemplate;

  private final String imageModelConfig;
  private final CircuitBreaker openaiImageBreaker;
  private final CircuitBreaker leonardoBreaker;
  private final Random random = new Random();

  @Autowired
  public ImageGenerationService(@Autowired(required = false) ImageModel imageModel,
                                LeonardoImageService leonardoImageService,
                                ImageCacheRepository imageCacheRepository,
                                RestTemplate restTemplate,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                @Value("${image.model}") String imageModelConfig) {
    this.imageModel           = imageModel;
    this.leonardoImageService = leonardoImageService;
    this.imageCacheRepository = imageCacheRepository;
    this.restTemplate         = restTemplate;
    this.imageModelConfig     = imageModelConfig.toLowerCase();
    this.openaiImageBreaker = circuitBreakerRegistry.circuitBreaker("openai-image");
    this.leonardoBreaker = circuitBreakerRegistry.circuitBreaker("leonardo");
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

    List<String> allowedModelNames = switch (imageModelConfig) {
      case "openai" -> List.of("openai-image");
      case "leonardo" -> List.of("leonardo");
      case "random", "all" -> List.of("openai-image", "leonardo");
      default -> throw new IllegalArgumentException(
          "Unsupported image.model value: '" + imageModelConfig + "'. Supported values: openai, leonardo, none, random");
    };

    List<CircuitBreaker> availableModels = new ArrayList<>();
    for (String provider : allowedModelNames) {
      CircuitBreaker breaker = provider.equals("openai-image") ? openaiImageBreaker : leonardoBreaker;
      if (breaker.getState() == CircuitBreaker.State.CLOSED && isProviderAvailable(provider)) {
        availableModels.add(breaker);
      }
    }

    if (availableModels.isEmpty()) {
      throw new RuntimeException("All image models are unavailable. Please try again later.");
    }

    CircuitBreaker selectedBreaker = availableModels.get(random.nextInt(availableModels.size()));

    byte[] imageBytes;
    try {
      imageBytes = CircuitBreaker.decorateSupplier(selectedBreaker,
              () -> callImageModel(selectedBreaker.getName(), prompt, hash)).get();
    } catch (Exception e) {
      LOGGER.error("Error generating image with model {}: {}", selectedBreaker.getName(), e.getMessage(), e);
      throw new RuntimeException("Image generation failed: " + e.getMessage(), e);
    }

    if (imageBytes != null) {
      ImageCache entry = new ImageCache();
      entry.setDescriptionHash(hash);
      entry.setDescription(description);
      entry.setImageData(imageBytes);
      entry.setCreatedAt(LocalDateTime.now());
      imageCacheRepository.save(entry);
      LOGGER.info("Image generated and cached — provider={}, hash={}", selectedBreaker.getName(), hash);
    }

    return imageBytes;
  }

  private byte[] callImageModel(String modelName, String prompt, String hash) {
    LOGGER.info("Generating image with model: {}", modelName);
    byte[] imageBytes = switch (modelName) {
      case "openai-image" -> generateViaOpenAi(prompt, hash);
      case "leonardo" -> leonardoImageService.generateImage(prompt);
      default -> throw new IllegalArgumentException("Unsupported image provider: " + modelName);
    };

    if (imageBytes == null || imageBytes.length == 0) {
      throw new RuntimeException("Image generation returned no image for " + modelName);
    }
    return imageBytes;
  }


  // ── OpenAI (Spring AI ImageModel) ────────────────────────────────────────

  private byte[] generateViaOpenAi(String prompt, String hash) {
    if (imageModel == null) {
      throw new IllegalStateException("OpenAI ImageModel not available — check OPENAI_API_KEY and image.model settings.");
    }
    try {
      ImageResponse response = imageModel.call(new ImagePrompt(prompt));
      byte[] imageBytes = extractImageBytes(response, hash);
      if (imageBytes == null || imageBytes.length == 0) {
        throw new RuntimeException("OpenAI image generation returned no image for hash '" + hash + "'.");
      }
      return imageBytes;
    } catch (Exception e) {
      LOGGER.warn("OpenAI image generation failed for hash '{}': {}", hash, e.getMessage(), e);
      throw new RuntimeException("OpenAI image generation failed: " + e.getMessage(), e);
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

  private boolean isProviderAvailable(String modelName) {
    return switch (modelName) {
      case "openai-image" -> imageModel != null;
      case "leonardo" -> true;
      default -> false;
    };
  }

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
