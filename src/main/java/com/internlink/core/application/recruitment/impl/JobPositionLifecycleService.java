package com.internlink.core.application.recruitment.impl;

import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.RequirementType;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobPositionLifecycleService {
    private final JpaJobPositionRepository jobPositionRepository;
    private final JpaUserRepository userRepository;
    private final JpaJobSkillRepository jobSkillRepository;
    private final AuditLogService auditLogService;
    private final SecurityGuard securityGuard;
    private final JobPositionMapper jobPositionMapper;
    private final JobPositionNotificationHelper notificationHelper;
    private final com.internlink.core.application.recruitment.JobPositionAccessPolicy access;

    @Transactional
    public JobPositionResponse submitJob(UUID id) {
        JobPosition job =
                jobPositionRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", id));
        TermGuard.requireNotClosed(job.getTerm());
        User actor = access.currentActor();
        ResourceAuthorization.require(ResourceAuthorization.canManageJobs(actor, job.getCompany().getId()));
        if (job.getStatus() != JobStatus.DRAFT && job.getStatus() != JobStatus.REJECTED) {
            throw new BadRequestException("Chỉ có thể gửi duyệt vị trí DRAFT hoặc REJECTED");
        }
        if (jobSkillRepository.findByIdJobId(id).stream()
                .noneMatch(skill -> skill.getRequirementType() == RequirementType.MANDATORY)) {
            throw new BadRequestException(
                    "Vị trí cần ít nhất một kỹ năng bắt buộc trước khi gửi duyệt");
        }
        job.setStatus(JobStatus.PENDING_APPROVAL);
        JobPosition submitted = jobPositionRepository.save(job);
        notificationHelper.notifySubmitted(submitted);
        return jobPositionMapper.toResponse(submitted);
    }

    @Transactional
    public JobPositionResponse closeJobEarly(UUID id) {
        JobPosition job =
                jobPositionRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", id));
        TermGuard.requireNotClosed(job.getTerm());
        User actor = access.currentActor();
        ResourceAuthorization.require(ResourceAuthorization.canManageJobs(actor, job.getCompany().getId()));

        if (job.getStatus() == JobStatus.CLOSED) {
            throw new BadRequestException("Vị trí tuyển dụng này đã đóng trước đó");
        }

        job.setStatus(JobStatus.CLOSED);
        job.setClosedEarlyAt(OffsetDateTime.now());
        JobPosition closed = jobPositionRepository.save(job);

        auditLogService.logAction(
                actor.getId(),
                "CLOSE_JOB_POSITION_EARLY",
                "JobPosition",
                closed.getId(),
                "SUCCESS",
                Map.of("status", JobStatus.CLOSED.name(), "closedEarlyAt", closed.getClosedEarlyAt().toString()),
                null);

        return jobPositionMapper.toResponse(closed);
    }

    @Transactional
    public JobPositionResponse reviewJob(
            UUID id, JobStatus status, String facultyFeedback, UUID approvedByUserId) {
        JobPosition job =
                jobPositionRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", id));
        TermGuard.requireNotClosed(job.getTerm());

        if (status != JobStatus.APPROVED && status != JobStatus.REJECTED) {
            throw new BadRequestException("Kết quả thẩm định chỉ có thể là APPROVED hoặc REJECTED");
        }
        if (status == JobStatus.REJECTED
                && (facultyFeedback == null || facultyFeedback.isBlank())) {
            throw new BadRequestException("Cần nêu lý do từ chối tin tuyển dụng");
        }
        if (job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể thẩm định vị trí đã gửi duyệt");
        }

        User approver =
                userRepository
                        .findById(approvedByUserId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "User", "id", approvedByUserId));
        ResourceAuthorization.require(
                approvedByUserId.equals(securityGuard.currentUser().getId())
                        && ResourceAuthorization.actsForSchool(approver));

        job.setStatus(status);
        job.setFacultyFeedback(facultyFeedback);
        job.setApprovedBy(approver);
        job.setApprovedAt(OffsetDateTime.now());

        JobPosition saved = jobPositionRepository.save(job);
        auditLogService.logAction(
                approvedByUserId,
                "REVIEW_JOB_POSITION",
                "JobPosition",
                saved.getId(),
                "SUCCESS",
                Map.of("status", status.name()),
                null);

        // Gửi thông báo
        notificationHelper.notifyReviewResult(job, status, facultyFeedback);

        return jobPositionMapper.toResponse(saved);
    }
}
