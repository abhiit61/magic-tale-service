package com.fusion.psb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class HuggingFaceITTest {

  @Autowired
  @Qualifier("huggingface-image")
  private ImageModel huggingFaceImageModel;

  @Test
  void testLiveGeneration_ShouldReturnValidDecodableData() {
    // Arrange
    String promptText = "A single red apple on an empty clean desk, hyper-realistic vector image style";
    ImagePrompt prompt = new ImagePrompt(promptText);

    // Act
    ImageResponse response = huggingFaceImageModel.call(prompt);

    // Assert
    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getResult(), "Generation result wrapper should exist");

    String urlOutputPayload = response.getResult().getOutput().getB64Json();
    assertNotNull(urlOutputPayload, "The generated data payload string should not be null");

    // Verify that the output is indeed a valid Base64 string that decodes cleanly back to raw bytes
    assertDoesNotThrow(() -> {
      byte[] rawImageBytes = Base64.getDecoder().decode(urlOutputPayload);
      assertTrue(rawImageBytes.length > 0, "Decoded binary image should contain byte length content");
    }, "The output string from the model must be valid Base64 format for iText compilation");
  }
}
