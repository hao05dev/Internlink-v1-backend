package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.placement.StudentFoundApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaStudentFoundApplicationRepository extends JpaRepository<StudentFoundApplication, UUID> {
    List<StudentFoundApplication> findByTermId(UUID termId);
    List<StudentFoundApplication> findByTermIdAndStudentDepartmentId(UUID termId, UUID departmentId);
    List<StudentFoundApplication> findByTermIdAndStatusAndStudentDepartmentId(UUID termId, String status, UUID departmentId);
    List<StudentFoundApplication> findByStudentId(UUID studentId);
    Optional<StudentFoundApplication> findByTermIdAndStudentId(UUID termId, UUID studentId);
    Optional<StudentFoundApplication> findByAgreementId(UUID agreementId);
    List<StudentFoundApplication> findByTermIdAndStatus(UUID termId, String status);
    List<StudentFoundApplication> findByStatusInOrderByUpdatedAtDesc(Collection<String> statuses);
}
