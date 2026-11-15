package com.internlink.core.application.system.notification;

import com.internlink.core.application.system.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationReminderService {
    private final ReminderSource source;
    private final NotificationService notifications;
    private final PlatformTransactionManager transactions;
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public int run(OffsetDateTime now) {
        int count = 0;
        for (var candidate : source.pending(now)) {
            try {
                boolean sent =
                        Boolean.TRUE.equals(
                                new TransactionTemplate(transactions)
                                        .execute(
                                                tx -> {
                                                    if (!source.stillPending(candidate, now)
                                                            || !source.reserve(candidate.key()))
                                                        return false;
                                                    String message = message(candidate, now);
                                                notifications.publishNotification(
                                                        candidate.recipientId(),
                                                        candidate.type(),
                                                        candidate.title(),
                                                        message,
                                                        candidate.path(),
                                                        true);
                                                    return true;
                                                }));
                if (sent) count++;
            } catch (RuntimeException failure) {
                // This recipient's transaction rolls back, including its event key; retry on the
                // next run.
                log.warn(
                        "Reminder processing failed: type={}, cause={}",
                        candidate.type(),
                        failure.getClass().getSimpleName());
            }
        }
        return count;
    }

    static String message(ReminderCandidate candidate, OffsetDateTime now) {
        if (candidate.type().startsWith("SYSTEM_")) {
            return "Có lỗi vận hành cần kiểm tra trong trang quản trị.";
        }
        String when = DISPLAY.format(candidate.dueAt());
        if ("MEETING_REMINDER".equals(candidate.type())) {
            return "Cuộc họp bắt đầu lúc " + when + ". Mở trang Cuộc họp để lấy link Google Meet.";
        }
        return "Thời điểm cần xử lý: " + when
                + (candidate.dueAt().isBefore(now)
                        ? ". Công việc vẫn chưa hoàn thành; hãy kiểm tra hồ sơ."
                        : ". Hãy kiểm tra công việc trước thời điểm này.");
    }
}
