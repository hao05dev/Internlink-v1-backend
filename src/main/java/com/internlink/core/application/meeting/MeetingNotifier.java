package com.internlink.core.application.meeting;

import com.internlink.core.application.system.NotificationService;
import com.internlink.core.application.system.notification.EmailDispatchContext;
import com.internlink.core.application.system.notification.EmailOutboxService;
import com.internlink.core.application.system.notification.EmailRenderer;
import com.internlink.core.application.system.notification.EmailTemplate;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.meeting.Meeting;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/** Gửi thông báo trong hệ thống và email cho người được mời khi họp được tạo, đổi hoặc hủy. */
@Component
@RequiredArgsConstructor
public class MeetingNotifier {
    public static final String STUDENT_PATH = "/student/meetings";
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public enum Change {
        SCHEDULED("MEETING_SCHEDULED", "Lời mời họp", "mời bạn tham gia cuộc họp trực tuyến."),
        UPDATED("MEETING_UPDATED", "Cập nhật cuộc họp", "đã cập nhật thông tin cuộc họp. Vui lòng xem lại thời gian và link."),
        CANCELLED("MEETING_CANCELLED", "Đã hủy cuộc họp", "đã hủy cuộc họp dưới đây.");

        final String type;
        final String heading;
        final String intro;

        Change(String type, String heading, String intro) {
            this.type = type;
            this.heading = heading;
            this.intro = intro;
        }
    }

    private final NotificationService notifications;
    private final EmailRenderer renderer;
    private final EmailOutboxService outbox;
    private final EmailProperties settings;

    public void notify(Meeting meeting, User organizer, User recipient, Change change) {
        String when = DISPLAY.format(meeting.getStartAt());
        String message = change == Change.CANCELLED
                ? organizer.getFullName() + " đã hủy buổi họp lúc " + when + reasonSuffix(meeting)
                : organizer.getFullName() + " hẹn họp lúc " + when + ". Bấm để xem link tham gia.";
        notifications.sendInAppNotification(
                recipient.getId(), change.type, change.heading + ": " + meeting.getTitle(), message, STUDENT_PATH);
        if (!settings.isEnabled() || "DISABLED".equalsIgnoreCase(settings.getDeliveryMode())) return;

        long minutes = Duration.between(meeting.getStartAt(), meeting.getEndAt()).toMinutes();
        var content = renderer.render(
                EmailTemplate.MEETING_INVITATION,
                Map.ofEntries(
                        Map.entry("heading", change.heading),
                        Map.entry("intro", change.intro),
                        Map.entry("cancelled", change == Change.CANCELLED),
                        Map.entry("recipientName", Objects.toString(recipient.getFullName(), "")),
                        Map.entry("organizerName", Objects.toString(organizer.getFullName(), "")),
                        Map.entry("title", meeting.getTitle()),
                        Map.entry("scheduledAt", when),
                        Map.entry("duration", minutes + " phút"),
                        Map.entry("meetUrl", meeting.getMeetUrl()),
                        Map.entry("agenda", Objects.toString(meeting.getAgenda(), "")),
                        Map.entry("cancelReason", Objects.toString(meeting.getCancelReason(), "")),
                        Map.entry("actionUrl", renderer.baseUrl() + STUDENT_PATH)));
        outbox.enqueueNotice(
                recipient.getEmail(),
                content,
                change.type,
                "meeting:" + meeting.getId() + ":" + meeting.getVersion() + ":" + change.name() + ":" + recipient.getId(),
                new EmailDispatchContext(organizer.getId(), "MEETING", meeting.getId(), null, null));
    }

    private static String reasonSuffix(Meeting meeting) {
        String reason = meeting.getCancelReason();
        return reason == null || reason.isBlank() ? "." : ". Lý do: " + reason;
    }
}
