package com.fusion.psb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.*;

import java.util.List;
import java.util.stream.Collectors;

public class LeonardoImageModel implements ImageModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(LeonardoImageModel.class);

  private final LeonardoImageService leonardoImageService;

  public LeonardoImageModel(LeonardoImageService leonardoImageService) {
    this.leonardoImageService = leonardoImageService;
  }

  @Override
  public ImageResponse call(ImagePrompt request) {
    String promptText = extractPromptText(request);
    LOGGER.info("LeonardoImageModel.call() prompt='{}'", promptText);
    byte[] imageBytes = leonardoImageService.generateImage(promptText);
    if (imageBytes == null || imageBytes.length == 0) {
      throw new RuntimeException("Leonardo image generation returned no image.");
    }

    String encoded = java.util.Base64.getEncoder().encodeToString(imageBytes);
    org.springframework.ai.image.Image image = new org.springframework.ai.image.Image(null, encoded);
    ImageGeneration imageGeneration = new ImageGeneration(image);
    ImageResponseMetadata metadata = new ImageResponseMetadata(System.currentTimeMillis());
    return new ImageResponse(List.of(imageGeneration), metadata);
  }

  private String extractPromptText(ImagePrompt request) {
    if (request == null) {
      throw new IllegalArgumentException("ImagePrompt is required");
    }
    List<ImageMessage> instructions = request.getInstructions();
    if (instructions == null || instructions.isEmpty()) {
      throw new IllegalArgumentException("ImagePrompt must contain at least one ImageMessage.");
    }
    return instructions.stream()
        .map(ImageMessage::getText)
        .collect(Collectors.joining(" "));
  }

  @Override
  public String toString() {
    return "LeonardoImageModel";
  }
}
