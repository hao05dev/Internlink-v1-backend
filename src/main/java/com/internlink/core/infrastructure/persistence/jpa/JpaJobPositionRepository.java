package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.WorkFormat;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaJobPositionRepository extends JpaRepository<JobPosition, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM JobPosition j WHERE j.id = :id")
    Optional<JobPosition> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"company", "term"})
    List<JobPosition> findByCompanyId(UUID companyId);

    @EntityGraph(attributePaths = {"company", "term"})
    List<JobPosition> findByTermId(UUID termId);

    @EntityGraph(attributePaths = {"company", "term"})
    List<JobPosition> findByTermIdAndStatus(UUID termId, JobStatus status);


    boolean existsByCompanyId(UUID companyId);

    @EntityGraph(attributePaths = {"company", "term", "createdBy"})
    @Query("""
        SELECT j FROM JobPosition j
        WHERE (:termId IS NULL OR j.term.id = :termId)
          AND (:companyId IS NULL OR j.company.id = :companyId)
          AND (:status IS NULL OR j.status = :status)
          AND (:workFormat IS NULL OR j.workFormat = :workFormat)
          AND (
              :location IS NULL OR :location = ''
              OR LOWER(CASE WHEN j.useCompanyLocation = true THEN j.company.officeAddress ELSE j.location END) LIKE LOWER(CONCAT('%', :location, '%'))
          )
          AND (
              :keyword IS NULL OR :keyword = ''
              OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(j.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(CASE WHEN j.useCompanyLocation = true THEN j.company.officeAddress ELSE j.location END) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(j.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY j.createdAt DESC
    """)
    List<JobPosition> searchJobs(
        @Param("keyword") String keyword,
        @Param("termId") UUID termId,
        @Param("companyId") UUID companyId,
        @Param("status") JobStatus status,
        @Param("location") String location,
        @Param("workFormat") WorkFormat workFormat
    );

    default List<JobPosition> searchJobs(String keyword, UUID termId, UUID companyId, JobStatus status) {
        return searchJobs(keyword, termId, companyId, status, null, null);
    }

    @EntityGraph(attributePaths = {"company", "term", "createdBy"})
    @Query("""
        SELECT j FROM JobPosition j
        WHERE j.status = com.internlink.core.shared.enums.JobStatus.APPROVED
          AND j.closedEarlyAt IS NULL
          AND (:termId IS NULL OR j.term.id = :termId)
          AND (:companyId IS NULL OR j.company.id = :companyId)
          AND (:workFormat IS NULL OR j.workFormat = :workFormat)
          AND (
              :location IS NULL OR :location = ''
              OR LOWER(CASE WHEN j.useCompanyLocation = true THEN j.company.officeAddress ELSE j.location END) LIKE LOWER(CONCAT('%', :location, '%'))
          )
          AND (
              :keyword IS NULL OR :keyword = ''
              OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(j.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(CASE WHEN j.useCompanyLocation = true THEN j.company.officeAddress ELSE j.location END) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(j.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY 
          CASE WHEN (j.applicationDeadline IS NULL OR j.applicationDeadline >= :now) THEN 0 ELSE 1 END ASC,
          j.createdAt DESC
    """)
    List<JobPosition> searchActivePublicJobs(
        @Param("keyword") String keyword,
        @Param("termId") UUID termId,
        @Param("companyId") UUID companyId,
        @Param("location") String location,
        @Param("workFormat") WorkFormat workFormat,
        @Param("now") OffsetDateTime now
    );

    @EntityGraph(attributePaths = {"company", "term", "createdBy"})
    @Query("""
        SELECT j FROM JobPosition j
        WHERE (:termId IS NULL OR j.term.id = :termId)
          AND (:companyId IS NULL OR j.company.id = :companyId)
          AND (:status IS NULL OR j.status = :status)
          AND (:workFormat IS NULL OR j.workFormat = :workFormat)
          AND (
              :location IS NULL OR :location = ''
              OR LOWER(CASE WHEN j.useCompanyLocation = true THEN j.company.officeAddress ELSE j.location END) LIKE LOWER(CONCAT('%', :location, '%'))
          )
          AND (
              :keyword IS NULL OR :keyword = ''
              OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(j.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(CASE WHEN j.useCompanyLocation = true THEN j.company.officeAddress ELSE j.location END) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(j.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY j.createdAt DESC
    """)
    Page<JobPosition> searchJobsPage(
        @Param("keyword") String keyword,
        @Param("termId") UUID termId,
        @Param("companyId") UUID companyId,
        @Param("status") JobStatus status,
        @Param("location") String location,
        @Param("workFormat") WorkFormat workFormat,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"company", "term", "createdBy"})
    @Query("""
        SELECT j FROM JobPosition j
        WHERE j.status = com.internlink.core.shared.enums.JobStatus.APPROVED
          AND j.closedEarlyAt IS NULL
          AND (:termId IS NULL OR j.term.id = :termId)
          AND (:companyId IS NULL OR j.company.id = :companyId)
          AND (:workFormat IS NULL OR j.workFormat = :workFormat)
          AND (
              :location IS NULL OR :location = ''
              OR LOWER(CASE WHEN j.useCompanyLocation = true THEN j.company.officeAddress ELSE j.location END) LIKE LOWER(CONCAT('%', :location, '%'))
          )
          AND (
              :keyword IS NULL OR :keyword = ''
              OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(j.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(CASE WHEN j.useCompanyLocation = true THEN j.company.officeAddress ELSE j.location END) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(j.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY 
          CASE WHEN (j.applicationDeadline IS NULL OR j.applicationDeadline >= :now) THEN 0 ELSE 1 END ASC,
          j.createdAt DESC
    """)
    Page<JobPosition> searchActivePublicJobsPage(
        @Param("keyword") String keyword,
        @Param("termId") UUID termId,
        @Param("companyId") UUID companyId,
        @Param("location") String location,
        @Param("workFormat") WorkFormat workFormat,
        @Param("now") OffsetDateTime now,
        Pageable pageable
    );
}
