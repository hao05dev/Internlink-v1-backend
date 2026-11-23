package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.organization.TermMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaTermMilestoneRepository extends JpaRepository<TermMilestone, UUID> {
    List<TermMilestone> findByTermIdOrderBySortOrderAscStartAtAscEndAtAsc(UUID termId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TermMilestone m WHERE m.term.id = :termId")
    void deleteByTermId(@Param("termId") UUID termId);
}
