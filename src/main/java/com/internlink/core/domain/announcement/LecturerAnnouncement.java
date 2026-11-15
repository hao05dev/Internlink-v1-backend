package com.internlink.core.domain.announcement;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Thông báo GVHD gửi cho một nhóm sinh viên mình hướng dẫn. */
@Entity
@Table(name = "lecturer_announcements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LecturerAnnouncement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "send_email", nullable = false)
    private boolean sendEmail;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
