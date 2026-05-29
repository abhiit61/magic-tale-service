package com.fusion.psb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StoryBookAppConfig implements WebMvcConfigurer {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoryBookAppConfig.class);

  @Value("${chat.model}")
  private String chatModel;

  @Value("${image.model}")
  private String imageModelConfig;

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public ChatClient chatClient(
      @Autowired(required = false) GoogleGenAiChatModel googleGenAiChatModel,
      @Autowired(required = false) OpenAiChatModel openAiChatModel,
      @Autowired(required = false) AnthropicChatModel anthropicChatModel) {

    ChatModel selectedModel = switch (chatModel.toLowerCase()) {
      case "openai" -> {
        if (openAiChatModel == null) throw new IllegalStateException("OpenAI model is not configured. Set spring.ai.openai.api-key.");
        LOGGER.info("Using ChatModel: OpenAI");
        yield openAiChatModel;
      }
      case "anthropic" -> {
        if (anthropicChatModel == null) throw new IllegalStateException("Anthropic model is not configured. Set spring.ai.anthropic.api-key.");
        LOGGER.info("Using ChatModel: Anthropic");
        yield anthropicChatModel;
      }
      case "gemini" -> {
        if (googleGenAiChatModel == null) throw new IllegalStateException("Gemini model is not configured. Set spring.ai.google.genai.api-key.");
        LOGGER.info("Using ChatModel: Gemini");
        yield googleGenAiChatModel;
      }
      default -> throw new IllegalArgumentException(
          "Unsupported chat.model value: '" + chatModel + "'. Supported values: gemini, openai, anthropic");
    };

    return ChatClient.create(selectedModel);
  }

  @Bean
  @Primary
  public ImageModel imageModel(@Autowired(required = false) OpenAiImageModel openAiImageModel) {
    return switch (imageModelConfig.toLowerCase()) {
      case "openai" -> {
        if (openAiImageModel == null) {
          LOGGER.warn("image.model=openai but OpenAiImageModel is not available — check OPENAI_API_KEY. Images will be skipped.");
          yield null;
        }
        LOGGER.info("Using ImageModel: OpenAI (DALL-E 3)");
        yield openAiImageModel;
      }
      case "none" -> {
        LOGGER.info("Image generation disabled (image.model=none).");
        yield null;
      }
      default -> throw new IllegalArgumentException(
          "Unsupported image.model value: '" + imageModelConfig + "'. Supported values: openai, none");
    };
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedOriginPatterns("http://localhost:4200")
        .allowedOrigins("https://magictale.netlify.app","http://localhost:8080")
        .allowedHeaders("*")
        .allowCredentials(true);
  }
}
