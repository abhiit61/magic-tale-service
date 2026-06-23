package com.fusion.psb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Leonardo AI image generation.
 * Requires LEONARDO_API_KEY environment variable to be set.
 * Run with: LEONARDO_API_KEY=<key> mvn test -Dtest=LeonardoImageServiceTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("leonardo-test")
@EnabledIfEnvironmentVariable(named = "LEONARDO_API_KEY", matches = ".+")
class LeonardoImageServiceTest {

    @Autowired
    private LeonardoImageService leonardoImageService;

    @Autowired
    private ImageGenerationService imageGenerationService;

    @Test
    void generateImage_directService_shouldReturnImageBytes() throws Exception {
        String prompt = "Children's storybook illustration, colorful cartoon style, warm friendly tones, "
                + "soft watercolor look, suitable for young children: "
                + "A brave little fox crossing a wooden bridge over a sparkling river";

        byte[] imageBytes = leonardoImageService.generateImage(prompt);

        assertNotNull(imageBytes, "Image bytes should not be null — check LEONARDO_API_KEY");
        assertTrue(imageBytes.length > 1000, "Image bytes look too small — may be an error response");

        Path output = Files.createTempFile("leonardo-direct-test-", ".png");
        Files.write(output, imageBytes);
        System.out.println("✅ Leonardo direct call — image saved: " + output.toAbsolutePath());
        System.out.println("   Size: " + imageBytes.length + " bytes");
    }

    @Test
    void generateImage_viaImageGenerationService_shouldRouteToLeonardo() throws Exception {
        String description = "A friendly wizard teaching magic to a group of animals in an enchanted forest";

        byte[] imageBytes = imageGenerationService.generateStorybookImage(description);

        assertNotNull(imageBytes, "ImageGenerationService should route to Leonardo when image.model=leonardo");
        assertTrue(imageBytes.length > 1000, "Image bytes look too small — may be an error response");

        Path output = Files.createTempFile("leonardo-service-test-", ".png");
        Files.write(output, imageBytes);
        System.out.println("✅ Leonardo via ImageGenerationService — image saved: " + output.toAbsolutePath());
        System.out.println("   Size: " + imageBytes.length + " bytes");
    }

    @Test
    void generateImage_secondCall_shouldHitCache() throws Exception {
        String description = "A mermaid playing with colorful fish near a coral reef";

        byte[] first  = imageGenerationService.generateStorybookImage(description);
        byte[] second = imageGenerationService.generateStorybookImage(description);

        assertNotNull(first,  "First call should return image bytes");
        assertNotNull(second, "Second call should return cached image bytes");
        assertArrayEquals(first, second, "Second call should return identical bytes from cache — no second API call");
        System.out.println("✅ Cache hit confirmed — both calls returned identical bytes");
    }
}
