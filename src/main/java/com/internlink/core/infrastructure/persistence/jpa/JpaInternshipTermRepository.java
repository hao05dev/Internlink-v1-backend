package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.shared.enums.TermStatus;
import com.internlink.core.domain.organization.InternshipTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;



@Repository
public interface JpaInternshipTermRepository extends JpaRepository<InternshipTerm, UUID> {
    Optional<InternshipTerm> findByCode(String code);
    List<InternshipTerm> findAllByOrderByStartDateDesc();
    List<InternshipTerm> findByStatus(TermStatus status);
    boolean existsByCode(String code);
}
