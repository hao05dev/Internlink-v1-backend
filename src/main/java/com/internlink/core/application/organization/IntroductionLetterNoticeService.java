package com.internlink.core.application.organization;

import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.application.system.notification.EmailOutboxService;
import com.internlink.core.application.system.notification.EmailRenderer;
import com.internlink.core.application.system.notification.EmailTemplate;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntroductionLetterNoticeService {
    private final JpaInternshipTermRepository terms;
    private final JpaStudentRosterRepository rosters;
    private final JpaUserRepository users;
    private final SecurityGuard securityGuard;
    private final EmailOutboxService gmail;
    private final EmailRenderer renderer;
    private final NotificationService notifications;
    private final AuditLogService audit;
    private final PlatformTransactionManager transactionManager;

    public record NoticeResult(
            int recipients,
            int emailsSent,
            int emailsQueued,
            int notificationsCreated,
            List<String> failedStudentCodes) {}

    private record Recipient(String studentCode, String fullName, String email, UUID userId) {}

    private record NoticeBatch(String termName, List<Recipient> recipients) {}

    public NoticeResult send(UUID termId, String pickupLocation, LocalDate pickupDate, List<UUID> rosterIds) {
        if (pickupLocation == null
                || pickupLocation.isBlank()
                || pickupLocation.length() > 300
                || pickupDate == null)
            throw new BadRequestException("Cần ngày và địa điểm nhận giấy giới thiệu");
        UUID actorId = securityGuard.currentUser().getId();
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        NoticeBatch batch =
                read.execute(
                        status -> {
                            var term =
                                    terms.findById(termId)
                                            .orElseThrow(
                                                    () ->
                                                            new ResourceNotFoundException(
                                                                    "InternshipTerm",
                                                                    "id",
                                                                    termId));
                            TermGuard.requireNotClosed(term);
                            var actor = users.findById(actorId).orElseThrow();
                            ResourceAuthorization.require(
                                    ResourceAuthorization.managesDepartment(
                                            actor, term.getDepartment().getId()));
                            List<Recipient> recipients =
                                    rosters.findByTermId(termId).stream()
                                            .filter(RosterSelection.of(rosterIds))
                                            .map(
                                                    roster ->
                                                            new Recipient(
                                                                    roster.getStudentCode(),
                                                                    roster.getFullName() != null ? roster.getFullName() : "",
                                                                    roster.getOfficialEmail(),
                                                                    roster.getClaimedUser() != null
                                                                            ? roster.getClaimedUser()
                                                                                    .getId()
                                                                            : users.findByEmail(
                                                                                            roster
                                                                                                    .getOfficialEmail())
                                                                                    .map(
                                                                                            user ->
                                                                                                    user
                                                                                                            .getId())
                                                                                    .orElse(null)))
                                            .toList();
                            if (recipients.isEmpty())
                                throw new BadRequestException(
                                        RosterSelection.isPartial(rosterIds)
                                                ? "Không có sinh viên nào trong các sinh viên đã chọn"
                                                : "Kỳ này chưa có sinh viên để thông báo");
                            return new NoticeBatch(term.getTermName(), recipients);
                        });
        if (batch == null) throw new IllegalStateException("Không tải được danh sách sinh viên");
        
        String dateStr = pickupDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String location = pickupLocation.trim();
        String subject = "Thông báo nhận giấy giới thiệu thực tập - " + batch.termName();
        String body = "Bạn vui lòng đến " + location + " từ ngày " + dateStr
                + " để nhận bản cứng giấy giới thiệu thực tập cho kỳ " + batch.termName() + ".";

        int emails = 0;
        int inApp = 0;
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        for (Recipient recipient : batch.recipients()) {
            try {
                var content = renderer.render(
                        EmailTemplate.INTRODUCTION_LETTER_PICKUP,
                        Map.of(
                                "studentName", recipient.fullName(),
                                "studentCode", recipient.studentCode(),
                                "termName", batch.termName(),
                                "pickupLocation", location,
                                "pickupDate", dateStr));
                if (gmail.enqueueNotice(recipient.email(), content)) emails++;
            } catch (RuntimeException error) {
                failures.add(recipient.studentCode());
            }
            if (recipient.userId() != null) {
                notifications.sendInAppNotification(
                        recipient.userId(),
                        "INTRODUCTION_LETTER_PICKUP",
                        subject,
                        body,
                        "/student/dashboard");
                inApp++;
            }
        }
        audit.logAction(
                actorId,
                "NOTIFY_INTRODUCTION_LETTER_PICKUP",
                "InternshipTerm",
                termId,
                failures.isEmpty() ? "SUCCESS" : "PARTIAL",
                Map.of(
                        "recipients",
                        batch.recipients().size(),
                        "emailsQueued",
                        emails,
                        "notificationsCreated",
                        inApp),
                null);
        return new NoticeResult(batch.recipients().size(), 0, emails, inApp, failures);
    }
}
