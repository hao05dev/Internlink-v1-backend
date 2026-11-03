package com.internlink.core.shared.security;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.LearningAgreement;
import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.ForbiddenException;

import java.util.UUID;

/** Quyền truy cập dựa trên quan hệ thực tế giữa người dùng và hồ sơ. */
public final class ResourceAuthorization {
    private ResourceAuthorization() {}

    public static boolean isAdmin(User actor) {
        return actor.getRole() == UserRole.ADMIN;
    }

    /** Văn phòng Trường CNTT-TT: phạm vi toàn trường, không gắn với một khoa. */
    public static boolean isSchoolOffice(User actor) {
        return actor != null && actor.getRole() == UserRole.SCHOOL_OFFICE;
    }

    /** Nghiệp vụ cấp Trường: đăng tin/bài, duyệt DN, giấy giới thiệu, tiếp nhận phiếu, phát giấy tờ. */
    public static boolean actsForSchool(User actor) {
        return isAdmin(actor) || isSchoolOffice(actor);
    }

    public static boolean managesDepartment(User actor, UUID departmentId) {
        return isAdmin(actor) || (actor.getRole() == UserRole.FACULTY_ADMIN
            && actor.getDepartment() != null && actor.getDepartment().getId().equals(departmentId));
    }

    public static boolean representsCompany(User actor, UUID companyId) {
        return actor.getRole() == UserRole.COMPANY_REP && actor.getCompany() != null
            && actor.getCompany().getId().equals(companyId);
    }

    public static boolean canManageJobs(User actor, UUID companyId) {
        if (actor == null) return false;
        return actsForSchool(actor) || representsCompany(actor, companyId);
    }

    public static boolean belongsToCompany(User actor, UUID companyId) {
        return (actor.getRole() == UserRole.COMPANY_REP || actor.getRole() == UserRole.COMPANY_MENTOR)
            && actor.getCompany() != null && actor.getCompany().getId().equals(companyId);
    }

    public static boolean canReadApplication(User actor, JobApplication application) {
        return isAdmin(actor)
            || actor.getRole() == UserRole.STUDENT && actor.getId().equals(application.getStudent().getId())
            || representsCompany(actor, application.getJob().getCompany().getId())
            || managesDepartment(actor, application.getJob().getDepartment().getId());
    }

    public static boolean canReadAgreement(User actor, LearningAgreement agreement) {
        return isAdmin(actor)
            || actor.getRole() == UserRole.STUDENT && actor.getId().equals(agreement.getStudent().getId())
            || representsCompany(actor, agreement.getCompany().getId())
            || actor.getRole() == UserRole.COMPANY_MENTOR
                && agreement.getOffer() != null && agreement.getOffer().getProposedMentor() != null
                && actor.getId().equals(agreement.getOffer().getProposedMentor().getId())
            || managesDepartment(actor, agreement.getDepartment().getId());
    }

    public static boolean canReadPlacement(User actor, InternshipPlacement placement) {
        return isAdmin(actor)
            || actor.getRole() == UserRole.STUDENT && actor.getId().equals(placement.getStudent().getId())
            || actor.getRole() == UserRole.COMPANY_MENTOR && placement.getMentor() != null
                && actor.getId().equals(placement.getMentor().getId())
            || actor.getRole() == UserRole.LECTURER && actor.getId().equals(placement.getLecturer().getId())
            || placement.getCompany() != null && representsCompany(actor, placement.getCompany().getId())
            || managesDepartment(actor, placement.getTerm().getDepartment().getId());
    }

    public static void require(boolean allowed) {
        if (!allowed) throw new ForbiddenException("Bạn không có quyền truy cập hồ sơ này");
    }
}
