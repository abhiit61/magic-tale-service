package com.fusion.psb.service;

import com.fusion.psb.dto.StoryResponse;
import com.fusion.psb.entity.Role;
import com.fusion.psb.entity.StorybookAuditLog;
import com.fusion.psb.entity.User;
import com.fusion.psb.repository.StorybookAuditLogRepository;
import com.fusion.psb.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StoryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StorybookAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public StoryService(StorybookAuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public Page<StoryResponse> getStories(User currentUser, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        Page<StorybookAuditLog> results = isAdmin
                ? auditLogRepository.findAllByOrderByRequestTimestampDesc(pageable)
                : auditLogRepository.findByUserIdOrderByRequestTimestampDesc(currentUser.getId(), pageable);

        // pre-fetch users for admin view to avoid N+1
        Map<Long, User> userCache = isAdmin
                ? results.stream()
                        .filter(l -> l.getUserId() != null)
                        .map(StorybookAuditLog::getUserId)
                        .distinct()
                        .flatMap(uid -> userRepository.findById(uid).stream())
                        .collect(Collectors.toMap(User::getId, u -> u))
                : Map.of();

        return results.map(log -> toResponse(log, isAdmin, userCache));
    }

    public StoryResponse getStory(Long id, User currentUser) {
        StorybookAuditLog log = findAndCheckAccess(id, currentUser);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        Map<Long, User> userCache = Map.of();
        if (isAdmin && log.getUserId() != null) {
            userCache = userRepository.findById(log.getUserId())
                    .map(u -> Map.of(u.getId(), u))
                    .orElse(Map.of());
        }
        return toResponse(log, isAdmin, userCache);
    }

    public byte[] downloadStory(Long id, User currentUser) {
        StorybookAuditLog log = findAndCheckAccess(id, currentUser);
        if (log.getPdfData() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not available for this story");
        }
        return log.getPdfData();
    }

    private StorybookAuditLog findAndCheckAccess(Long id, User currentUser) {
        StorybookAuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        if (currentUser.getRole() != Role.ADMIN && !currentUser.getId().equals(log.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return log;
    }

    private StoryResponse toResponse(StorybookAuditLog log, boolean isAdmin, Map<Long, User> userCache) {
        StoryResponse r = new StoryResponse();
        r.setId(log.getId());
        r.setTitle(log.getName() + "'s " + log.getTheme() + " Story");
        r.setChildName(log.getName());
        r.setAge(log.getAge());
        r.setGender(log.getGender());
        r.setBodyTone(log.getBodyTone());
        r.setLocation(log.getLocation());
        r.setEvent(log.getEvent());
        r.setTheme(log.getTheme());
        r.setMood(log.getMood());
        r.setCompanion(log.getCompanion());
        r.setMoralAttributes(log.getMoralAttributes());
        r.setLanguage(log.getLanguage());
        r.setStatus(resolveStatus(log));
        r.setErrorMessage(log.getErrorMessage());
        r.setCreatedAt(log.getRequestTimestamp() != null
                ? log.getRequestTimestamp().format(FMT) + "Z" : null);

        if (isAdmin && log.getUserId() != null) {
            User owner = userCache.get(log.getUserId());
            if (owner != null) {
                r.setUserId(owner.getId());
                r.setUserEmail(owner.getEmail());
                r.setUserName(owner.getName());
            } else {
                r.setUserId(log.getUserId());
            }
        }
        return r;
    }

    private String resolveStatus(StorybookAuditLog log) {
        if (log.getStatus() != null) return log.getStatus().name();
        // fallback for records created before status field was added
        return log.isSuccess() ? "COMPLETED" : "FAILED";
    }
}
