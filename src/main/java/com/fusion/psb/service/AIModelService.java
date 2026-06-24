package com.fusion.psb.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class AIModelService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AIModelService.class);

  private final Map<String, CircuitBreaker> breakers;
  private final Map<String, ChatClient> chatClients;
  private final String requestedChatModel;
  private final Random random = new Random();

  public AIModelService(CircuitBreakerRegistry circuitBreakerRegistry,
      @Autowired(required = false) @Qualifier("geminiChatClient") ChatClient geminiChatClient,
      @Autowired(required = false) @Qualifier("openaiChatClient") ChatClient openaiChatClient,
      @Autowired(required = false) @Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
      @Value("${chat.model}") String requestedChatModel) {
    this.breakers = Map.of(
        "gemini", circuitBreakerRegistry.circuitBreaker("gemini"),
        "anthropic", circuitBreakerRegistry.circuitBreaker("anthropic"),
        "openai", circuitBreakerRegistry.circuitBreaker("openai")
    );

    Map<String, ChatClient> clients = new HashMap<>();
    if (geminiChatClient != null) {
      clients.put("gemini", geminiChatClient);
    }
    if (openaiChatClient != null) {
      clients.put("openai", openaiChatClient);
    }
    if (anthropicChatClient != null) {
      clients.put("anthropic", anthropicChatClient);
    }
    this.chatClients = Map.copyOf(clients);
    this.requestedChatModel = requestedChatModel.toLowerCase();
  }

  public String getChatResponse(String systemPrompt, String userPrompt) {
    LOGGER.info("getChatResponse...");

    List<String> availableModels = breakers.entrySet().stream()
        .filter(entry -> isModelAllowed(entry.getKey()))
        .filter(entry -> entry.getValue().getState() == CircuitBreaker.State.CLOSED)
        .filter(entry -> chatClients.containsKey(entry.getKey()))
        .map(Map.Entry::getKey)
        .toList();

    if (availableModels.isEmpty()) {
      throw new RuntimeException("All configured AI models are unavailable. Please try again later.");
    }

    String selectedModel = availableModels.get(random.nextInt(availableModels.size()));
    CircuitBreaker selectedBreaker = breakers.get(selectedModel);

    try {
      return CircuitBreaker.decorateSupplier(selectedBreaker,
              () -> callAIModel(selectedModel, systemPrompt, userPrompt))
          .get();
    } catch (Exception e) {
      LOGGER.error("Error calling AI model {}: {}", selectedModel, e.getMessage());
      throw new RuntimeException("AI generation failed: " + e.getMessage(), e);
    }
  }

  private boolean isModelAllowed(String modelName) {
    return switch (requestedChatModel) {
      case "random", "all" -> true;
      default -> requestedChatModel.equals(modelName);
    };
  }

  private String callAIModel(String modelName, String systemPrompt, String userPrompt) {
    ChatClient client = chatClients.get(modelName);
    if (client == null) {
      throw new IllegalStateException("Chat client not available for model: " + modelName);
    }
    LOGGER.info("Calling AI model: {}", modelName);

    String response = client.prompt()
        .system(systemPrompt)
        .user(userPrompt)
        .call()
        .content();

    if (response == null || response.isBlank()) {
      throw new RuntimeException("AI provider returned empty response for " + modelName);
    }
    return response;
  }
}
