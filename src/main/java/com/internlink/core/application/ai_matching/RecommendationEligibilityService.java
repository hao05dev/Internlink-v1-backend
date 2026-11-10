package com.internlink.core.application.ai_matching;

import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.TermStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecommendationEligibilityService {

    private final JpaStudentRosterRepository rosterRepository;
    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaJobApplicationRepository applicationRepository;
    private final JpaPlacementOfferRepository offerRepository;
    private final JpaInternshipTermRepository termRepository;

    @Transactional(readOnly = true)
    public EligibilityResult checkStudentTermEligibility(UUID studentId, UUID termId) {
        InternshipTerm term = termRepository.findById(termId).orElse(null);
        if (term == null) {
            return EligibilityResult.ineligible("TERM_NOT_FOUND", "Học kỳ không tồn tại");
        }
        if (term.getStatus() == TermStatus.CLOSED) {
            return EligibilityResult.ineligible("TERM_CLOSED", "Học kỳ thực tập đã đóng");
        }

        StudentRoster roster = rosterRepository.findByTermIdAndClaimedUserId(termId, studentId).orElse(null);
        if (roster == null) {
            return EligibilityResult.ineligible("NOT_IN_ROSTER", "Sinh viên không có trong danh sách thực tập của kỳ này");
        }

        var placements = placementRepository.findByTermIdAndStudentId(termId, studentId);
        boolean hasActivePlacement = placements.stream()
            .anyMatch(p -> p.getStatus() != null && !List.of("TERMINATED", "TRANSFERRED").contains(p.getStatus().name()));
        if (hasActivePlacement) {
            return EligibilityResult.ineligible("ALREADY_PLACED", "Sinh viên đã có đơn vị tiếp nhận thực tập trong kỳ này");
        }

        return EligibilityResult.eligible();
    }

    @Transactional(readOnly = true)
    public EligibilityResult evaluateJobEligibility(UUID studentId, JobPosition job, OffsetDateTime now) {
        if (job.getStatus() != JobStatus.APPROVED) {
            return EligibilityResult.ineligible("JOB_NOT_APPROVED", "Vị trí tuyển dụng chưa được phê duyệt");
        }
        if (job.getClosedEarlyAt() != null && job.getClosedEarlyAt().isBefore(now)) {
            return EligibilityResult.ineligible("JOB_CLOSED_EARLY", "Vị trí đã đóng nhận hồ sơ sớm");
        }
        if (job.getApplicationDeadline() != null && job.getApplicationDeadline().isBefore(now)) {
            return EligibilityResult.ineligible("APPLICATION_DEADLINE_PASSED", "Vị trí đã quá hạn nộp hồ sơ");
        }

        long activeCount = offerRepository.countReservedPlaces(job.getId(), now);
        if (job.getVacancies() != null && activeCount >= job.getVacancies()) {
            return EligibilityResult.ineligible("CAPACITY_FULL", "Vị trí đã đủ chỉ tiêu tuyển dụng");
        }

        // Tin tuyển dụng là cấp Trường: SV mọi khoa đều được ứng tuyển.

        if (applicationRepository.existsByJobIdAndStudentId(job.getId(), studentId)) {
            return EligibilityResult.ineligible("ALREADY_APPLIED", "Sinh viên đã nộp hồ sơ ứng tuyển vị trí này");
        }

        return EligibilityResult.eligible();
    }
}
