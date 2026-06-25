package com.fusion.psb.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@PreAuthorize("hasRole('ADMIN')")
public class AIModelController {

  private final CircuitBreaker geminiBreaker;
  private final CircuitBreaker anthropicBreaker;
  private final CircuitBreaker openaiBreaker;
  private final CircuitBreaker openaiImageBreaker;
  private final CircuitBreaker leonardoBreaker;
  private final CircuitBreaker huggingFaceImageBreaker;

  public AIModelController(CircuitBreakerRegistry circuitBreakerRegistry) {
    this.geminiBreaker = circuitBreakerRegistry.circuitBreaker("gemini");
    this.anthropicBreaker = circuitBreakerRegistry.circuitBreaker("anthropic");
    this.openaiBreaker = circuitBreakerRegistry.circuitBreaker("openai");
    this.openaiImageBreaker = circuitBreakerRegistry.circuitBreaker("openai-image");
    this.leonardoBreaker = circuitBreakerRegistry.circuitBreaker("leonardo");
    this.huggingFaceImageBreaker = circuitBreakerRegistry.circuitBreaker("huggingface-image");
  }

  @GetMapping("/chat/availability")
  public Map<String, String> getChatModelAvailability() {
    Map<String, String> modelAvailability = new HashMap<>();

    modelAvailability.put("Gemini", getAvailability(geminiBreaker));
    modelAvailability.put("Anthropic", getAvailability(anthropicBreaker));
    modelAvailability.put("OpenAI", getAvailability(openaiBreaker));

    return modelAvailability;
  }

  @GetMapping("/image/availability")
  public Map<String, String> getImageModelAvailability() {
    Map<String, String> modelAvailability = new HashMap<>();

    modelAvailability.put("OpenAI Image", getAvailability(openaiImageBreaker));
    modelAvailability.put("LEONARDO.AI", getAvailability(leonardoBreaker));
    modelAvailability.put("Hugging Face", getAvailability(huggingFaceImageBreaker));

    return modelAvailability;
  }

  private String getAvailability(CircuitBreaker circuitBreaker) {
    CircuitBreaker.State state = circuitBreaker.getState();
    return state == CircuitBreaker.State.CLOSED ? "Available" : "Unavailable";
  }
}
