package com.fusion.psb.service;

import com.fusion.psb.config.StorybookConstants;
import com.fusion.psb.dto.StorybookRequest;
import com.fusion.psb.entity.StorybookAuditLog;
import com.fusion.psb.repository.StorybookAuditLogRepository;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class StorybookService {

  private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StorybookService.class);

  private final RestTemplate restTemplate;
  private final ChatClient chatClient;
  private final StorybookAuditLogRepository auditLogRepository;
  private final PdfGeneratorService pdfGeneratorService;

  @Autowired
  public StorybookService(RestTemplate restTemplate, ChatClient chatClient,
      StorybookAuditLogRepository auditLogRepository, PdfGeneratorService pdfGeneratorService) {
    this.restTemplate = restTemplate;
    this.chatClient = chatClient;
    this.auditLogRepository = auditLogRepository;
    this.pdfGeneratorService = pdfGeneratorService;
  }

  public byte[] generateStorybook(StorybookRequest request) throws Exception {
    String language = (request.getLanguage() != null && !request.getLanguage().isBlank())
        ? request.getLanguage() : "English";

    // Check if an identical request was successfully served before
    Optional<StorybookAuditLog> cached = auditLogRepository.findCachedStory(
        request.getAge(), request.getGender(), request.getBodyTone(),
        request.getLocation(), request.getEvent(), request.getTheme(),
        request.getMood(), request.getCompanion(), request.getMoralAttributes(), language
    );

    if (cached.isPresent()) {
      LOGGER.info("Cache hit — reusing past story, replacing name '{}' with '{}'",
          cached.get().getName(), request.getName());
      String cachedContent = cached.get().getAiResponse()
          .replace(cached.get().getName(), request.getName());
      return pdfGeneratorService.createPDF(request.getName(), cachedContent, language);
    } else {
      LOGGER.info("Cache missed");
    }

    // No cache hit — call AI
    String sysPromp = "You are a story creator who creates personalized storybooks for children. "
        + "Generate a story across 3 to 4 pages. Keep each page short and use simple vocabulary suitable for the child's age. "
        + "One page will be divided between text and image, 50% text and 50% image"
        + "For EACH story page you MUST include an image description on its own line using EXACTLY this format: [IMAGE: <detailed visual scene description>]. "
        + "Image descriptions MUST always be written in English (even if the story language is different), because they are used to generate illustrations. "
        + "Separate story pages with '---'. "
        + "Respond ONLY with the story content. Do not include any introductory or concluding remarks. "
        + "Generate the story text in " + language + " language.";

    String userPrompt = String.format(
        "Create a personalized storybook for a %d-year-old %s named %s with body tone %s. " +
            "The story is set in %s during %s. The theme is %s, mood is %s, and the companion is %s. " +
            "Include moral attributes: %s. Write the story in %s language.",
        request.getAge(), request.getGender(), request.getName(), request.getBodyTone(),
        request.getLocation(), request.getEvent(), request.getTheme(), request.getMood(),
        request.getCompanion(), request.getMoralAttributes(), language
    );

    StorybookAuditLog auditLog = buildAuditLog(request);
    auditLog.setSystemPrompt(sysPromp);
    auditLog.setUserPrompt(userPrompt);

    String storyContent;
    try {
      storyContent = callChatApi(userPrompt, sysPromp);
      auditLog.setAiResponse(storyContent);
      auditLog.setSuccess(true);
    } catch (Exception e) {
      auditLog.setSuccess(false);
      auditLog.setErrorMessage(e.getMessage());
      auditLogRepository.save(auditLog);
      throw e;
    }

    auditLogRepository.save(auditLog);

    return pdfGeneratorService.createPDF(request.getName(), storyContent, language);
  }

  private StorybookAuditLog buildAuditLog(StorybookRequest request) {
    StorybookAuditLog log = new StorybookAuditLog();
    log.setName(request.getName());
    log.setGender(request.getGender());
    log.setAge(request.getAge());
    log.setBodyTone(request.getBodyTone());
    log.setLocation(request.getLocation());
    log.setEvent(request.getEvent());
    log.setTheme(request.getTheme());
    log.setMood(request.getMood());
    log.setCompanion(request.getCompanion());
    log.setMoralAttributes(request.getMoralAttributes());
    log.setLanguage(request.getLanguage());
    log.setRequestTimestamp(LocalDateTime.now());
    return log;
  }

  private String callChatApi(String userPrompt, String systemPromp) {
    try {
      return chatClient.prompt()
          .system(systemPromp)
          .user(userPrompt)
          .call()
          .content();
    } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
      // Handle 4xx errors (e.g., invalid credentials, bad request)
      LOGGER.error("Error : ", e);
      throw new RuntimeException("Error: "+ e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Error : ", e);
      throw new RuntimeException("Error: "+ e.getMessage());
    }
  }

}
