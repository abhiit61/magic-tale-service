package com.fusion.psb.service;

import com.fusion.psb.dto.StorybookRequest;
import com.fusion.psb.entity.StorybookAuditLog;
import com.fusion.psb.entity.StoryStatus;
import com.fusion.psb.entity.User;
import com.fusion.psb.repository.StorybookAuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class StorybookService {

    private final StorybookAuditLogRepository auditLogRepository;
    private final AsyncStorybookService asyncStorybookService;

    public StorybookService(StorybookAuditLogRepository auditLogRepository,
                            AsyncStorybookService asyncStorybookService) {
        this.auditLogRepository = auditLogRepository;
        this.asyncStorybookService = asyncStorybookService;
    }

    public StorybookAuditLog generateStorybook(StorybookRequest request, User user) {
        String language = (request.getLanguage() != null && !request.getLanguage().isBlank())
                ? request.getLanguage() : "English";

        StorybookAuditLog log = buildAuditLog(request, user, language);
        log.setStatus(StoryStatus.GENERATING);
        auditLogRepository.save(log);

        asyncStorybookService.generateAsync(log.getId(), request, language);

        return log;
    }

    private StorybookAuditLog buildAuditLog(StorybookRequest request, User user, String language) {
        StorybookAuditLog log = new StorybookAuditLog();
        log.setUserId(user != null ? user.getId() : null);
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
        log.setLanguage(language);
        log.setRequestTimestamp(LocalDateTime.now());
        return log;
    }
}
