package com.internlink.core.application.organization;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.shared.enums.ReportTemplateType;
import com.internlink.core.shared.enums.UserRole;

import java.util.UUID;

/** Dữ liệu dựng sẵn cho test biểu mẫu: hai khoa KHMT/MMT và mẫu theo khoa. */
final class TemplateTestFixtures {

    static final Department KHMT = department("KHMT");
    static final Department MMT = department("MMT");

    private TemplateTestFixtures() {
    }

    static Department department(String code) {
        Department d = Department.builder().code(code).name(code).build();
        d.setId(UUID.randomUUID());
        return d;
    }

    static User facultyAdmin(Department department) {
        User u = User.builder().role(UserRole.FACULTY_ADMIN).department(department).build();
        u.setId(UUID.randomUUID());
        return u;
    }

    static ReportTemplateVersion template(Department department, ReportTemplateType type, String programCode) {
        ReportTemplateVersion t = ReportTemplateVersion.builder()
            .department(department)
            .title(type + "-" + (department != null ? department.getCode() : "TRUONG") + "-" + programCode)
            .reportType(type)
            .programCode(programCode)
            .fileUrl("internlink/templates/" + UUID.randomUUID() + ".doc")
            .fileName(type + ".doc")
            .fileSizeBytes(1L)
            .mimeType("application/msword")
            .versionNumber(1)
            .isMandatory(true)
            .isActive(true)
            .build();
        t.setId(UUID.randomUUID());
        return t;
    }
}
