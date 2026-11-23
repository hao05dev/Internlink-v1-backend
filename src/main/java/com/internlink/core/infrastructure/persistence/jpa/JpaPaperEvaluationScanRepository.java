package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.evaluation.PaperEvaluationScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaPaperEvaluationScanRepository extends JpaRepository<PaperEvaluationScan, UUID> {
    Optional<PaperEvaluationScan> findFirstByPlacementIdOrderByCreatedAtDesc(UUID placementId);

    List<PaperEvaluationScan> findByConfirmedAtIsNotNull();
}
