package com.internlink.core.application.recruitment.impl;

import com.internlink.core.application.recruitment.JobPositionAccessPolicy;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.persistence.jpa.JpaExternalApplyClickRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobPositionRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.presentation.recruitment.dto.response.ExternalApplyJobOption;
import com.internlink.core.presentation.recruitment.dto.response.ExternalApplyStatsResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Ghi nhận lượt SV chuyển sang kênh ứng tuyển của DN và thống kê cho Trường/DN. */
@Service
@RequiredArgsConstructor
public class ExternalApplyService {
    private final JpaJobPositionRepository jobs;
    private final JpaExternalApplyClickRepository clicks;
    private final JpaStudentFoundApplicationRepository acceptanceForms;
    private final JobPositionAccessPolicy access;

    @Transactional
    public void recordClick(UUID jobId) {
        var student = access.currentActor();
        ResourceAuthorization.require(student.getRole() == UserRole.STUDENT);
        JobPosition job = requireExternal(jobId);
        if (job.getStatus() != JobStatus.APPROVED && job.getStatus() != JobStatus.CLOSED)
            throw new BadRequestException("Tin tuyển dụng chưa được công bố");
        clicks.recordClick(student.getId(), job.getId());
    }

    /** Tin EXTERNAL SV đã mở kênh của DN, mới nhất trước – để chọn tin nguồn cho Phiếu tiếp nhận. */
    @Transactional(readOnly = true)
    public List<ExternalApplyJobOption> myJobs() {
        var student = access.currentActor();
        ResourceAuthorization.require(student.getRole() == UserRole.STUDENT);
        return clicks.findByStudentIdWithJob(student.getId()).stream()
                .filter(c -> c.getJob().isExternalApply())
                .map(c -> {
                    JobPosition job = c.getJob();
                    String address = job.getLocation() != null && !job.getLocation().isBlank()
                            ? job.getLocation() : job.getCompany().getOfficeAddress();
                    return new ExternalApplyJobOption(job.getId(), job.getTitle(),
                            job.getCompany().getCompanyName(), address, job.getExternalApplyEmail(),
                            c.getLastClickedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ExternalApplyStatsResponse stats(UUID jobId) {
        var actor = access.currentActor();
        JobPosition job = requireExternal(jobId);
        ResourceAuthorization.require(ResourceAuthorization.canManageJobs(actor, job.getCompany().getId())
                || actor.getRole() == UserRole.FACULTY_ADMIN);
        return new ExternalApplyStatsResponse(job.getId(), clicks.countByJobId(jobId),
                clicks.sumClicksByJobId(jobId), acceptanceForms.countBySourceJobId(jobId));
    }

    private JobPosition requireExternal(UUID jobId) {
        JobPosition job = jobs.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", jobId));
        if (!job.isExternalApply())
            throw new BadRequestException("Tin này nhận hồ sơ trong hệ thống, không có kênh ứng tuyển ngoài");
        return job;
    }
}
