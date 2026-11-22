package com.internlink.core.application.evaluation.impl;

import com.internlink.core.application.evaluation.AssessmentRules;
import com.internlink.core.application.placement.dossier.DossierInspector;
import com.internlink.core.domain.evaluation.AssessmentComponentScore;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaAssessmentComponentScoreRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaWeeklyLogbookRepository;
import com.internlink.core.shared.enums.LogbookStatus;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FinalizationReadinessValidator {

    private final JpaAssessmentComponentScoreRepository componentScoreRepository;
    private final JpaWeeklyLogbookRepository logbookRepository;
    private final JpaInternshipReportRepository reportRepository;
    private final DossierInspector dossierInspector;

    /** Hồ sơ nộp Khoa phải đủ và được GVHD duyệt hết trước khi tổng hợp điểm. */
    public void requireDossierComplete(InternshipPlacement placement) {
        dossierInspector.requireComplete(placement);
    }

    public Map<String, AssessmentComponentScore> requireReadyForFinalization(InternshipPlacement placement) {
        if (placement.getStatus() != PlacementStatus.ACTIVE)
            throw new BadRequestException("Chỉ tổng hợp điểm khi lần thực tập đang ACTIVE");
        var scheme = placement.getAssessmentScheme();
        if (scheme == null || !List.of("APPROVED", "RETIRED").contains(scheme.getStatus()))
            throw new BadRequestException("Chưa gán phương án đánh giá được duyệt cho học phần");
        AssessmentRules.validate(scheme.getComponents());
        var logbooks = logbookRepository.findByPlacementIdOrderByWeekNumberAsc(placement.getId());
        LogbookStatus requiredStatus = "STUDENT_FOUND".equals(placement.getSource())
            ? LogbookStatus.APPROVED_BY_LECTURER : LogbookStatus.APPROVED_BY_MENTOR;
        for (int week = 1; week <= scheme.getRequiredLogbookWeeks(); week++) {
            int requiredWeek = week;
            if (logbooks.stream().noneMatch(l -> l.getWeekNumber() == requiredWeek && l.getStatus() == requiredStatus))
                throw new BadRequestException("Thiếu nhật ký tuần " + week + " đã được duyệt");
        }
        var reports = reportRepository.findByPlacementId(placement.getId());
        dossierInspector.requireComplete(placement);
        if (Boolean.TRUE.equals(scheme.getRequireMidtermReport())
            && reports.stream().noneMatch(r -> "MIDTERM".equals(r.getReportType())
                && "APPROVED".equals(r.getStatus()) && "ACTIVE".equals(r.getDocument().getStatus())))
            throw new BadRequestException("Thiếu báo cáo giữa kỳ được duyệt");
        if (Boolean.TRUE.equals(scheme.getRequireFinalReport())
            && reports.stream().noneMatch(r -> "FINAL".equals(r.getReportType())
                && "APPROVED".equals(r.getStatus()) && "ACTIVE".equals(r.getDocument().getStatus())))
            throw new BadRequestException("Thiếu báo cáo cuối kỳ được duyệt");
        Map<String, AssessmentComponentScore> scores = new HashMap<>();
        for (AssessmentComponentScore item : componentScoreRepository.findByPlacementId(placement.getId())) {
            scores.put(item.getComponentCode(), item);
        }
        for (Map<String, Object> component : scheme.getComponents()) {
            String code = String.valueOf(component.get("code"));
            AssessmentComponentScore item = scores.get(code);
            if (item == null || !"VERIFIED".equals(item.getStatus())
                || (item.getEvidenceDocument() != null && !"ACTIVE".equals(item.getEvidenceDocument().getStatus())))
                throw new BadRequestException("Thiếu điểm thành phần đã xác minh: " + code);
        }
        return scores;
    }
}
