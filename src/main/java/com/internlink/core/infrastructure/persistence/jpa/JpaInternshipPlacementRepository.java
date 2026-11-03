package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.shared.enums.PlacementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaInternshipPlacementRepository extends JpaRepository<InternshipPlacement, UUID> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InternshipPlacement p where p.id = :id")
    Optional<InternshipPlacement> findLockedById(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    Optional<InternshipPlacement> findById(UUID id);

    Optional<InternshipPlacement> findByAgreementId(UUID agreementId);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    List<InternshipPlacement> findByStudentId(UUID studentId);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    List<InternshipPlacement> findByCompanyId(UUID companyId);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    List<InternshipPlacement> findByMentorId(UUID mentorId);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    List<InternshipPlacement> findByLecturerId(UUID lecturerId);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    List<InternshipPlacement> findByTermId(UUID termId);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    Page<InternshipPlacement> findByTermId(UUID termId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    List<InternshipPlacement> findByTermDepartmentId(UUID departmentId);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    List<InternshipPlacement> findByTermIdAndStudentId(UUID termId, UUID studentId);

    @Query("SELECT p FROM InternshipPlacement p WHERE p.student.id IN :studentIds AND p.term.id IN :termIds")
    List<InternshipPlacement> findByStudentIdsAndTermIds(@Param("studentIds") java.util.Collection<UUID> studentIds,
                                                         @Param("termIds") java.util.Collection<UUID> termIds);

    Optional<InternshipPlacement> findByStudentFoundApplicationId(UUID studentFoundApplicationId);

    @EntityGraph(attributePaths = {"student", "company", "mentor", "lecturer", "term", "assessmentScheme"})
    List<InternshipPlacement> findByTermIdAndStatus(UUID termId, PlacementStatus status);

    /**
     * Đếm số placement trong kỳ chưa kết thúc (không phải COMPLETED/TERMINATED/TRANSFERRED).
     * Dùng để kiểm tra trước khi chuyển kỳ sang CLOSED.
     */
    @Query("""
        SELECT COUNT(p) FROM InternshipPlacement p
        WHERE p.term.id = :termId
          AND p.status NOT IN ('COMPLETED', 'TERMINATED', 'TRANSFERRED')
        """)
    long countUnfinishedPlacementsByTerm(@Param("termId") UUID termId);

    /**
     * Đếm số placement đã hoàn thành thành công trong kỳ — dùng cho báo cáo.
     */
    long countByTermIdAndStatus(UUID termId, PlacementStatus status);
}
