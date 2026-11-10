package com.internlink.core.application.placement;

import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.PlacementTask;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.ForbiddenException;

/**
 * Chính sách kiểm soát phân quyền truy cập nhiệm vụ thực tập (PlacementTask Access Policy).
 * Đảm bảo sinh viên, mentor và các vai trò chỉ được thao tác trong phạm vi hồ sơ được phân công.
 */
public final class PlacementTaskAccessPolicy {

    private PlacementTaskAccessPolicy() {}

    public static boolean canView(User actor, InternshipPlacement placement) {
        if (actor == null || placement == null) {
            return false;
        }
        if (actor.getRole() == UserRole.ADMIN) {
            return true;
        }
        if (actor.getRole() == UserRole.STUDENT) {
            return placement.getStudent() != null && actor.getId().equals(placement.getStudent().getId());
        }
        if (actor.getRole() == UserRole.COMPANY_MENTOR) {
            return placement.getMentor() != null && actor.getId().equals(placement.getMentor().getId());
        }
        if (actor.getRole() == UserRole.LECTURER) {
            return placement.getLecturer() != null && actor.getId().equals(placement.getLecturer().getId());
        }
        if (actor.getRole() == UserRole.FACULTY_ADMIN) {
            return DepartmentScope.sameDepartment(actor, DepartmentScope.idOf(placement));
        }
        return false;
    }

    public static void requireCanView(User actor, InternshipPlacement placement) {
        if (!canView(actor, placement)) {
            throw new ForbiddenException("Bạn không có quyền xem nhiệm vụ của hồ sơ thực tập này");
        }
    }

    public static boolean canCreate(User actor, InternshipPlacement placement) {
        if (actor == null || placement == null) {
            return false;
        }
        if (actor.getRole() == UserRole.ADMIN) {
            return true;
        }
        return actor.getRole() == UserRole.COMPANY_MENTOR
                && placement.getMentor() != null
                && actor.getId().equals(placement.getMentor().getId());
    }

    public static void requireCanCreate(User actor, InternshipPlacement placement) {
        if (!canCreate(actor, placement)) {
            throw new ForbiddenException("Chỉ Mentor được phân công hoặc Quản trị viên mới được giao nhiệm vụ");
        }
    }

    public static boolean canSubmit(User actor, PlacementTask task) {
        if (actor == null || task == null || task.getPlacement() == null) {
            return false;
        }
        return actor.getRole() == UserRole.STUDENT
                && task.getPlacement().getStudent() != null
                && actor.getId().equals(task.getPlacement().getStudent().getId());
    }

    public static void requireCanSubmit(User actor, PlacementTask task) {
        if (!canSubmit(actor, task)) {
            throw new ForbiddenException("Bạn không có quyền nộp báo cáo cho nhiệm vụ này");
        }
    }

    public static boolean canReview(User actor, PlacementTask task) {
        if (actor == null || task == null || task.getPlacement() == null) {
            return false;
        }
        if (actor.getRole() == UserRole.ADMIN) {
            return true;
        }
        return actor.getRole() == UserRole.COMPANY_MENTOR
                && task.getPlacement().getMentor() != null
                && actor.getId().equals(task.getPlacement().getMentor().getId());
    }

    public static void requireCanReview(User actor, PlacementTask task) {
        if (!canReview(actor, task)) {
            throw new ForbiddenException("Chỉ Mentor phụ trách hoặc Quản trị viên mới được đánh giá nhiệm vụ này");
        }
    }
}
