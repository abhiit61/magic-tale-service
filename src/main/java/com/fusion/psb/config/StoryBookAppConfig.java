package com.fusion.psb.config;

import com.fusion.psb.imagemodel.HuggingFaceImageModel;
import com.fusion.psb.service.LeonardoImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fusion.psb.imagemodel.LeonardoImageModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

  @Bean(name = "imageGenerationTaskExecutor")
  public TaskExecutor imageGenerationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(20);
    executor.setThreadNamePrefix("image-gen-");
    executor.initialize();
    return executor;
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

  @Bean("openai-image")
  @ConditionalOnBean(OpenAiImageModel.class)
  public ImageModel openAiImageModel(OpenAiImageModel openAiImageModel) {
    LOGGER.info("Registering ImageModel: OpenAI (gpt-image-1)");
    return openAiImageModel;
  }

  @Bean("leonardo")
  public ImageModel leonardoImageModel(LeonardoImageService leonardoImageService) {
    LOGGER.info("Registering ImageModel: Leonardo AI");
    return new LeonardoImageModel(leonardoImageService);
  }

  @Bean(name = "huggingface-image")
  public ImageModel huggingFaceImageModel(
      @Value("${huggingface.api-key}") String apiKey,
      @Value("${huggingface.model}") String model) {
    return new HuggingFaceImageModel(apiKey, model);
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
