package com.fusion.psb.service;

import com.fusion.psb.dto.StorybookRequest;
import com.fusion.psb.entity.StorybookAuditLog;
import com.fusion.psb.entity.StoryStatus;
import com.fusion.psb.repository.StorybookAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AsyncStorybookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncStorybookService.class);

    private final StorybookAuditLogRepository auditLogRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final AIModelService aiModelService;

    public AsyncStorybookService(StorybookAuditLogRepository auditLogRepository,
                                 PdfGeneratorService pdfGeneratorService,
                                 AIModelService aiModelService) {
        this.auditLogRepository = auditLogRepository;
        this.pdfGeneratorService = pdfGeneratorService;
        this.aiModelService = aiModelService;
    }

    @Async
    public void generateAsync(Long logId, StorybookRequest request, String language) {
        StorybookAuditLog log = auditLogRepository.findById(logId).orElseThrow();

        try {
            // Check cache (excludes the current GENERATING record since success=false)
            Optional<StorybookAuditLog> cached = auditLogRepository.findCachedStory(
                    request.getAge(), request.getGender(), request.getBodyTone(),
                    request.getLocation(), request.getEvent(), request.getTheme(),
                    request.getMood(), request.getCompanion(), request.getMoralAttributes(), language
            );

            String storyContent;
            if (cached.isPresent()) {
                LOGGER.info("Cache hit for log {} — reusing story, replacing name '{}' with '{}'",
                        logId, cached.get().getName(), request.getName());
                storyContent = cached.get().getAiResponse().replace(cached.get().getName(), request.getName());
            } else {
                LOGGER.info("Cache miss for log {} — calling AI", logId);
                String systemPrompt = buildSystemPrompt(language);
                String userPrompt = buildUserPrompt(request, language);
                log.setSystemPrompt(systemPrompt);
                log.setUserPrompt(userPrompt);
                storyContent = callChatApi(userPrompt, systemPrompt);
            }

            byte[] pdf = pdfGeneratorService.createPDF(request.getName(), storyContent, language);
            log.setAiResponse(storyContent);
            log.setPdfData(pdf);
            log.setSuccess(true);
            log.setStatus(StoryStatus.COMPLETED);

        } catch (Exception e) {
            LOGGER.error("Async story generation failed for log {}: {}", logId, e.getMessage());
            log.setSuccess(false);
            log.setStatus(StoryStatus.FAILED);
            log.setErrorMessage(e.getMessage());
        }

        auditLogRepository.save(log);
    }

    private String buildSystemPrompt(String language) {
        return "You are a story creator who creates personalized storybooks for children. "
                + "Begin your response with the story title on its own line using EXACTLY this format: # <Story Title> "
                + "(for example: # The Brave Little Fox and the Magic River). "
                + "After the title, generate a story across 3 to 4 pages. Keep each page short and use simple vocabulary suitable for the child's age. "
                + "One page will be divided between text and image, 50% text and 50% image. "
                + "For EACH story page you MUST include an image description on its own line using EXACTLY this format: [IMAGE: <detailed visual scene description>]. "
                + "Image descriptions MUST always be written in English (even if the story language is different), because they are used to generate illustrations. "
                + "Separate story pages with '---'. "
                + "Respond ONLY with the story content. Do not include any introductory or concluding remarks. "
                + "Generate the story text in " + language + " language.";
    }

    private String buildUserPrompt(StorybookRequest request, String language) {
        return String.format(
                "Create a personalized storybook for a %d-year-old %s named %s with body tone %s. " +
                        "The story is set in %s during %s. The theme is %s, mood is %s, and the companion is %s. " +
                        "Include moral attributes: %s. Write the story in %s language.",
                request.getAge(), request.getGender(), request.getName(), request.getBodyTone(),
                request.getLocation(), request.getEvent(), request.getTheme(), request.getMood(),
                request.getCompanion(), request.getMoralAttributes(), language
        );
    }

    private String callChatApi(String userPrompt, String systemPrompt) {
        try {
            return aiModelService.getChatResponse(systemPrompt, userPrompt);
        } catch (Exception e) {
            LOGGER.error("Chat API error: {}", e.getMessage());
            throw new RuntimeException("AI generation failed: " + e.getMessage());
        }
    }
}
