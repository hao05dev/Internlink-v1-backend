package com.internlink.core.application.placement.impl;

import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.LearningAgreement;
import com.internlink.core.shared.enums.PlacementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlacementNotificationHelper {

    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public void logAndNotifyActivation(UUID actorId, InternshipPlacement saved, LearningAgreement agreement, User mentor) {
        auditLogService.logAction(
            actorId,
            "ACTIVATE_PLACEMENT",
            "InternshipPlacement",
            saved.getId(),
            "SUCCESS",
            Map.of("agreementId", agreement.getId(), "mentorId", mentor.getId(), "studentId", agreement.getStudent().getId()),
            null
        );

        UUID studentId = agreement.getStudent().getId();
        notificationService.sendNotification(
            studentId,
            "PLACEMENT_ACTIVATED",
            "Đợt thực tập đã được kích hoạt",
            "Đợt thực tập của bạn tại " + agreement.getCompany().getCompanyName() + " đã được tạo. Trạng thái: Chuẩn bị (PREPARING).",
            "/placements/" + saved.getId()
        );

        notificationService.sendNotification(
            mentor.getId(),
            "MENTOR_ASSIGNED",
            "Phân công hướng dẫn thực tập",
            "Bạn được phân công làm Mentor hướng dẫn sinh viên " + agreement.getStudent().getFullName(),
            "/placements/" + saved.getId()
        );
    }

    public void logAndNotifyLecturerAssignment(UUID actorId, InternshipPlacement placement, User lecturer, UUID previousId) {
        auditLogService.logAction(actorId, "ASSIGN_LECTURER", "InternshipPlacement", placement.getId(),
            "SUCCESS", lecturerChange(previousId, lecturer.getId()), null);
        notificationService.sendNotification(lecturer.getId(), "LECTURER_ASSIGNED", "Phân công hướng dẫn thực tập",
            "Bạn được phân công hướng dẫn sinh viên " + placement.getStudent().getFullName(),
            "/lecturer/supervision");
        notificationService.sendNotification(placement.getStudent().getId(), "LECTURER_ASSIGNED", "Giảng viên hướng dẫn",
            "Giảng viên hướng dẫn của bạn là " + lecturer.getFullName(), "/student/dashboard");
    }

    private static Map<String, Object> lecturerChange(UUID previousId, UUID lecturerId) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("previousLecturerId", previousId);
        changes.put("lecturerId", lecturerId);
        return changes;
    }

    public void logAndNotifyStatusChange(InternshipPlacement saved, PlacementStatus previousStatus, PlacementStatus newStatus) {
        auditLogService.logAction(
            null,
            "UPDATE_PLACEMENT_STATUS_" + newStatus.name(),
            "InternshipPlacement",
            saved.getId(),
            "SUCCESS",
            Map.of("previousStatus", previousStatus.name(), "newStatus", newStatus.name()),
            null
        );

        notificationService.sendNotification(
            saved.getStudent().getId(),
            "PLACEMENT_STATUS_CHANGED",
            "Trạng thái thực tập cập nhật",
            "Đợt thực tập của bạn đã chuyển sang trạng thái: " + newStatus.name(),
            "/placements/" + saved.getId()
        );
    }
}
