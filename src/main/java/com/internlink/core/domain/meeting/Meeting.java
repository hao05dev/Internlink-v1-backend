package com.internlink.core.domain.meeting;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Một buổi họp trực tuyến; link Meet do người tổ chức dán (MANUAL) hoặc tạo qua Google. */
@Entity
@Table(name = "meetings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Meeting {
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String CONTEXT_SUPERVISION = "SUPERVISION";
    public static final String LINK_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Column(name = "context_type", nullable = false, length = 20)
    private String contextType;

    @Column(name = "context_id")
    private UUID contextId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String agenda;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "meet_url", nullable = false, length = 255)
    private String meetUrl;

    @Column(name = "link_mode", nullable = false, length = 20)
    private String linkMode;

    @Column(name = "external_event_id", length = 255)
    private String externalEventId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}
