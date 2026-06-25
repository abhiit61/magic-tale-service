package com.fusion.psb.service;

import com.fusion.psb.imagemodel.HuggingFaceImageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HuggingFaceImageModelTest {

  private HuggingFaceImageModel huggingFaceImageModel;

  @Mock
  private RestClient mockRestClient;

  @Mock
  private RestClient.RequestBodyUriSpec mockRequestBodyUriSpec;

  @Mock
  private RestClient.RequestBodySpec mockRequestBodySpec;

  @Mock
  private RestClient.ResponseSpec mockResponseSpec;

  @Value("${huggingface.api-key}")
  private final String testApiKey;
  private final String testModelName = "black-forest-labs/FLUX.1-schnell";

  HuggingFaceImageModelTest(String testApiKey) {
    this.testApiKey = testApiKey;
  }

  @BeforeEach
  void setUp() {
    // Instantiate the model
    huggingFaceImageModel = new HuggingFaceImageModel(testApiKey, testModelName);

    // Inject our mocked RestClient into the class instance to isolate network logic
    ReflectionTestUtils.setField(huggingFaceImageModel, "restClient", mockRestClient);
  }

  @Test
  void testCall_Success_ReturnsEncodedBytes() {
    // Arrange
    String userPrompt = "A magical white cat inside a forest";
    ImagePrompt imagePrompt = new ImagePrompt(userPrompt);

    // Mock data matching what a real image would look like in raw bytes
    byte[] expectedImageBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    String expectedBase64String = Base64.getEncoder().encodeToString(expectedImageBytes);

    // Mock the RestClient fluid API chain
    when(mockRestClient.post()).thenReturn(mockRequestBodyUriSpec);
    when(mockRequestBodyUriSpec.uri("/" + testModelName)).thenReturn(mockRequestBodySpec);
    when(mockRequestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(mockRequestBodySpec);
    when(mockRequestBodySpec.body(ArgumentMatchers.<Map<String, String>>any())).thenReturn(mockRequestBodySpec);
    when(mockRequestBodySpec.retrieve()).thenReturn(mockResponseSpec);
    when(mockResponseSpec.body(byte[].class)).thenReturn(expectedImageBytes);

    // Act
    ImageResponse response = huggingFaceImageModel.call(imagePrompt);

    // Assert
    assertNotNull(response);
    assertNotNull(response.getResult());
    assertFalse(response.getResults().isEmpty());

    String actualOutputUrlString = response.getResult().getOutput().getB64Json();
    assertEquals(expectedBase64String, actualOutputUrlString);

    // Verify the byte array decodes back perfectly to original mock data
    byte[] decodedBytes = Base64.getDecoder().decode(actualOutputUrlString);
    assertArrayEquals(expectedImageBytes, decodedBytes);
  }

  @Test
  void testCall_EmptyBytesResponse_ThrowsRuntimeException() {
    // Arrange
    ImagePrompt imagePrompt = new ImagePrompt("Sample prompt");

    // Set up the mock to return an empty array simulating an API failure or timeout
    when(mockRestClient.post()).thenReturn(mockRequestBodyUriSpec);
    when(mockRequestBodyUriSpec.uri(anyString())).thenReturn(mockRequestBodySpec);
    when(mockRequestBodySpec.contentType(any(MediaType.class))).thenReturn(mockRequestBodySpec);
    when(mockRequestBodySpec.body(any())).thenReturn(mockRequestBodySpec);
    when(mockRequestBodySpec.retrieve()).thenReturn(mockResponseSpec);
    when(mockResponseSpec.body(byte[].class)).thenReturn(new byte[0]); // Empty response

    // Act & Assert
    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
      huggingFaceImageModel.call(imagePrompt);
    });

    assertTrue(exception.getMessage().contains("Hugging Face API returned empty image payload"));
  }
}
