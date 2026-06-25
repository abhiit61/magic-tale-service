package com.fusion.psb.imagemodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.*;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HuggingFaceImageModel implements ImageModel {

  private final RestClient restClient;
  private final String model;
  private static final Logger LOGGER = LoggerFactory.getLogger(HuggingFaceImageModel.class);


  public HuggingFaceImageModel(String apiKey, String model) {
    this.model = model;
    this.restClient = RestClient.builder()
        .baseUrl("https://router.huggingface.co/hf-inference")
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .build();
  }

  @Override
  public ImageResponse call(ImagePrompt request) {
    LOGGER.info("Calling huggingface image model");

    // Extract prompt string from Spring AI instruction wrappers
    String promptText = request.getInstructions().get(0).getText();

    // Hugging Face inference API accepts json mapping payload: {"inputs": "..."}
    Map<String, String> requestBody = Collections.singletonMap("inputs", promptText);

    // Execute raw POST request to download the generated image binary bytes
    byte[] imageBytes = restClient.post()
        .uri("/models/{model}", this.model)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .body(byte[].class);

    if (imageBytes == null || imageBytes.length == 0) {
      throw new RuntimeException("Hugging Face API returned empty image payload response.");
    }

    String encoded = java.util.Base64.getEncoder().encodeToString(imageBytes);
    org.springframework.ai.image.Image image = new org.springframework.ai.image.Image(null, encoded);
    ImageGeneration imageGeneration = new ImageGeneration(image);
    ImageResponseMetadata metadata = new ImageResponseMetadata(System.currentTimeMillis());

    LOGGER.info("Returning from huggingface image model");
    return new ImageResponse(List.of(imageGeneration), metadata);
  }
}
