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
 * Integration test for image generation.
 * Requires OPENAI_API_KEY environment variable to be set.
 * Run with: OPENAI_API_KEY=sk-... mvn test -Dtest=ImageGenerationServiceTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ImageGenerationServiceTest {

    @Autowired
    private ImageGenerationService imageGenerationService;

    @Test
    void generateImage_shouldReturnImageBytes() throws Exception {
        String description = "A happy child reading a colorful book under a large oak tree on a sunny day";

        byte[] imageBytes = imageGenerationService.generateStorybookImage(description);

        assertNotNull(imageBytes, "Image bytes should not be null — check OPENAI_API_KEY and image.model");
        assertTrue(imageBytes.length > 1000, "Image bytes look too small — may be an error response");

        // Save to a temp file so you can open and visually inspect it
        Path output = Files.createTempFile("storybook-image-test-", ".png");
        Files.write(output, imageBytes);
        System.out.println("✅ Image saved for inspection: " + output.toAbsolutePath());
        System.out.println("   Size: " + imageBytes.length + " bytes");
    }

    @Test
    void generateImage_secondCall_shouldHitCache() throws Exception {
        String description = "A small dragon learning to fly over a rainbow-coloured meadow";

        byte[] first  = imageGenerationService.generateStorybookImage(description);
        byte[] second = imageGenerationService.generateStorybookImage(description);

        assertNotNull(first);
        assertNotNull(second);
        assertArrayEquals(first, second, "Second call should return the cached image");
        System.out.println("✅ Cache hit confirmed — both calls returned identical bytes");
    }
}
