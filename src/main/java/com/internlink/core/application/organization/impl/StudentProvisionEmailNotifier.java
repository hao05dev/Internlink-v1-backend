package com.internlink.core.application.organization.impl;

import com.internlink.core.application.system.notification.EmailOutboxService;
import com.internlink.core.application.system.notification.EmailRenderer;
import com.internlink.core.application.system.notification.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Soạn (template verify-account-student-tmpl) và đưa thư kích hoạt tài khoản sinh viên vào email outbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentProvisionEmailNotifier {

    private final EmailOutboxService emailOutboxService;
    private final EmailRenderer renderer;

    public void sendProvisionEmail(
            String studentEmail,
            String studentName,
            String studentCode,
            String activationUrl,
            long ttlHours,
            String termName) {
        var content = renderer.render(
                EmailTemplate.VERIFY_ACCOUNT_STUDENT,
                Map.of(
                        "studentName", Objects.toString(studentName, ""),
                        "studentCode", Objects.toString(studentCode, ""),
                        "email", studentEmail,
                        "activationUrl", activationUrl,
                        "ttlHours", ttlHours,
                        "termName", Objects.toString(termName, "")));

        boolean enqueued = emailOutboxService.enqueueNotice(studentEmail, content);
        if (enqueued) {
            log.info("Đã đưa email kích hoạt cho sinh viên {} ({}) vào hàng đợi email.", studentCode, studentEmail);
        } else {
            log.warn("Không thể đưa email kích hoạt cho sinh viên {} ({}) vào hàng đợi (email có thể bị vô hiệu hóa hoặc trùng lặp).", studentCode, studentEmail);
        }
    }
}
