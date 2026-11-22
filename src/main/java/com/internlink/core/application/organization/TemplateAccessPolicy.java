package com.internlink.core.application.organization;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.infrastructure.persistence.jpa.JpaDepartmentRepository;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Quyền quản lý biểu mẫu: Quản lý Khoa chỉ thao tác trên mẫu của khoa mình;
 * Quản trị viên quản lý mọi khoa và mẫu chung cấp trường (department NULL).
 */
@Component
@RequiredArgsConstructor
public class TemplateAccessPolicy {

    private final JpaDepartmentRepository departmentRepository;

    public void verifyManager(User user) {
        if (user == null || (user.getRole() != UserRole.FACULTY_ADMIN && user.getRole() != UserRole.ADMIN)) {
            throw new ForbiddenException("Chỉ Quản lý Khoa hoặc Quản trị viên mới có quyền quản lý biểu mẫu");
        }
    }

    public boolean canManage(User actor, ReportTemplateVersion template) {
        if (actor == null) return false;
        if (actor.getRole() == UserRole.ADMIN) return true;
        if (actor.getRole() != UserRole.FACULTY_ADMIN) return false;
        UUID templateDept = departmentIdOf(template);
        return templateDept != null && templateDept.equals(departmentIdOf(actor));
    }

    public void verifyCanManage(User actor, ReportTemplateVersion template) {
        verifyManager(actor);
        if (!canManage(actor, template)) {
            throw new ForbiddenException("Bạn chỉ được quản lý biểu mẫu của khoa mình");
        }
    }

    /** Quản lý Khoa luôn upload vào khoa của mình; Quản trị viên chọn khoa hoặc để trống (mẫu chung cấp trường). */
    public Department resolveUploadDepartment(UUID requestedDepartmentId, User uploader) {
        verifyManager(uploader);
        if (uploader.getRole() == UserRole.FACULTY_ADMIN) {
            if (uploader.getDepartment() == null) {
                throw new BadRequestException("Tài khoản Quản lý Khoa chưa được gán khoa, không thể treo biểu mẫu");
            }
            return uploader.getDepartment();
        }
        if (requestedDepartmentId == null) {
            return uploader.getDepartment();
        }
        return departmentRepository.findById(requestedDepartmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", requestedDepartmentId));
    }

    public static UUID departmentIdOf(User user) {
        return user != null && user.getDepartment() != null ? user.getDepartment().getId() : null;
    }

    public static UUID departmentIdOf(ReportTemplateVersion template) {
        return template != null && template.getDepartment() != null ? template.getDepartment().getId() : null;
    }

    public static boolean sameDepartment(ReportTemplateVersion a, ReportTemplateVersion b) {
        return Objects.equals(departmentIdOf(a), departmentIdOf(b));
    }
}
