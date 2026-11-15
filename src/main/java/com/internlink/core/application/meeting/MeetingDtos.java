package com.internlink.core.application.meeting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request/response của module cuộc họp. */
public final class MeetingDtos {
    private MeetingDtos() {}

    public record MeetingRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String agenda,
            @NotNull OffsetDateTime startAt,
            @Min(15) @Max(240) int durationMinutes,
            @NotBlank @Size(max = 255) String meetUrl,
            @NotEmpty @Size(max = 50) List<@NotNull UUID> attendeeIds) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record AttendanceItem(@NotNull UUID userId, @NotNull @Pattern(regexp = "UNKNOWN|PRESENT|ABSENT") String attendance) {}

    public record AttendanceRequest(@NotEmpty @Size(max = 50) List<@Valid AttendanceItem> items) {}

    public record AttendeeView(UUID userId, String fullName, String email, String attendance) {}

    public record MeetingView(
            UUID id,
            String title,
            String agenda,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String meetUrl,
            String linkMode,
            String status,
            String cancelReason,
            UUID organizerId,
            String organizerName,
            int attendeeCount,
            List<AttendeeView> attendees) {}
}
