package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.recruitment.SavedJob;
import com.internlink.core.shared.enums.JobStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaSavedJobRepository extends JpaRepository<SavedJob, UUID> {

    @EntityGraph(attributePaths = {"job", "job.company"})
    List<SavedJob> findByStudentIdAndJobStatusOrderByCreatedAtDesc(UUID studentId, JobStatus status);

    @Query("SELECT s.job.id FROM SavedJob s WHERE s.student.id = :studentId AND s.job.status = :status")
    List<UUID> findJobIdsByStudentIdAndJobStatus(@Param("studentId") UUID studentId, @Param("status") JobStatus status);

    boolean existsByStudentIdAndJobId(UUID studentId, UUID jobId);

    // Idempotent and race-safe: a double click or two tabs never raise a unique violation
    @Modifying
    @Query(value = "INSERT INTO saved_jobs (student_id, job_id) VALUES (:studentId, :jobId) "
        + "ON CONFLICT (student_id, job_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("studentId") UUID studentId, @Param("jobId") UUID jobId);

    @Modifying
    @Query("DELETE FROM SavedJob s WHERE s.student.id = :studentId AND s.job.id = :jobId")
    int deleteByStudentIdAndJobId(@Param("studentId") UUID studentId, @Param("jobId") UUID jobId);
}
