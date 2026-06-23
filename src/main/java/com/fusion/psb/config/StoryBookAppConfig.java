package com.fusion.psb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StoryBookAppConfig implements WebMvcConfigurer {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoryBookAppConfig.class);

  @Value("${chat.model:all}")
  private String chatModel;

  @Value("${image.model:all}")
  private String imageModelConfig;

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public ChatClient geminiChatClient(GoogleGenAiChatModel googleGenAiChatModel) {
    LOGGER.info("Registering ChatClient: Gemini");
    return ChatClient.create(googleGenAiChatModel);
  }

  @Bean
  public ChatClient openaiChatClient(OpenAiChatModel openAiChatModel) {
    LOGGER.info("Registering ChatClient: OpenAI");
    return ChatClient.create(openAiChatModel);
  }

  @Bean
  public ChatClient anthropicChatClient(AnthropicChatModel anthropicChatModel) {
    LOGGER.info("Registering ChatClient: Anthropic");
    return ChatClient.create(anthropicChatModel);
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
        LOGGER.info("Using ImageModel: OpenAI (gpt-image-1)");
        yield openAiImageModel;
      }
      case "leonardo" -> {
        LOGGER.info("Using ImageModel: Leonardo AI (handled via LeonardoImageService)");
        yield null; // Leonardo uses its own REST client, not Spring AI ImageModel
      }
      case "random", "all" -> {
        if (openAiImageModel != null) {
          LOGGER.info("Using ImageModel: random selection defaults to OpenAI primary image model.");
          yield openAiImageModel;
        }
        LOGGER.info("Using ImageModel: Leonardo AI (handled via LeonardoImageService) — no OpenAI image bean available");
        yield null;
      }
      case "none" -> {
        LOGGER.info("Image generation disabled (image.model=none).");
        yield null;
      }
      default -> throw new IllegalArgumentException(
          "Unsupported image.model value: '" + imageModelConfig + "'. Supported values: openai, leonardo, none, random");
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
