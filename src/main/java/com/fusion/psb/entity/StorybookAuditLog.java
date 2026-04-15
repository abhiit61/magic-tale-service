package com.fusion.psb.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "storybook_audit_log")
public class StorybookAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String gender;
    private int age;
    private String bodyTone;
    private String location;
    private String event;
    private String theme;
    private String mood;
    private String companion;
    private String moralAttributes;
    private String language;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(columnDefinition = "TEXT")
    private String userPrompt;

    @Column(columnDefinition = "TEXT")
    private String aiResponse;

    private LocalDateTime requestTimestamp;
    private boolean success;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getBodyTone() { return bodyTone; }
    public void setBodyTone(String bodyTone) { this.bodyTone = bodyTone; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public String getCompanion() { return companion; }
    public void setCompanion(String companion) { this.companion = companion; }

    public String getMoralAttributes() { return moralAttributes; }
    public void setMoralAttributes(String moralAttributes) { this.moralAttributes = moralAttributes; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }

    public String getAiResponse() { return aiResponse; }
    public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }

    public LocalDateTime getRequestTimestamp() { return requestTimestamp; }
    public void setRequestTimestamp(LocalDateTime requestTimestamp) { this.requestTimestamp = requestTimestamp; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
