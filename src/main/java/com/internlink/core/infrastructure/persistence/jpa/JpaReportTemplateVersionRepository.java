package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.shared.enums.ReportTemplateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaReportTemplateVersionRepository extends JpaRepository<ReportTemplateVersion, UUID> {
    List<ReportTemplateVersion> findByTermIdAndIsActiveTrue(UUID termId);
    List<ReportTemplateVersion> findByTermIdAndDepartmentIdAndIsActiveTrue(UUID termId, UUID departmentId);
    List<ReportTemplateVersion> findByTermIdOrderByCreatedAtDesc(UUID termId);
    List<ReportTemplateVersion> findByTermIdAndDepartmentIdOrderByCreatedAtDesc(UUID termId, UUID departmentId);
    List<ReportTemplateVersion> findByTermIdAndReportTypeOrderByVersionNumberDesc(UUID termId, ReportTemplateType reportType);
    List<ReportTemplateVersion> findByReportTypeAndIsActiveTrue(ReportTemplateType reportType);
    List<ReportTemplateVersion> findByTermIdAndReportTypeAndIsActiveTrue(UUID termId, ReportTemplateType reportType);

    /** Mẫu của một khoa cộng với mẫu chung cấp trường (department_id NULL) trong kỳ. */
    @Query("""
        SELECT t FROM ReportTemplateVersion t
        WHERE t.term.id = :termId
          AND (t.department IS NULL OR t.department.id = :departmentId)
          AND (:activeOnly = false OR t.isActive = true)
        ORDER BY t.createdAt DESC
        """)
    List<ReportTemplateVersion> findVisibleToDepartment(@Param("termId") UUID termId,
                                                        @Param("departmentId") UUID departmentId,
                                                        @Param("activeOnly") boolean activeOnly);

    /** Đánh số phiên bản riêng cho từng khoa (departmentId null = mẫu chung cấp trường). */
    Optional<ReportTemplateVersion> findFirstByTermIdAndDepartmentIdAndReportTypeAndProgramCodeOrderByVersionNumberDesc(
            UUID termId, UUID departmentId, ReportTemplateType reportType, String programCode);

    Optional<ReportTemplateVersion> findFirstByTermIdAndReportTypeAndProgramCodeAndIsActiveTrueOrderByVersionNumberDesc(
            UUID termId, ReportTemplateType reportType, String programCode);

    Optional<ReportTemplateVersion> findFirstByTermIdAndReportTypeAndProgramCodeOrderByVersionNumberDesc(
            UUID termId, ReportTemplateType reportType, String programCode);

    Optional<ReportTemplateVersion> findFirstByTermIdAndReportTypeAndIsActiveTrueOrderByVersionNumberDesc(
            UUID termId, ReportTemplateType reportType);

    Optional<ReportTemplateVersion> findFirstByTermIdAndReportTypeOrderByVersionNumberDesc(
            UUID termId, ReportTemplateType reportType);

    Optional<ReportTemplateVersion> findFirstByTermIdAndDepartmentIdAndReportTypeOrderByVersionNumberDesc(
            UUID termId, UUID departmentId, ReportTemplateType reportType);
}
