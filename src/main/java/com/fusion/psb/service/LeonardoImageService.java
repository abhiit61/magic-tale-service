package com.fusion.psb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class LeonardoImageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeonardoImageService.class);
    private static final String BASE_URL  = "https://cloud.leonardo.ai/api/rest/v1";
    private static final int    POLL_INTERVAL_MS = 2000;
    private static final int    MAX_POLLS        = 30; // 60 seconds max

    @Value("${leonardo.api-key:}")
    private String apiKey;

    @Value("${leonardo.model-id:b24e16ff-06e3-43eb-8d33-4416c2d75876}")
    private String modelId;

    /** Image width in pixels. Smaller = fewer credits. Typical values: 512, 768, 1024. */
    @Value("${leonardo.width:512}")
    private int width;

    /** Image height in pixels. Smaller = fewer credits. Typical values: 512, 768, 1024. */
    @Value("${leonardo.height:512}")
    private int height;

    /**
     * Alchemy pipeline — significantly improves quality but costs ~2-3x credits.
     * false = standard generation (~5 credits), true = alchemy (~10-15 credits).
     */
    @Value("${leonardo.alchemy:false}")
    private boolean alchemy;

    /**
     * Number of diffusion steps. Lower = faster and cheaper, higher = more detail.
     * Typical range: 4-20 for Phoenix; set 0 to let the model use its default.
     */
    @Value("${leonardo.num-inference-steps:10}")
    private int numInferenceSteps;

    /**
     * Automatically rewrites/enriches the prompt before generation — costs extra credits.
     */
    @Value("${leonardo.enhance-prompt:false}")
    private boolean enhancePrompt;

    /**
     * Upscale the output to high resolution after generation — costs significant extra credits.
     */
    @Value("${leonardo.high-resolution:false}")
    private boolean highResolution;

    private final RestTemplate restTemplate;

    public LeonardoImageService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public byte[] generateImage(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("Leonardo API key not configured — set LEONARDO_API_KEY. Skipping image generation.");
            return null;
        }
        try {
            String generationId = createGeneration(prompt);
            LOGGER.info("Leonardo generation started: {}", generationId);

            String imageUrl = pollForCompletion(generationId);
            if (imageUrl == null) return null;

            LOGGER.info("Leonardo generation complete, downloading image.");
            return restTemplate.getForObject(imageUrl, byte[].class);
        } catch (Exception e) {
            LOGGER.warn("Leonardo image generation failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String createGeneration(String prompt) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("prompt",           prompt);
        payload.put("modelId",          modelId);
        payload.put("width",            width);
        payload.put("height",           height);
        payload.put("num_images",       1);
        payload.put("alchemy",          alchemy);
        payload.put("enhancePrompt",    enhancePrompt);
        payload.put("highResolution",   highResolution);
        if (numInferenceSteps > 0) {
            payload.put("num_inference_steps", numInferenceSteps);
        }

        LOGGER.info("Leonardo generation request — size={}x{}, alchemy={}, steps={}, enhancePrompt={}, highRes={}",
                width, height, alchemy, numInferenceSteps, enhancePrompt, highResolution);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, authHeaders());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                BASE_URL + "/generations", request, Map.class);

        Map<String, Object> body  = response.getBody();
        Map<String, Object> sdJob = (Map<String, Object>) body.get("sdGenerationJob");
        return (String) sdJob.get("generationId");
    }

    @SuppressWarnings("unchecked")
    private String pollForCompletion(String generationId) throws InterruptedException {
        HttpEntity<?> request = new HttpEntity<>(authHeaders());

        for (int i = 0; i < MAX_POLLS; i++) {
            Thread.sleep(POLL_INTERVAL_MS);

            ResponseEntity<Map> response = restTemplate.exchange(
                    BASE_URL + "/generations/" + generationId,
                    HttpMethod.GET, request, Map.class);

            Map<String, Object> generation = (Map<String, Object>) response.getBody().get("generations_by_pk");
            String status = (String) generation.get("status");
            LOGGER.debug("Leonardo generation {} — status: {}", generationId, status);

            if ("COMPLETE".equals(status)) {
                List<Map<String, Object>> images =
                        (List<Map<String, Object>>) generation.get("generated_images");
                if (images != null && !images.isEmpty()) {
                    return (String) images.get(0).get("url");
                }
                LOGGER.warn("Leonardo generation {} completed but returned no images.", generationId);
                return null;
            }

            if ("FAILED".equals(status)) {
                LOGGER.warn("Leonardo generation {} failed.", generationId);
                return null;
            }
        }

        LOGGER.warn("Leonardo generation {} timed out after {}s.", generationId, (MAX_POLLS * POLL_INTERVAL_MS) / 1000);
        return null;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
