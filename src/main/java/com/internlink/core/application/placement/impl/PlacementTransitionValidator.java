package com.internlink.core.application.placement.impl;

import com.internlink.core.application.placement.AcceptanceFormGate;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaFinalResultRepository;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlacementTransitionValidator {

    private final JpaFinalResultRepository finalResultRepository;
    private final AcceptanceFormGate acceptanceFormGate;

    private static final Map<PlacementStatus, Set<PlacementStatus>> VALID_TRANSITIONS = Map.of(
        // SV bỏ ngang trước khi bắt đầu: phải dừng được, nếu không kỳ sẽ không đóng được.
        PlacementStatus.PREPARING,    Set.of(PlacementStatus.ACTIVE,
                                             PlacementStatus.TERMINATED),
        PlacementStatus.ACTIVE,       Set.of(PlacementStatus.PAUSED,
                                             PlacementStatus.COMPLETED,
                                             PlacementStatus.TERMINATED,
                                             PlacementStatus.TRANSFERRED),
        PlacementStatus.PAUSED,       Set.of(PlacementStatus.ACTIVE,
                                             PlacementStatus.TERMINATED,
                                             PlacementStatus.TRANSFERRED),
        PlacementStatus.COMPLETED,    Set.of(), // trạng thái cuối
        PlacementStatus.TERMINATED,   Set.of(), // trạng thái cuối
        PlacementStatus.TRANSFERRED,  Set.of()  // trạng thái cuối
    );

    public void validateTransition(InternshipPlacement placement, PlacementStatus newStatus) {
        PlacementStatus currentStatus = placement.getStatus();

        // 1. Kiểm tra chuyển trạng thái có hợp lệ theo State Machine không
        Set<PlacementStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new BadRequestException(String.format(
                "Không thể chuyển trạng thái thực tập từ %s sang %s. Các trạng thái hợp lệ: %s",
                currentStatus, newStatus, allowed.isEmpty() ? "không có (trạng thái cuối)" : allowed
            ));
        }

        // 2. Điều kiện tiên quyết theo bước đích
        if (newStatus == PlacementStatus.ACTIVE
            && (placement.getAssessmentScheme() == null
                || !Set.of("APPROVED", "RETIRED").contains(placement.getAssessmentScheme().getStatus()))) {
            throw new BadRequestException("Cần gán phương án đánh giá đã duyệt trước khi bắt đầu thực tập");
        }
        if (newStatus == PlacementStatus.ACTIVE && "STUDENT_FOUND".equals(placement.getSource())
            && (placement.getStudentFoundApplication() == null
                || placement.getStudentFoundApplication().getAcceptanceDocument() == null
                || !"ACTIVE".equals(placement.getStudentFoundApplication().getAcceptanceDocument().getStatus()))) {
            throw new BadRequestException("Thư tiếp nhận của nơi tự tìm không còn hợp lệ");
        }
        if (newStatus == PlacementStatus.ACTIVE) {
            acceptanceFormGate.requirePublished(placement);
        }
        if (newStatus == PlacementStatus.COMPLETED) {
            boolean hasFinalResult = finalResultRepository.findByPlacementId(placement.getId())
                .map(r -> r.getPublishedAt() != null)
                .orElse(false);
            if (!hasFinalResult) {
                throw new BadRequestException(
                    "Phải có kết quả thực tập đã công bố (FinalResult.publishedAt != null) trước khi đánh dấu COMPLETED"
                );
            }
        }
    }
}
