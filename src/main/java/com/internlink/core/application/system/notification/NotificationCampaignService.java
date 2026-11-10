package com.internlink.core.application.system.notification;

import com.internlink.core.application.organization.TermDepartmentScope;
import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.infrastructure.persistence.jdbc.NotificationCampaignRepository;
import com.internlink.core.infrastructure.persistence.jdbc.NotificationCampaignAudienceRepository;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.system.dto.request.NotificationCampaignRequest;
import com.internlink.core.presentation.system.dto.response.NotificationCampaignResponse.*;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationCampaignService {
    private final NotificationCampaignRepository repository;
    private final NotificationCampaignAudienceRepository audiences;
    private final JpaInternshipTermRepository terms;
    private final JpaUserRepository users;
    private final SecurityGuard guard;
    private final AuditLogService audit;
    private final EmailRecipientPolicy recipients;
    private final TermDepartmentScope termScope;

    @Transactional(readOnly = true)
    public Preview preview(NotificationCampaignRequest request) {
        validate(request);
        var term = authorize(request.termId());
        TermGuard.requireNotClosed(term);
        var targets = audiences.audience(
                request.termId(), request.audience(), termScope.restrictTo(actor()));
        int eligible = 0;
        for (var target : targets)
            if (target.userId() == null
                    || users.findById(target.userId())
                            .filter(user -> recipients.allows(user, eventType(request.purpose())))
                            .isPresent()) eligible++;
        return new Preview(
                targets.size(),
                (int) targets.stream().filter(t -> t.userId() != null).count(),
                eligible,
                targets.stream()
                        .limit(20)
                        .map(
                                t ->
                                        new Recipient(
                                                null,
                                                t.userId(),
                                                t.email(),
                                                t.name(),
                                                t.studentCode(),
                                                "PREVIEW",
                                                null,
                                                null))
                        .toList());
    }

    @Transactional
    public Detail create(NotificationCampaignRequest request) {
        validate(request);
        var term = authorize(request.termId());
        TermGuard.requireNotClosed(term);
        var actor = actor();
        String hash = hash(request);
        repository.lockRequest(actor.getId(), request.requestKey());
        var previous = repository.byRequest(actor.getId(), request.requestKey());
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().get("request_hash")))
                throw new BadRequestException("Request key đã được dùng với nội dung khác");
            return repository.detail((UUID) previous.get().get("id"), 0, 20);
        }
        var targets = audiences.audience(
                request.termId(), request.audience(), termScope.restrictTo(actor));
        if (targets.isEmpty()) throw new BadRequestException("Không có người nhận phù hợp");
        UUID id = repository.create(actor.getId(), hash, request, targets);
        audit.logAction(
                actor.getId(),
                "CREATE_NOTIFICATION_CAMPAIGN",
                "NotificationCampaign",
                id,
                "SUCCESS",
                Map.of(
                        "termId",
                        request.termId().toString(),
                        "audience",
                        request.audience(),
                        "channel",
                        request.channel(),
                        "total",
                        targets.size()),
                null);
        return repository.detail(id, 0, 20);
    }

    @Transactional(readOnly = true)
    public Page page(int page, int size) {
        var actor = actor();
        guard.requireRole(UserRole.FACULTY_ADMIN, UserRole.ADMIN);
        if (actor.getRole() != UserRole.ADMIN && actor.getDepartment() == null)
            throw new ForbiddenException("Tài khoản chưa có khoa phụ trách");
        return repository.page(
                actor.getRole() == UserRole.ADMIN ? null : actor.getDepartment().getId(),
                Math.max(0, page),
                Math.max(1, Math.min(50, size)));
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID id, int page) {
        authorize(repository.campaign(id, false));
        return repository.detail(id, Math.max(0, page), 20);
    }

    @Transactional
    public void cancel(UUID id) {
        var campaign = repository.campaign(id, true);
        authorize(campaign);
        repository.cancel(id);
        audit.logAction(
                actor().getId(),
                "CANCEL_NOTIFICATION_CAMPAIGN",
                "NotificationCampaign",
                id,
                "SUCCESS",
                Map.of(),
                null);
    }

    @Transactional
    public void retry(UUID id) {
        var campaign = repository.campaign(id, true);
        var term = authorize(campaign);
        TermGuard.requireNotClosed(term);
        if (!"COMPLETED".equals(campaign.status()))
            throw new BadRequestException("Chỉ thử lại người nhận lỗi của campaign đã kết thúc");
        repository.retryFailures(id);
        audit.logAction(
                actor().getId(),
                "RETRY_NOTIFICATION_CAMPAIGN",
                "NotificationCampaign",
                id,
                "SUCCESS",
                Map.of(),
                null);
    }

    private InternshipTerm authorize(UUID termId) {
        guard.requireRole(UserRole.FACULTY_ADMIN, UserRole.ADMIN);
        var term =
                terms.findById(termId)
                        .orElseThrow(() -> new ResourceNotFoundException("Term", "id", termId));
        ResourceAuthorization.require(ResourceAuthorization.worksOnTerm(actor()));
        return term;
    }

    /** Quản lý Khoa chỉ xem/điều khiển campaign do người cùng khoa tạo. */
    private InternshipTerm authorize(NotificationCampaignRepository.Campaign campaign) {
        var term = authorize(campaign.termId());
        var actor = actor();
        if (!ResourceAuthorization.isAdmin(actor)) {
            var creator = users.findById(campaign.creator()).orElse(null);
            ResourceAuthorization.require(creator != null
                    && DepartmentScope.sameDepartment(actor, DepartmentScope.idOfActor(creator)));
        }
        return term;
    }

    private User actor() {
        return users.findById(guard.currentUser().getId())
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new ForbiddenException("Tài khoản không hoạt động"));
    }

    private void validate(NotificationCampaignRequest request) {
        if (!Set.of("INTRODUCTION_LETTER", "GENERAL").contains(request.purpose())
                || !Set.of(
                                "ALL_STUDENTS",
                                "MENTORS",
                                "LECTURERS",
                                "COMPANY_REPS",
                                "FACULTY_ADMINS",
                                "ADMINS")
                        .contains(request.audience())
                || !Set.of("BOTH", "IN_APP", "EMAIL").contains(request.channel())
                || request.subject() == null
                || request.subject().isBlank()
                || request.subject().length() > 255
                || request.subject().contains("\r")
                || request.subject().contains("\n")
                || request.body() == null
                || request.body().isBlank()
                || request.body().length() > 20000)
            throw new BadRequestException("Nội dung hoặc phạm vi campaign không hợp lệ");
        if ("INTRODUCTION_LETTER".equals(request.purpose())
                && !"ALL_STUDENTS".equals(request.audience()))
            throw new BadRequestException("Phạm vi người nhận không đúng mục đích");
        if ("ADMINS".equals(request.audience())) guard.requireRole(UserRole.ADMIN);
    }

    static String eventType(String purpose) {
        return switch (purpose) {
            case "INTRODUCTION_LETTER" -> "INTRODUCTION_LETTER_PICKUP";
            default -> "GENERAL_ANNOUNCEMENT";
        };
    }

    private String hash(NotificationCampaignRequest r) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (r.termId()
                                                            + "\n"
                                                            + r.purpose()
                                                            + "\n"
                                                            + r.audience()
                                                            + "\n"
                                                            + r.channel()
                                                            + "\n"
                                                            + r.subject()
                                                            + "\n"
                                                            + r.body()
                                                            // Giữ "\n" cuối để hash cũ (trước khi bỏ templateId) không đổi.
                                                            + "\n")
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
