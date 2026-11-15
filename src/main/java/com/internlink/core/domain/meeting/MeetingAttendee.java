package com.internlink.core.domain.meeting;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Người được mời vào một buổi họp, kèm kết quả điểm danh. */
@Entity
@Table(name = "meeting_attendees")
@IdClass(MeetingAttendee.Key.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MeetingAttendee {
    public static final String UNKNOWN = "UNKNOWN";

    @Id @Column(name = "meeting_id") private UUID meetingId;
    @Id @Column(name = "user_id") private UUID userId;
    @Column(nullable = false, length = 10) private String attendance;
    @Column(name = "notified_at") private OffsetDateTime notifiedAt;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID meetingId;
        private UUID userId;
    }
}
