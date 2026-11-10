package com.internlink.core.application.organization;

import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.shared.security.ResourceAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Kỳ thực tập là cấp Trường; mỗi Khoa chỉ thấy và xử lý phần sinh viên thuộc khoa mình.
 * ADMIN và Văn phòng Trường thấy toàn bộ kỳ (có thể lọc theo khoa).
 */
@Component
@RequiredArgsConstructor
public class TermDepartmentScope {
    private final JpaStudentRosterRepository rosters;
    private final JpaInternshipPlacementRepository placements;
    private final JpaStudentFoundApplicationRepository applications;

    /** Khoa mà người thao tác bị giới hạn; null nghĩa là phạm vi toàn Trường. */
    public UUID restrictTo(User actor, UUID requestedDepartmentId) {
        if (ResourceAuthorization.isAdmin(actor) || ResourceAuthorization.isSchoolOffice(actor))
            return requestedDepartmentId;
        UUID own = DepartmentScope.idOfActor(actor);
        ResourceAuthorization.require(own != null);
        return own;
    }

    public UUID restrictTo(User actor) {
        return restrictTo(actor, null);
    }

    public List<StudentRoster> rosters(UUID termId, User actor) {
        UUID dept = restrictTo(actor);
        return dept == null ? rosters.findByTermId(termId) : rosters.findByTermIdAndProgramDepartmentId(termId, dept);
    }

    public List<StudentRoster> rostersOrdered(UUID termId, User actor) {
        UUID dept = restrictTo(actor);
        return dept == null ? rosters.findByTermIdOrderByStudentCodeAsc(termId)
            : rosters.findByTermIdAndProgramDepartmentIdOrderByStudentCodeAsc(termId, dept);
    }

    public List<InternshipPlacement> placements(UUID termId, User actor) {
        UUID dept = restrictTo(actor);
        return dept == null ? placements.findByTermId(termId) : placements.findByTermIdAndStudentDepartmentId(termId, dept);
    }

    /** Lần thực tập người thao tác được chấm trong kỳ: GVHD -> SV mình hướng dẫn, Khoa -> SV của khoa. */
    public List<InternshipPlacement> gradablePlacements(UUID termId, User actor) {
        if (actor.getRole() == UserRole.LECTURER)
            return placements.findByTermId(termId).stream()
                .filter(p -> ResourceAuthorization.supervises(actor, p)).toList();
        ResourceAuthorization.require(ResourceAuthorization.worksOnTerm(actor));
        return placements(termId, actor);
    }

    public List<StudentFoundApplication> applications(UUID termId, User actor, UUID requestedDepartmentId) {
        UUID dept = restrictTo(actor, requestedDepartmentId);
        return dept == null ? applications.findByTermId(termId)
            : applications.findByTermIdAndStudentDepartmentId(termId, dept);
    }

    public List<StudentFoundApplication> applicationsWithStatus(UUID termId, String status, User actor) {
        UUID dept = restrictTo(actor);
        return dept == null ? applications.findByTermIdAndStatus(termId, status)
            : applications.findByTermIdAndStatusAndStudentDepartmentId(termId, status, dept);
    }
}
