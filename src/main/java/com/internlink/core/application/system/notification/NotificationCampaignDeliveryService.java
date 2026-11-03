package com.internlink.core.application.system.notification;

import com.internlink.core.application.system.NotificationService;
import com.internlink.core.infrastructure.persistence.jdbc.NotificationCampaignRepository;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.security.ResourceAuthorization;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationCampaignDeliveryService {
    private final NotificationCampaignRepository repository;
    private final JpaInternshipTermRepository terms;
    private final JpaUserRepository users;
    private final NotificationService notifications;
    private final EmailOutboxService emails;
    private final EmailRenderer renderer;
    private final PlatformTransactionManager transactions;

    public void process(NotificationCampaignRepository.Work row) {
        var tx = new TransactionTemplate(transactions);
        try {
            tx.executeWithoutResult(
                    state -> {
                        var campaign = repository.campaign(row.campaignId(), true);
                        if (!repository.owns(row)) return;
                        var term = terms.findById(campaign.termId()).orElse(null);
                        var creator = users.findById(campaign.creator()).orElse(null);
                        if (!"RUNNING".equals(campaign.status())
                                || term == null
                                || "CLOSED".equals(term.getStatus().name())
                                || creator == null
                                || !Boolean.TRUE.equals(creator.getIsActive())
                                || (creator.getRole() != UserRole.ADMIN
                                        && creator.getRole() != UserRole.FACULTY_ADMIN)
                                || !ResourceAuthorization.managesDepartment(
                                        creator, term.getDepartment().getId())) {
                            repository.finish(row, "SKIPPED", "campaign_not_authorized");
                            return;
                        }
                        var recipient =
                                row.userId() == null
                                        ? users.findByEmail(row.email()).orElse(null)
                                        : users.findById(row.userId()).orElse(null);
                        if (recipient != null
                                && (!Boolean.TRUE.equals(recipient.getIsActive())
                                        || !row.email().equalsIgnoreCase(recipient.getEmail()))) {
                            repository.finish(row, "SKIPPED", "recipient_changed");
                            return;
                        }
                        String subject =
                                render(campaign.subject(), row, term.getTermName())
                                        .replace("\r", " ")
                                        .replace("\n", " ");
                        String body = render(campaign.body(), row, term.getTermName());
                        String type = NotificationCampaignService.eventType(campaign.purpose());
                        boolean inApp = !"EMAIL".equals(campaign.channel()) && recipient != null;
                        boolean email = false;
                        if (inApp) {
                            UUID notificationId =
                                    notifications.publishNotification(
                                            recipient.getId(),
                                            type,
                                            subject,
                                            body,
                                            null,
                                            "BOTH".equals(campaign.channel()));
                            repository.attachNotification(row, notificationId);
                            emails.attachContext(
                                    notificationId,
                                    new EmailDispatchContext(
                                            campaign.creator(), "NOTIFICATION_CAMPAIGN",
                                            campaign.id(), "DEPARTMENT", term.getDepartment().getId()));
                        } else if (!"IN_APP".equals(campaign.channel())) {
                            String key = "campaign:" + campaign.id() + ":" + row.id();
                            var content = renderer.render(
                                    EmailTemplate.CAMPAIGN,
                                    Map.of(
                                            "subject", subject.replaceFirst("^\\[InternLink\\]\\s*", ""),
                                            "body", body));
                            email = emails.enqueueNotice(
                                    row.email(), content, type, key,
                                    new EmailDispatchContext(
                                            campaign.creator(), "NOTIFICATION_CAMPAIGN",
                                            campaign.id(), "DEPARTMENT", term.getDepartment().getId()));
                            repository.attach(row, key);
                        }
                        repository.finish(
                                row,
                                inApp || email ? "QUEUED" : "SKIPPED",
                                inApp || email ? null : "channel_disabled_or_opted_out");
                    });
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(
                    state -> {
                        repository.campaign(row.campaignId(), true);
                        if (repository.owns(row))
                            repository.finish(row, "FAILED", "processing_failed");
                    });
        }
    }

    private String render(String value, NotificationCampaignRepository.Work row, String term) {
        return value.replace("{{studentName}}", row.name() == null ? "" : row.name())
                .replace("{{studentCode}}", row.code() == null ? "" : row.code())
                .replace("{{termName}}", term);
    }
}
