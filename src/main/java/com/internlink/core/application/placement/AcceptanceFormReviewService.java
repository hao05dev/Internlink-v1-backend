package com.internlink.core.application.placement;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.*;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Khoa: duyệt Phiếu tiếp nhận và đơn vị thực tập (chỉ phiếu VP Trường đã nhận bản giấy),
 * phân công GVHD, công bố danh sách SV thực tập của kỳ.
 */
@Service
@RequiredArgsConstructor
public class AcceptanceFormReviewService {
    private static final List<String> DECISIONS = List.of(
        AcceptanceFormStatus.APPROVED, AcceptanceFormStatus.REJECTED, AcceptanceFormStatus.REVISION_REQUIRED);

    private final JpaStudentFoundApplicationRepository applications;
    private final JpaInternshipPlacementRepository placements;
    private final JpaStudentRosterRepository rosters;
    private final JpaInternshipTermRepository terms;
    private final JpaUserRepository users;
    private final PlacementEligibilityService placementEligibility;
    private final InternshipPlacementService placementService;
    private final AcceptanceFormLookup lookup;
    private final AcceptanceFormMapper mapper;
    private final AcceptanceFormNotifier notifier;

    @Transactional
    public StudentFoundResponse review(UUID id, String decision, String note, UUID lecturerId) {
        var app = lookup.get(id);
        TermGuard.requireNotClosed(app.getTerm());
        var reviewer = lookup.actor();
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(reviewer, app.getTerm().getDepartment().getId()));
        AcceptanceFormLookup.requireStatus(app, AcceptanceFormStatus.RECEIVED,
            "Khoa chỉ duyệt phiếu đã được Văn phòng Trường xác nhận bản giấy");
        if (!DECISIONS.contains(decision)) throw new BadRequestException("Quyết định không hợp lệ");
        String reason = AcceptanceFormStatus.APPROVED.equals(decision) ? trimOrNull(note)
            : AcceptanceFormLookup.requireNote(note, "Cần nêu lý do khi từ chối hoặc yêu cầu sửa");

        if (AcceptanceFormStatus.APPROVED.equals(decision)) {
            if (app.getAcceptanceDocument() == null || !"ACTIVE".equals(app.getAcceptanceDocument().getStatus()))
                throw new BadRequestException("Thiếu bản scan Phiếu tiếp nhận hợp lệ");
            User lecturer = app.isSystemOffer()
                ? activateSystemPlacement(app, lecturerId)
                : createSelfFoundPlacement(app, lecturerId);
            notifier.approved(app, lecturer);
        } else if (AcceptanceFormStatus.REVISION_REQUIRED.equals(decision)) {
            notifier.returned(app, "Khoa", reason);
        } else {
            notifier.rejected(app, reason);
        }
        app.setStatus(decision);
        app.setReviewNote(reason);
        app.setReviewedBy(reviewer);
        app.setReviewedAt(OffsetDateTime.now());
        return mapper.toResponse(applications.save(app));
    }

    /** Công bố danh sách SV thực tập của kỳ: mọi phiếu đã duyệt chuyển PUBLISHED, SV đến VP Trường nhận giấy tờ. */
    @Transactional
    public int publish(UUID termId) {
        var term = terms.findById(termId).orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);
        var publisher = lookup.actor();
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(publisher, term.getDepartment().getId()));
        var approved = applications.findByTermIdAndStatus(termId, AcceptanceFormStatus.APPROVED);
        if (approved.isEmpty()) throw new BadRequestException("Không có phiếu đã duyệt nào chờ công bố");
        var now = OffsetDateTime.now();
        for (var app : approved) {
            app.setStatus(AcceptanceFormStatus.PUBLISHED);
            app.setPublishedAt(now);
            app.setPublishedBy(publisher);
            notifier.published(app);
        }
        applications.saveAll(approved);
        notifier.listPublished(term, approved.size());
        return approved.size();
    }

    @Transactional(readOnly = true)
    public List<StudentFoundResponse> byTerm(UUID termId) {
        var term = terms.findById(termId).orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        var actor = lookup.actor();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(actor)
            || ResourceAuthorization.managesDepartment(actor, term.getDepartment().getId()));
        return applications.findByTermId(termId).stream().map(mapper::toResponse).toList();
    }

    /** Nhắc SV trong danh sách học phần chưa nộp Phiếu tiếp nhận. */
    @Transactional
    public int remindStudentsWithoutForm(UUID termId) {
        var term = terms.findById(termId).orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(lookup.actor(), term.getDepartment().getId()));
        int count = 0;
        for (StudentRoster roster : rosters.findByTermId(termId)) {
            if (roster.getClaimedUser() == null) continue;
            UUID studentId = roster.getClaimedUser().getId();
            if (!placements.findByTermIdAndStudentId(termId, studentId).isEmpty()) continue;
            boolean inProgress = applications.findByTermIdAndStudentId(termId, studentId)
                .map(a -> AcceptanceFormStatus.IN_PROGRESS.contains(a.getStatus())).orElse(false);
            if (inProgress) continue;
            notifier.remindMissingForm(studentId, term);
            count++;
        }
        return count;
    }

    /** Trả về null vì GVHD đã được thông báo khi kích hoạt từ thỏa thuận 3 bên. */
    private User activateSystemPlacement(StudentFoundApplication app, UUID lecturerId) {
        placementService.activatePlacementFromAgreement(app.getAgreement().getId(), lecturerId);
        return null;
    }

    private User createSelfFoundPlacement(StudentFoundApplication app, UUID lecturerId) {
        var roster = placementEligibility.requireEligibleRoster(app.getStudent().getId(), app.getTerm().getId());
        placementEligibility.requireNoLivePlacement(app.getStudent().getId(), app.getTerm().getId());
        var chosen = lecturerId == null ? null
            : users.findById(lecturerId).orElseThrow(() -> new ResourceNotFoundException("User", "id", lecturerId));
        var lecturer = placementEligibility.resolveLecturer(chosen, roster);
        placementEligibility.requireActiveLecturer(lecturer, app.getTerm().getDepartment().getId());
        placementEligibility.syncRosterLecturer(app.getStudent().getId(), app.getTerm().getId(), lecturer);
        placements.save(InternshipPlacement.builder().studentFoundApplication(app).source("STUDENT_FOUND")
            .student(app.getStudent()).lecturer(lecturer).term(app.getTerm())
            .startDate(app.getStartDate()).endDate(app.getEndDate())
            .workSchedule(Map.of()).totalHoursWorked(BigDecimal.ZERO).status(PlacementStatus.PREPARING).build());
        return lecturer;
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
