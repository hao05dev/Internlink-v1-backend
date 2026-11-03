package com.internlink.core.application.placement;

import com.internlink.core.domain.placement.InternshipDurationRule;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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
        var department = term.getDepartment();
        var roster = rosters.findByTermIdAndClaimedUserId(term.getId(), student.getId()).orElse(null);
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
            a.getUpdatedAt(), a.getReceivedAt(), a.getReviewedAt(), a.getPublishedAt(), a.getDocumentsCollectedAt());
    }

    private UUID placementId(StudentFoundApplication a) {
        Optional<InternshipPlacement> placement = a.getAgreement() != null
            ? placements.findByAgreementId(a.getAgreement().getId())
            : placements.findByStudentFoundApplicationId(a.getId());
        return placement.map(InternshipPlacement::getId).orElse(null);
    }
}
