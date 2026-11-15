package com.internlink.core.domain.announcement;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/** Sinh viên nhận một thông báo của GVHD. */
@Entity
@Table(name = "lecturer_announcement_recipients")
@IdClass(LecturerAnnouncementRecipient.Key.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LecturerAnnouncementRecipient {
    @Id @Column(name = "announcement_id") private UUID announcementId;
    @Id @Column(name = "user_id") private UUID userId;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID announcementId;
        private UUID userId;
    }
}
