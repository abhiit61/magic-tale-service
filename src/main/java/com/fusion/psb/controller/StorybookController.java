package com.fusion.psb.controller;

import com.fusion.psb.dto.StorybookRequest;
import com.fusion.psb.dto.StoryResponse;
import com.fusion.psb.entity.StorybookAuditLog;
import com.fusion.psb.entity.User;
import com.fusion.psb.repository.StorybookAuditLogRepository;
import com.fusion.psb.service.StorybookService;
import com.fusion.psb.service.StoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/storybook")
public class StorybookController {

  @Autowired
  private StorybookService storybookService;

  @Autowired
  private StoryService storyService;

  @Autowired
  private StorybookAuditLogRepository auditLogRepository;

  @Value("${admin.audit.password}")
  private String adminPassword;

  @Value("${admin.audit.allowed-ip}")
  private String allowedIp;

  @PostMapping("/generate")
  public ResponseEntity<Map<String, Object>> generateStorybook(
      @RequestBody StorybookRequest request,
      @AuthenticationPrincipal User user) {
    StorybookAuditLog log = storybookService.generateStorybook(request, user);
    return ResponseEntity.accepted().body(Map.of(
        "id", log.getId(),
        "status", log.getStatus().name()
    ));
  }

  @GetMapping("/stories")
  public ResponseEntity<Page<StoryResponse>> getStories(
      @AuthenticationPrincipal User user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok(storyService.getStories(user, page, size));
  }

  @GetMapping("/stories/{id}")
  public ResponseEntity<StoryResponse> getStory(
      @PathVariable Long id,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(storyService.getStory(id, user));
  }

  @GetMapping("/stories/{id}/download")
  public ResponseEntity<byte[]> downloadStory(
      @PathVariable Long id,
      @AuthenticationPrincipal User user) {
    byte[] pdf = storyService.downloadStory(id, user);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/pdf");
    headers.add("Content-Disposition", "attachment; filename=\"story-" + id + ".pdf\"");
    return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
  }

  @GetMapping("/admin/audit-logs")
  public ResponseEntity<?> getAuditLogs(
      @RequestHeader("X-Admin-Password") String password,
      HttpServletRequest httpRequest) {

    String remoteAddr = httpRequest.getRemoteAddr();
    if (!"all".equalsIgnoreCase(allowedIp) && !allowedIp.equals(remoteAddr)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: IP " + remoteAddr + " not allowed");
    }

    if (!adminPassword.equals(password)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
    }

    List<StorybookAuditLog> logs = auditLogRepository.findAll();
    return ResponseEntity.ok(logs);
  }
}
