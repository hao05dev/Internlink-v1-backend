package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.evaluation.FinalResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;



@Repository
public interface JpaFinalResultRepository extends JpaRepository<FinalResult, UUID> {
    Optional<FinalResult> findByPlacementId(UUID placementId);

    List<FinalResult> findByPlacementIdIn(Collection<UUID> placementIds);
}
