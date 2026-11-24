package com.internlink.core.application.organization;

import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.security.DepartmentScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Đồng bộ hai chiều: VP Trường nhập thì Khoa liên quan biết; Khoa bổ sung thì VP Trường biết. */
@Component
@RequiredArgsConstructor
public class RosterImportNotifier {
    static final String TYPE = "STUDENT_ROSTER_UPDATED";
    static final String FACULTY_LINK = "/faculty/roster";
    static final String OFFICE_LINK = "/school-office/roster";

    private final NotificationService notifications;
    private final JpaUserRepository users;

    public void schoolOfficeImported(InternshipTerm term, Collection<StudentRoster> changed) {
        Map<UUID, Long> perDepartment = new LinkedHashMap<>();
        for (StudentRoster roster : changed) {
            UUID department = DepartmentScope.idOf(roster);
            if (department != null) perDepartment.merge(department, 1L, Long::sum);
        }
        perDepartment.forEach((department, count) -> {
            for (User admin : users.findByDepartmentIdAndRole(department, UserRole.FACULTY_ADMIN)) {
                notifications.sendNotification(admin.getId(), TYPE,
                    "Văn phòng Trường cập nhật danh sách sinh viên thực tập",
                    "Văn phòng Trường đã nhập " + count + " sinh viên của khoa vào kỳ " + term.getTermName()
                        + ". Danh sách đã có ở mục Danh sách sinh viên.",
                    FACULTY_LINK);
            }
        });
    }

    public void facultyAdded(InternshipTerm term, User faculty, int count) {
        if (count == 0) return;
        String department = faculty.getDepartment() != null ? faculty.getDepartment().getName() : "Khoa";
        for (User office : users.findByRole(UserRole.SCHOOL_OFFICE)) {
            notifications.sendNotification(office.getId(), TYPE,
                department + " bổ sung sinh viên thực tập",
                department + " đã bổ sung " + count + " sinh viên vào danh sách kỳ " + term.getTermName() + ".",
                OFFICE_LINK);
        }
    }
}
