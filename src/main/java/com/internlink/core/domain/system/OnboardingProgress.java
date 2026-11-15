package com.internlink.core.domain.system;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Một tour hướng dẫn mà người dùng đã xem hết hoặc đã bỏ qua. */
@Entity
@Table(name = "user_onboarding_progress")
@IdClass(OnboardingProgress.Key.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OnboardingProgress {
    @Id @Column(name = "user_id") private UUID userId;
    @Id @Column(name = "tour_key", length = 60) private String tourKey;
    @Column(name = "tour_version", nullable = false) private int tourVersion;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "last_step") private Integer lastStep;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID userId;
        private String tourKey;
    }
}
