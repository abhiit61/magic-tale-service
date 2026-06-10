package com.fusion.psb.dto;

public class StoryResponse {

    private Long id;
    private String title;
    private String name;
    private int age;
    private String gender;
    private String bodyTone;
    private String location;
    private String event;
    private String theme;
    private String mood;
    private String companion;
    private String moralAttributes;
    private String language;
    private String status;
    private String errorMessage;
    private String createdAt;

    // populated only for admin
    private Long userId;
    private String createdByEmail;
    private String createdByName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCreatedByEmail() { return createdByEmail; }
    public void setCreatedByEmail(String createdByEmail) { this.createdByEmail = createdByEmail; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
}
