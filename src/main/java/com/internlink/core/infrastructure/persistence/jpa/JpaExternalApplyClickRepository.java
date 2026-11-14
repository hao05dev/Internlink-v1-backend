package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.recruitment.ExternalApplyClick;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaExternalApplyClickRepository extends JpaRepository<ExternalApplyClick, UUID> {

    // Race-safe: bấm nhiều lần/nhiều tab chỉ tăng bộ đếm, không vi phạm unique
    @Modifying
    @Query(value = "INSERT INTO external_apply_clicks (student_id, job_id) VALUES (:studentId, :jobId) "
        + "ON CONFLICT (student_id, job_id) DO UPDATE SET click_count = external_apply_clicks.click_count + 1, "
        + "last_clicked_at = NOW(), updated_at = NOW()", nativeQuery = true)
    int recordClick(@Param("studentId") UUID studentId, @Param("jobId") UUID jobId);

    long countByJobId(UUID jobId);

    @Query("SELECT c FROM ExternalApplyClick c JOIN FETCH c.job j JOIN FETCH j.company "
        + "WHERE c.student.id = :studentId ORDER BY c.lastClickedAt DESC")
    List<ExternalApplyClick> findByStudentIdWithJob(@Param("studentId") UUID studentId);

    @Query("SELECT COALESCE(SUM(c.clickCount), 0) FROM ExternalApplyClick c WHERE c.job.id = :jobId")
    long sumClicksByJobId(@Param("jobId") UUID jobId);
}
