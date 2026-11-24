package com.internlink.core.application.placement;

import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

/** Thông báo theo từng bước của Phiếu tiếp nhận (SV, VP Trường, Khoa, GVHD). */
@Component
@RequiredArgsConstructor
public class AcceptanceFormNotifier {
    static final String STUDENT_LINK = "/student/acceptance-form";
    static final String OFFICE_LINK = "/school-office/acceptance-forms";
    static final String FACULTY_LINK = "/faculty/acceptance-forms";

    private final NotificationService notifications;
    private final JpaUserRepository users;

    public void submitted(StudentFoundApplication app) {
        for (User office : users.findByRole(UserRole.SCHOOL_OFFICE)) {
            notifications.sendNotification(office.getId(), "ACCEPTANCE_FORM_SUBMITTED",
                "Phiếu tiếp nhận chờ nhận bản giấy",
                "Sinh viên " + app.getStudent().getFullName() + " đã nộp Form Phiếu tiếp nhận tại "
                    + app.getHostName() + ". Đối chiếu khi SV nộp bản giấy tại Văn phòng Trường.",
                OFFICE_LINK);
        }
    }

    public void received(StudentFoundApplication app) {
        toStudent(app, "ACCEPTANCE_FORM_RECEIVED", "Văn phòng Trường đã nhận Phiếu tiếp nhận",
            "Phiếu tiếp nhận tại " + app.getHostName() + " đã được tiếp nhận và chuyển Khoa xét duyệt.");
        var departmentId = DepartmentScope.idOf(app);
        for (User admin : users.findByDepartmentIdAndRole(departmentId, UserRole.FACULTY_ADMIN)) {
            notifications.sendNotification(admin.getId(), "ACCEPTANCE_FORM_RECEIVED",
                "Phiếu tiếp nhận chờ Khoa duyệt",
                "Phiếu của sinh viên " + app.getStudent().getFullName() + " tại " + app.getHostName()
                    + " đã được Văn phòng Trường tiếp nhận.",
                FACULTY_LINK);
        }
    }

    public void returned(StudentFoundApplication app, String by, String note) {
        toStudent(app, "ACCEPTANCE_FORM_REVISION", "Phiếu tiếp nhận cần chỉnh sửa",
            by + " yêu cầu chỉnh sửa phiếu tại " + app.getHostName() + ": " + note);
    }

    public void rejected(StudentFoundApplication app, String note) {
        toStudent(app, "ACCEPTANCE_FORM_REJECTED", "Phiếu tiếp nhận không được duyệt",
            "Khoa không duyệt đơn vị " + app.getHostName() + ": " + note);
    }

    public void approved(StudentFoundApplication app, User lecturer) {
        toStudent(app, "ACCEPTANCE_FORM_APPROVED", "Khoa đã duyệt Phiếu tiếp nhận",
            "Đơn vị " + app.getHostName() + " đã được duyệt. Chờ Văn phòng Trường công bố danh sách SV đủ điều kiện thực tập.");
        if (lecturer != null) {
            notifications.sendNotification(lecturer.getId(), "LECTURER_ASSIGNED",
                "Phân công hướng dẫn thực tập",
                "Bạn đã được phân công hướng dẫn sinh viên " + app.getStudent().getFullName()
                    + " tại " + app.getHostName() + ".",
                "/lecturer/supervision");
        }
    }

    /** Gửi cả thông báo trong app và email: SV có tên trong danh sách, đến sảnh VP Trường nhận giấy tờ. */
    public void published(StudentFoundApplication app, int batch, String newsLink, String note) {
        String extra = note == null || note.isBlank() ? "" : " Thông báo của Văn phòng Trường: " + note.trim();
        notifications.publishNotification(app.getStudent().getId(), "ACCEPTANCE_FORM_PUBLISHED",
            "Đã có danh sách SV đủ điều kiện thực tập thực tế",
            "Văn phòng Trường CNTT-TT đã công bố danh sách sinh viên đủ điều kiện thực tập thực tế kỳ "
                + app.getTerm().getTermName() + " (đợt " + batch + "). Bạn có tên trong danh sách với đơn vị "
                + app.getHostName() + ". Vui lòng đến sảnh Văn phòng Trường CNTT-TT nhận các giấy tờ liên quan "
                + "để đi thực tập thực tế. Danh sách đầy đủ (tệp Excel) xem trên trang Tin tức InternLink." + extra,
            newsLink, true);
    }

    /** Báo Quản lý Khoa của các khoa có SV trong đợt vừa công bố. */
    public void listPublished(InternshipTerm term, int batch, Map<UUID, Long> countByDepartment, String newsLink) {
        countByDepartment.forEach((departmentId, count) -> {
            for (User admin : users.findByDepartmentIdAndRole(departmentId, UserRole.FACULTY_ADMIN)) {
                notifications.sendNotification(admin.getId(), "ACCEPTANCE_LIST_PUBLISHED",
                    "Văn phòng Trường đã công bố danh sách thực tập",
                    "Đợt " + batch + " kỳ " + term.getTermName() + " có " + count
                        + " sinh viên của khoa. Danh sách đã đăng trên trang Tin tức.",
                    newsLink);
            }
        });
    }

    public void documentsCollected(StudentFoundApplication app) {
        toStudent(app, "ACCEPTANCE_DOCUMENTS_COLLECTED", "Đã nhận giấy tờ thực tập",
            "Văn phòng Trường xác nhận bạn đã nhận giấy tờ thực tập tại " + app.getHostName() + ".");
    }

    public void remindMissingForm(UUID studentId, InternshipTerm term) {
        notifications.sendNotification(studentId, "COMPANY_FORM_REMINDER",
            "Nhắc nhở: Nộp Phiếu tiếp nhận thực tập",
            "Bạn chưa nộp Phiếu tiếp nhận kỳ " + term.getTermName()
                + ". Hãy nộp Form và bản giấy tại Văn phòng Trường CNTT-TT để có tên trong danh sách xét duyệt.",
            STUDENT_LINK);
    }

    private void toStudent(StudentFoundApplication app, String type, String title, String message) {
        notifications.sendNotification(app.getStudent().getId(), type, title, message, STUDENT_LINK);
    }
}
