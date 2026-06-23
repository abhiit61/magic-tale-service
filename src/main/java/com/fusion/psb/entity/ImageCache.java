package com.fusion.psb.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_cache")
public class ImageCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SHA-256 hash of the description — used as the fast lookup key
    @Column(name = "description_hash", unique = true, nullable = false, length = 64)
    private String descriptionHash;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;

    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public String getDescriptionHash() { return descriptionHash; }
    public void setDescriptionHash(String descriptionHash) { this.descriptionHash = descriptionHash; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
