package com.internlink.core.application.system.notification;

import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.AssessmentScheme;
import com.internlink.core.domain.placement.*;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.UserRole;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class PlacementWorkflowNotificationHelper {
    private final NotificationService notifications;
    private final JpaUserRepository users;
    private final JpaInternshipPlacementRepository placements;

    public void task(PlacementTask task, UUID actor, String type) {
        var p = task.getPlacement();
        boolean submitted = "TASK_SUBMITTED".equals(type);
        send(
                submitted ? p.getMentor() : p.getStudent(),
                actor,
                type,
                "Nhiệm vụ thực tập đã cập nhật",
                "Hãy kiểm tra nhiệm vụ trong hồ sơ thực tập.");
        send(
                p.getLecturer(),
                actor,
                type,
                "Nhiệm vụ thực tập đã cập nhật",
                "Hãy kiểm tra tiến trình nhiệm vụ của sinh viên.");
    }

    public void report(InternshipPlacement p, UUID actor, boolean submitted) {
        send(
                submitted ? p.getLecturer() : p.getStudent(),
                actor,
                submitted ? "REPORT_SUBMITTED" : "REPORT_REVIEWED",
                submitted ? "Báo cáo thực tập chờ phản hồi" : "Báo cáo thực tập đã có phản hồi",
                "Hãy kiểm tra báo cáo trong hồ sơ thực tập.");
    }

    public void daily(InternshipPlacement p, UUID actor, boolean submitted) {
        send(
                submitted
                        ? (p.getMentor() == null ? p.getLecturer() : p.getMentor())
                        : p.getStudent(),
                actor,
                submitted ? "DAILY_LOG_SUBMITTED" : "DAILY_LOG_REVIEWED",
                "Nhật ký thực tập đã cập nhật",
                "Hãy kiểm tra nhật ký ngày trong hồ sơ thực tập.");
    }

    public void evidence(InternshipPlacement p, UUID actor, boolean pending) {
        send(
                pending ? p.getLecturer() : p.getStudent(),
                actor,
                pending ? "EVIDENCE_PENDING" : "EVIDENCE_VERIFIED",
                pending ? "Minh chứng chờ xác minh" : "Minh chứng đã được xác minh",
                "Hãy kiểm tra phiếu đánh giá trong hồ sơ thực tập.");
    }

    public void changed(InternshipPlacement p, UUID actor, String type, String title) {
        for (var user : Arrays.asList(p.getStudent(), p.getMentor(), p.getLecturer()))
            send(user, actor, type, title, "Hãy kiểm tra hồ sơ và hạn xử lý mới.");
        if (p.getTerm() != null && p.getTerm().getDepartment() != null)
            for (var user :
                    users.findByDepartmentIdAndRole(
                            p.getTerm().getDepartment().getId(), UserRole.FACULTY_ADMIN))
                send(user, actor, type, title, "Hồ sơ thuộc khoa có cập nhật cần theo dõi.");
    }

    public void scheme(AssessmentScheme scheme, UUID actor) {
        for (var p : placements.findByTermId(scheme.getTerm().getId()))
            if (p.getAssessmentScheme() != null
                    && p.getAssessmentScheme().getId().equals(scheme.getId()))
                for (var user : Arrays.asList(p.getStudent(), p.getMentor(), p.getLecturer()))
                    send(
                            user,
                            actor,
                            "ASSESSMENT_SCHEME_CHANGED",
                            "Phương án đánh giá có cập nhật",
                            "Hãy xem cấu hình đánh giá của kỳ thực tập.");
        for (var user :
                users.findByDepartmentIdAndRole(
                        scheme.getTerm().getDepartment().getId(), UserRole.FACULTY_ADMIN))
            send(
                    user,
                    actor,
                    "ASSESSMENT_SCHEME_CHANGED",
                    "Phương án đánh giá có cập nhật",
                    "Hãy xem cấu hình đánh giá của kỳ thực tập.");
    }

    private void send(User user, UUID actor, String type, String title, String body) {
        if (user == null || user.getId().equals(actor)) return;
        String path =
                switch (user.getRole()) {
                    case STUDENT -> "/student/internship-record";
                    case COMPANY_MENTOR -> "/mentor/internship-record";
                    case LECTURER -> "/lecturer/internship-record";
                    case FACULTY_ADMIN -> "/faculty/evaluations";
                    case COMPANY_REP -> "/company/interns";
                    case ADMIN -> "/admin/notifications";
                    case SCHOOL_OFFICE -> "/school-office/notifications";
                };
        notifications.sendNotification(user.getId(), type, title, body, path);
    }
}
