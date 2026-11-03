package com.internlink.core.application.system.notification;

import java.util.Set;

/**
 * Các email template gán cứng trong {@code resources/templates/email}. Mỗi template gồm
 * {@code <file>.html} (subject nằm ở thẻ title) và {@code <file>.txt} cho bản text.
 */
public enum EmailTemplate {
    FORGOT_PASSWORD("forgot-password-user-tmpl", Set.of("fullName", "resetUrl", "ttlMinutes")),
    VERIFY_ACCOUNT_STUDENT(
            "verify-account-student-tmpl",
            Set.of("studentName", "studentCode", "email", "activationUrl", "ttlHours", "termName")),
    VERIFY_ACCOUNT_LECTURER(
            "verify-account-lecturer-tmpl",
            Set.of("fullName", "email", "activationUrl", "ttlHours", "termName")),
    INTRODUCTION_LETTER_PICKUP(
            "introduction-letter-pickup-tmpl",
            Set.of("studentName", "studentCode", "termName", "pickupLocation", "pickupDate")),
    INTERVIEW_INVITATION(
            "interview-invitation-tmpl",
            Set.of(
                    "studentName",
                    "companyName",
                    "jobTitle",
                    "scheduledAt",
                    "format",
                    "destination",
                    "interviewer",
                    "notes",
                    "actionUrl")),
    NOTIFICATION("notification-tmpl", Set.of("title", "message", "actionUrl", "preferencesUrl")),
    CAMPAIGN("campaign-tmpl", Set.of("subject", "body"));

    private final String file;
    private final Set<String> variables;

    EmailTemplate(String file, Set<String> variables) {
        this.file = file;
        this.variables = variables;
    }

    public String file() {
        return file;
    }

    public Set<String> variables() {
        return variables;
    }
}
