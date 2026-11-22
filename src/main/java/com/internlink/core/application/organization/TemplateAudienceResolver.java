package com.internlink.core.application.organization;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Xác định sinh viên nào sẽ dùng biểu mẫu để chọn đúng mẫu của khoa sinh viên đó.
 * Có placementId (GV/mentor/DN mở mẫu của một SV): lấy theo sinh viên của đợt thực tập.
 * Không có: lấy theo chính người đang đăng nhập.
 */
@Component
@RequiredArgsConstructor
public class TemplateAudienceResolver {

    private static final Set<UserRole> STAFF_ROLES =
        Set.of(UserRole.ADMIN, UserRole.FACULTY_ADMIN, UserRole.SCHOOL_OFFICE);

    private final JpaUserRepository userRepository;
    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaStudentRosterRepository rosterRepository;

    @Transactional(readOnly = true)
    public TemplateAudience resolve(UUID viewerId, UUID placementId, UUID termIdHint) {
        User viewer = viewerId != null ? userRepository.findById(viewerId).orElse(null) : null;
        if (viewer == null) {
            return new TemplateAudience(termIdHint, null, null, null);
        }

        if (placementId != null) {
            Optional<InternshipPlacement> placement = placementRepository.findById(placementId)
                .filter(p -> canView(viewer, p));
            if (placement.isPresent()) {
                InternshipPlacement p = placement.get();
                UUID termId = p.getTerm() != null ? p.getTerm().getId() : termIdHint;
                return fromStudent(p.getStudent(), termId);
            }
        }

        if (viewer.getRole() == UserRole.STUDENT) {
            return fromStudent(viewer, termIdHint);
        }
        UUID departmentId = viewer.getDepartment() != null ? viewer.getDepartment().getId() : null;
        return new TemplateAudience(termIdHint, departmentId, null, null);
    }

    private TemplateAudience fromStudent(User student, UUID termId) {
        Optional<StudentRoster> roster = termId != null
            ? rosterRepository.findByTermIdAndClaimedUserId(termId, student.getId())
            : Optional.empty();
        if (roster.isEmpty()) {
            roster = rosterRepository.findFirstByClaimedUserIdOrderByCreatedAtDesc(student.getId());
        }

        UUID resolvedTerm = termId != null ? termId
            : roster.map(r -> r.getTerm() != null ? r.getTerm().getId() : null).orElse(null);
        UUID departmentId = student.getDepartment() != null
            ? student.getDepartment().getId()
            : roster.map(r -> r.getProgram() != null && r.getProgram().getDepartment() != null
                ? r.getProgram().getDepartment().getId() : null).orElse(null);
        String programCode = roster.map(r -> r.getProgram() != null ? r.getProgram().getCode() : null).orElse(null);
        Integer credits = roster.map(StudentRoster::getInternshipCredits).orElse(null);

        return new TemplateAudience(resolvedTerm, departmentId, programCode, credits);
    }

    private boolean canView(User viewer, InternshipPlacement placement) {
        if (STAFF_ROLES.contains(viewer.getRole())) {
            return true;
        }
        UUID viewerId = viewer.getId();
        if (isUser(placement.getStudent(), viewerId)
            || isUser(placement.getMentor(), viewerId)
            || isUser(placement.getLecturer(), viewerId)) {
            return true;
        }
        return viewer.getRole() == UserRole.COMPANY_REP
            && viewer.getCompany() != null
            && placement.getCompany() != null
            && Objects.equals(viewer.getCompany().getId(), placement.getCompany().getId());
    }

    private static boolean isUser(User user, UUID id) {
        return user != null && Objects.equals(user.getId(), id);
    }
}
