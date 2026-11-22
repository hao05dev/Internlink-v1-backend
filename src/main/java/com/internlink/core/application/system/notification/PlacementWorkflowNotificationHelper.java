package com.internlink.core.application.system.notification;

import com.internlink.core.shared.security.DepartmentScope;
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

    /** Sinh viên nộp một file hồ sơ: báo GVHD vào duyệt. */
    public void dossierSubmitted(InternshipPlacement p, UUID actor, String itemLabel) {
        User lecturer = p.getLecturer();
        if (lecturer == null || lecturer.getId().equals(actor)) return;
        String student = p.getStudent() != null ? p.getStudent().getFullName() : "Sinh viên";
        notifications.sendNotification(lecturer.getId(), "DOSSIER_SUBMITTED",
                "Hồ sơ thực tập chờ duyệt",
                student + " vừa nộp " + itemLabel + ". Hãy kiểm tra hồ sơ trước khi chấm điểm.",
                "/lecturer/internship-record/" + p.getId());
    }

    /** GVHD duyệt hoặc yêu cầu nộp lại một file hồ sơ: báo sinh viên. */
    public void dossierReviewed(InternshipPlacement p, UUID actor, String itemLabel, boolean approved, String note) {
        User student = p.getStudent();
        if (student == null || student.getId().equals(actor)) return;
        String body = approved
                ? itemLabel + " đã được GVHD duyệt."
                : itemLabel + " cần nộp lại: " + note;
        notifications.sendNotification(student.getId(), approved ? "DOSSIER_APPROVED" : "DOSSIER_REVISION_REQUIRED",
                approved ? "Hồ sơ thực tập đã được duyệt" : "Hồ sơ thực tập cần nộp lại",
                body, "/student/documents");
    }

    public void changed(InternshipPlacement p, UUID actor, String type, String title) {
        for (var user : Arrays.asList(p.getStudent(), p.getMentor(), p.getLecturer()))
            send(user, actor, type, title, "Hãy kiểm tra hồ sơ và hạn xử lý mới.");
        if (DepartmentScope.idOf(p) != null)
            for (var user : users.findByDepartmentIdAndRole(DepartmentScope.idOf(p), UserRole.FACULTY_ADMIN))
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
                        scheme.getProgram().getDepartment().getId(), UserRole.FACULTY_ADMIN))
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
