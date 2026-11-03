package com.internlink.core.application.organization.impl;

import com.internlink.core.application.system.notification.EmailOutboxService;
import com.internlink.core.application.system.notification.EmailRenderer;
import com.internlink.core.application.system.notification.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/** Soạn (template verify-account-lecturer-tmpl) và đưa thư cấp tài khoản giảng viên vào outbox. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LecturerProvisionEmailNotifier {

    private final EmailOutboxService emailOutboxService;
    private final EmailRenderer renderer;

    public void sendProvisionEmail(
            String email,
            String fullName,
            String activationUrl,
            long ttlHours,
            String termName) {
        var content = renderer.render(
                EmailTemplate.VERIFY_ACCOUNT_LECTURER,
                Map.of(
                        "fullName", Objects.toString(fullName, ""),
                        "email", email,
                        "activationUrl", activationUrl,
                        "ttlHours", ttlHours,
                        "termName", termName != null ? termName : "Học kỳ hiện tại"));
        emailOutboxService.enqueueNotice(email, content);
    }
}
