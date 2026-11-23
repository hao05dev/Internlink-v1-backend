package com.internlink.core.application.placement;

import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.domain.placement.InternshipDurationRule;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.internlink.core.presentation.placement.dto.request.AcceptanceFormDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AcceptanceFormMapper {
    private final JpaInternshipPlacementRepository placements;
    private final JpaStudentRosterRepository rosters;

    public StudentFoundResponse toResponse(StudentFoundApplication a) {
        var term = a.getTerm();
        var student = a.getStudent();
        var roster = rosters.findByTermIdAndClaimedUserId(term.getId(), student.getId()).orElse(null);
        var department = roster != null ? DepartmentScope.of(roster) : DepartmentScope.ofStudent(student);
        Integer credits = roster != null ? roster.getInternshipCredits() : null;
        return new StudentFoundResponse(a.getId(), term.getId(), student.getId(), a.getHostName(),
            a.getHostAddress(), a.getContactName(), a.getContactEmail(), a.getWorkDescription(),
            a.getStartDate(), a.getEndDate(),
            a.getAcceptanceDocument() != null ? a.getAcceptanceDocument().getId() : null,
            a.getStatus(), a.getReviewNote(), placementId(a),
            a.getSource(), a.getAgreement() != null ? a.getAgreement().getId() : null,
            student.getFullName(), roster != null ? roster.getStudentCode() : null, student.getEmail(),
            term.getTermName(), department != null ? department.getId() : null,
            department != null ? department.getName() : null,
            credits, InternshipDurationRule.requiredWeeks(credits),
            a.getUpdatedAt(), a.getReceivedAt(), a.getReviewedAt(), a.getPublishedAt(), a.getDocumentsCollectedAt(),
            a.getSourceJob() != null ? a.getSourceJob().getId() : null,
            a.getSourceJob() != null ? a.getSourceJob().getTitle() : null,
            a.getSourceJob() != null ? a.getSourceJob().getCompany().getCompanyName() : null,
            new AcceptanceFormDetails(a.getHostProvince(), a.getHostPhone(), a.getContactPhone(),
                a.getSessionsPerWeek(), a.getHasWorkspace(), a.getHasComputer(),
                a.getWeeklyPlan() != null ? List.copyOf(a.getWeeklyPlan()) : List.of(), a.getOtherSupport()),
            roster != null ? roster.getPhoneNumber() : null, roster != null ? roster.getClassCode() : null,
            roster != null ? roster.getInternshipCourseCode() : null,
            roster != null && roster.getProgram() != null ? roster.getProgram().getName() : null,
            a.getSubmittedAt(), a.isSubmittedLate());
    }

    private UUID placementId(StudentFoundApplication a) {
        Optional<InternshipPlacement> placement = a.getAgreement() != null
            ? placements.findByAgreementId(a.getAgreement().getId())
            : placements.findByStudentFoundApplicationId(a.getId());
        return placement.map(InternshipPlacement::getId).orElse(null);
    }
}
