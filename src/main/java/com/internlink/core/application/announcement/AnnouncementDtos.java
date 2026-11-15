package com.internlink.core.application.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AnnouncementDtos {
    private AnnouncementDtos() {}

    public record AnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String body,
            @NotEmpty @Size(max = 200) List<UUID> recipientIds,
            boolean sendEmail) {}

    public record Recipient(UUID userId, String fullName) {}

    /** recipients chỉ trả cho người gửi; sinh viên nhận danh sách rỗng. */
    public record AnnouncementView(
            UUID id,
            String title,
            String body,
            String senderName,
            boolean sendEmail,
            int recipientCount,
            List<Recipient> recipients,
            OffsetDateTime createdAt) {}
}
