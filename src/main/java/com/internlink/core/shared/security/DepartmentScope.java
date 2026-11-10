package com.internlink.core.shared.security;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.StudentFoundApplication;

import java.util.UUID;

/**
 * Khoa phụ trách một hồ sơ thực tập. Kỳ thực tập là cấp Trường, nên khoa luôn là khoa của sinh viên
 * (khoa quản lý ngành của SV trong danh sách học phần, hoặc khoa gắn với tài khoản SV).
 */
public final class DepartmentScope {
    private DepartmentScope() {}

    public static Department of(StudentRoster roster) {
        if (roster == null) return null;
        if (roster.getProgram() != null && roster.getProgram().getDepartment() != null)
            return roster.getProgram().getDepartment();
        return roster.getClaimedUser() != null ? roster.getClaimedUser().getDepartment() : null;
    }

    public static Department ofStudent(User student) {
        return student != null ? student.getDepartment() : null;
    }

    public static Department of(InternshipPlacement placement) {
        return placement != null ? ofStudent(placement.getStudent()) : null;
    }

    public static Department of(StudentFoundApplication application) {
        return application != null ? ofStudent(application.getStudent()) : null;
    }

    public static UUID id(Department department) {
        return department != null ? department.getId() : null;
    }

    public static UUID idOf(StudentRoster roster) { return id(of(roster)); }

    public static UUID idOf(InternshipPlacement placement) { return id(of(placement)); }

    public static UUID idOf(StudentFoundApplication application) { return id(of(application)); }

    public static UUID idOfStudent(User student) { return id(ofStudent(student)); }

    /** Khoa của người dùng nội bộ (Quản lý Khoa, GVHD); null với tài khoản cấp Trường. */
    public static UUID idOfActor(User actor) {
        return actor != null && actor.getDepartment() != null ? actor.getDepartment().getId() : null;
    }

    public static boolean sameDepartment(User actor, UUID departmentId) {
        UUID own = idOfActor(actor);
        return own != null && own.equals(departmentId);
    }
}
