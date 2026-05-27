package com.fusion.psb.repository;

import com.fusion.psb.entity.StorybookAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorybookAuditLogRepository extends JpaRepository<StorybookAuditLog, Long> {

    Page<StorybookAuditLog> findAllByOrderByRequestTimestampDesc(Pageable pageable);

    Page<StorybookAuditLog> findByUserIdOrderByRequestTimestampDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT l FROM StorybookAuditLog l
        WHERE l.age = :age
          AND l.gender = :gender
          AND l.bodyTone = :bodyTone
          AND l.location = :location
          AND l.event = :event
          AND l.theme = :theme
          AND l.mood = :mood
          AND l.companion = :companion
          AND l.moralAttributes = :moralAttributes
          AND l.language = :language
          AND l.success = true
        ORDER BY l.requestTimestamp DESC
        LIMIT 1
        """)
    Optional<StorybookAuditLog> findCachedStory(
        @Param("age") int age,
        @Param("gender") String gender,
        @Param("bodyTone") String bodyTone,
        @Param("location") String location,
        @Param("event") String event,
        @Param("theme") String theme,
        @Param("mood") String mood,
        @Param("companion") String companion,
        @Param("moralAttributes") String moralAttributes,
        @Param("language") String language
    );
}
