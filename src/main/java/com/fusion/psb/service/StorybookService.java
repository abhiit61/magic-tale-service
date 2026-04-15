package com.fusion.psb.service;

import com.fusion.psb.config.StorybookConstants;
import com.fusion.psb.dto.StorybookRequest;
import com.fusion.psb.entity.StorybookAuditLog;
import com.fusion.psb.repository.StorybookAuditLogRepository;
import com.itextpdf.text.Document;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class StorybookService {

  private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StorybookService.class);

  private final RestTemplate restTemplate;
  private final ChatClient chatClient;
  private final StorybookAuditLogRepository auditLogRepository;

  @Autowired
  public StorybookService(RestTemplate restTemplate, ChatClient chatClient,
      StorybookAuditLogRepository auditLogRepository) {
    this.restTemplate = restTemplate;
    this.chatClient = chatClient;
    this.auditLogRepository = auditLogRepository;
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
      return createPDF(request.getName(), cachedContent, null, language);
    }

    // No cache hit — call AI
    String sysPromp = "You are a story creator who creates story as per the inputs given by user. Generate story for two pages. Short ones. "
        + "Use low level vocabulary so person in india can understand. Add images in form of text, so in next iteration we can use it to generate image."
        + "Generate content based on the age. "
        + "Generate the entire story in " + language + " language.";

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

    return createPDF(request.getName(), storyContent, null, language);
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

  private byte[] createPDF(String name, String content, String imageUrl, String language) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Document document = new Document();
    try {
      PdfWriter.getInstance(document, outputStream);
      document.open();

      Font titleFont;
      Font bodyFont;
      try {
        String fontPath = resolveFontPath(language);
        BaseFont baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        titleFont = new Font(baseFont, 16, Font.BOLD);
        bodyFont = new Font(baseFont, 12);
      } catch (Exception e) {
        LOGGER.warn("Unicode font not found for language '{}', falling back to default font. Place font file in src/main/resources/fonts/", language);
        titleFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
        bodyFont = new Font(Font.FontFamily.HELVETICA, 12);
      }

      document.add(new Paragraph("Personalized Storybook for " + name, titleFont));
      document.add(new Paragraph("\n"));
      document.add(new Paragraph(content, bodyFont));
      // Uncomment the following line if the image URL is accessible and valid
      // document.add(Image.getInstance(imageUrl));
    } catch (Exception e) {
      LOGGER.error("Error while creating PDF: ", e);
      throw new RuntimeException("Failed to generate the PDF. Please try again later.");
    } finally {
      if (document.isOpen()) {
        document.close();
      }
    }
    return outputStream.toByteArray();
  }

  private String resolveFontPath(String language) {
    return switch (language.toLowerCase()) {
      case "hindi", "marathi", "nepali" -> "/fonts/NotoSansDevanagari-Regular.ttf";
      case "tamil"                       -> "/fonts/NotoSansTamil-Regular.ttf";
      case "telugu"                      -> "/fonts/NotoSansTelugu-Regular.ttf";
      case "kannada"                     -> "/fonts/NotoSansKannada-Regular.ttf";
      case "malayalam"                   -> "/fonts/NotoSansMalayalam-Regular.ttf";
      case "bengali"                     -> "/fonts/NotoSansBengali-Regular.ttf";
      case "gujarati"                    -> "/fonts/NotoSansGujarati-Regular.ttf";
      case "punjabi"                     -> "/fonts/NotoSansGurmukhi-Regular.ttf";
      case "arabic", "urdu"              -> "/fonts/NotoSansArabic-Regular.ttf";
      case "chinese"                     -> "/fonts/NotoSansSC-Regular.ttf";
      case "japanese"                    -> "/fonts/NotoSansJP-Regular.ttf";
      default                            -> "/fonts/NotoSans-Regular.ttf";
    };
  }

}
