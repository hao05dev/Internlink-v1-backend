package com.internlink.core.application.recruitment.impl;

import com.internlink.core.application.system.NotificationService;
import com.internlink.core.application.system.notification.*;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import com.internlink.core.presentation.recruitment.dto.request.ScheduleInterviewRequest;
import com.internlink.core.shared.enums.InterviewFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class InterviewInvitationNotifier {
    private final NotificationService notifications;
    private final EmailRenderer renderer;
    private final EmailOutboxService outbox;
    private final EmailProperties settings;

    public void send(
            JobApplication application,
            User actor,
            ScheduleInterviewRequest request,
            int roundNumber,
            String formattedTime,
            String notificationMessage) {
        String jobTitle = application.getJob().getTitle();
        boolean email = !Boolean.FALSE.equals(request.getSendEmail());
        notifications.sendInAppNotification(
                application.getStudent().getId(), "INTERVIEW_SCHEDULED",
                "Mời phỏng vấn: " + jobTitle, notificationMessage, "/student/applications");
        if (!email
                || !settings.isEnabled()
                || "DISABLED".equalsIgnoreCase(settings.getDeliveryMode())) return;

        String companyName = application.getJob().getCompany().getCompanyName();
        String destination = request.getFormat() == InterviewFormat.ONLINE
                ? request.getMeetingUrl() : request.getLocation();
        var content = renderer.render(
                EmailTemplate.INTERVIEW_INVITATION,
                Map.of(
                        "studentName", Objects.toString(application.getStudent().getFullName(), ""),
                        "companyName", companyName,
                        "jobTitle", jobTitle,
                        "scheduledAt", formattedTime,
                        "format", formatLabel(request.getFormat()),
                        "destination", Objects.toString(destination, ""),
                        "interviewer", Objects.toString(request.getInterviewer(), ""),
                        "notes", Objects.toString(request.getNotes(), ""),
                        "actionUrl", renderer.baseUrl() + "/student/applications"));
        outbox.enqueueNotice(
                application.getStudent().getEmail(), content,
                "INTERVIEW_SCHEDULED",
                "interview:" + application.getId() + ":" + roundNumber + ":" + request.getScheduledAt(),
                new EmailDispatchContext(
                        actor.getId(), "JOB_APPLICATION", application.getId(),
                        "COMPANY", application.getJob().getCompany().getId()));
    }

    private static String formatLabel(InterviewFormat format) {
        return switch (format) {
            case ONLINE -> "Trực tuyến";
            case OFFLINE -> "Trực tiếp";
            case HYBRID -> "Kết hợp trực tuyến và trực tiếp";
        };
    }
}
