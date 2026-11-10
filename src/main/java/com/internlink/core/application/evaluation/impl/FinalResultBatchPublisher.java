package com.internlink.core.application.evaluation.impl;

import com.internlink.core.application.organization.TermDepartmentScope;
import com.internlink.core.application.evaluation.FinalResultService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.FinalResult;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaFinalResultRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.evaluation.dto.request.BatchPublishResultRequest;
import com.internlink.core.presentation.evaluation.dto.response.BatchPublishResultResponse;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.ResultStatus;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class FinalResultBatchPublisher {

    private final JpaInternshipTermRepository termRepository;
    private final JpaUserRepository userRepository;
    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaFinalResultRepository finalResultRepository;
    private final TermDepartmentScope termScope;

    public BatchPublishResultResponse publishBatchResults(
        UUID termId,
        BatchPublishResultRequest request,
        UUID facultyAdminId,
        FinalResultService finalResultService
    ) {
        InternshipTerm term = termRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);

        User admin = userRepository.findById(facultyAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", facultyAdminId));

        // GVHD công bố cho SV mình hướng dẫn; Quản lý Khoa công bố cho SV thuộc khoa.
        List<InternshipPlacement> targetPlacements = termScope.gradablePlacements(termId, admin);
        if (request != null && request.getPlacementIds() != null && !request.getPlacementIds().isEmpty()) {
            Set<UUID> targetIds = new HashSet<>(request.getPlacementIds());
            targetPlacements = targetPlacements.stream().filter(p -> targetIds.contains(p.getId())).toList();
        }

        int publishedCount = 0;
        int skippedCount = 0;
        List<String> messages = new ArrayList<>();

        for (InternshipPlacement p : targetPlacements) {
            FinalResult fr = finalResultRepository.findByPlacementId(p.getId()).orElse(null);
            if (fr == null) {
                try {
                    finalResultService.quickFinalizePlacement(p.getId(), facultyAdminId);
                    fr = finalResultRepository.findByPlacementId(p.getId()).orElse(null);
                } catch (Exception ex) {
                    skippedCount++;
                    messages.add("SV " + p.getStudent().getFullName() + ": Chưa đủ điều kiện (" + ex.getMessage() + ")");
                    continue;
                }
            }

            if (fr != null) {
                if (fr.getPublishedAt() != null) {
                    skippedCount++;
                    continue;
                }
                fr.setPublishedAt(OffsetDateTime.now());
                fr.setDecidedBy(admin);
                if (fr.getResultStatus() == ResultStatus.PASSED) {
                    p.setStatus(PlacementStatus.COMPLETED);
                    placementRepository.save(p);
                }
                finalResultRepository.save(fr);
                publishedCount++;
            } else {
                skippedCount++;
            }
        }

        return BatchPublishResultResponse.builder()
            .publishedCount(publishedCount)
            .skippedCount(skippedCount)
            .messages(messages)
            .build();
    }
}
