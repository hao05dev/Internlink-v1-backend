package com.internlink.core.application.system.notification;

import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import com.internlink.core.presentation.system.dto.response.EmailDeliveryResponse;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailDeliveryAdminService {
    private final EmailOutboxStore store;
    private final EmailProperties settings;
    private final SecurityGuard guard;
    private final AuditLogService audit;

    public EmailDeliveryResponse.Summary summary() {
        UUID viewer = departmentViewer();
        return new EmailDeliveryResponse.Summary(
                settings.isEnabled(),
                settings.getProvider(),
                store.summary(viewer),
                store.controls(),
                store.metrics(viewer));
    }

    /** Admin xem toàn hệ thống (null); Khoa chỉ xem email thuộc khoa mình. */
    private UUID departmentViewer() {
        guard.requireRole(UserRole.ADMIN, UserRole.FACULTY_ADMIN);
        var user = guard.currentUser();
        return user.getRole() == UserRole.ADMIN ? null : user.getId();
    }

    public EmailDeliveryResponse.Page page(
            String status, String scopeType, String sourceType, String templateKey,
            String recipient, int page, int size) {
        UUID viewer = departmentViewer();
        if (status != null
                && !status.isBlank()
                && !Set.of(
                                "PENDING",
                                "SENDING",
                                "RETRY",
                                "ACCEPTED",
                                "DELIVERED",
                                "BOUNCED",
                                "FAILED",
                                "UNKNOWN",
                                "SKIPPED")
                        .contains(status)) throw new BadRequestException("Trạng thái không hợp lệ");
        if (scopeType != null && !scopeType.isBlank()
                && !Set.of("SYSTEM", "DEPARTMENT", "COMPANY").contains(scopeType))
            throw new BadRequestException("Phạm vi email không hợp lệ");
        if (sourceType != null && sourceType.length() > 40)
            throw new BadRequestException("Nguồn gửi không hợp lệ");
        if (templateKey != null && templateKey.length() > 60)
            throw new BadRequestException("Mẫu email không hợp lệ");
        return store.page(
                new EmailDeliveryFilter(status, scopeType, sourceType, templateKey, recipient, viewer),
                Math.max(0, page), Math.max(1, Math.min(100, size)));
    }

    @Transactional
    public EmailDeliveryResponse.Controls controls(boolean paused, int limit, String reason) {
        guard.requireRole(UserRole.ADMIN);
        requireReason(reason);
        if (limit < 1 || limit > 2000)
            throw new BadRequestException("Giới hạn gửi cần từ 1 đến 2.000 lượt trong 24 giờ");
        store.updateControls(paused, limit);
        audit.logAction(
                guard.currentUser().getId(),
                "UPDATE_EMAIL_DELIVERY_CONTROL",
                "EmailOutbox",
                null,
                "SUCCESS",
                Map.of("paused", paused, "dailyLimit", limit, "reason", reason.strip()),
                null);
        return store.controls();
    }

    @Transactional
    public void retry(UUID id, boolean confirmedNotAccepted, String reason) {
        requireVisible(id);
        requireReason(reason);
        if (!store.retry(id, confirmedNotAccepted))
            throw new BadRequestException(
                    "Chỉ gửi lại FAILED, BOUNCED hoặc UNKNOWN đã xác nhận chưa được chấp nhận");
        audit.logAction(
                guard.currentUser().getId(),
                "RETRY_EMAIL_DELIVERY",
                "EmailOutbox",
                id,
                "SUCCESS",
                Map.of("confirmedNotAccepted", confirmedNotAccepted, "reason", reason.strip()),
                null);
    }

    @Transactional
    public void cancel(UUID id, String reason) {
        requireVisible(id);
        requireReason(reason);
        if (!store.cancel(id))
            throw new BadRequestException("Không thể hủy email đã gửi hoặc đang được xử lý");
        audit.logAction(
                guard.currentUser().getId(),
                "CANCEL_EMAIL_DELIVERY",
                "EmailOutbox",
                id,
                "SUCCESS",
                Map.of("reason", reason.strip()),
                null);
    }

    private void requireVisible(UUID id) {
        UUID viewer = departmentViewer();
        if (viewer != null && !store.visibleTo(id, viewer))
            throw new ForbiddenException("Email này không thuộc phạm vi khoa của bạn");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new BadRequestException("Cần lý do thao tác, tối đa 500 ký tự");
    }
}
