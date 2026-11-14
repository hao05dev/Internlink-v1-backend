package com.internlink.core.application.recruitment.impl;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.recruitment.dto.request.JobPositionRequest;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.JobType;
import com.internlink.core.shared.enums.VerificationStatus;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobPositionWriteService {
    private final JobLocationResolver locations;
    private final JpaJobPositionRepository jobPositionRepository;
    private final JpaCompanyRepository companyRepository;
    private final JpaInternshipTermRepository termRepository;
    private final JpaUserRepository userRepository;
    private final JpaJobSkillRepository jobSkillRepository;
    private final SecurityGuard securityGuard;
    private final JobPositionMapper jobPositionMapper;
    private final JobSkillAssignmentHandler jobSkillAssignmentHandler;
    private final com.internlink.core.application.system.upload.MediaReferencePolicy media;
    private final com.internlink.core.application.recruitment.JobPositionAccessPolicy access;
    private final ExternalApplyPolicy externalApply;

    @Transactional
    public JobPositionResponse createJob(JobPositionRequest request, UUID createdByUserId) {
        Company company =
                companyRepository
                        .findById(request.getCompanyId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Company", "id", request.getCompanyId()));

        if (company.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new BadRequestException(
                    "Doanh nghiệp chưa được xác thực, không thể đăng tin tuyển dụng");
        }

        JobType jobType = request.getJobType() != null ? request.getJobType() : JobType.INTERNSHIP;
        InternshipTerm term = null;
        if (request.getTermId() != null) {
            term = termRepository.findById(request.getTermId())
                    .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", request.getTermId()));
            TermGuard.requireNotClosed(term);
        } else if (jobType == JobType.INTERNSHIP) {
            throw new BadRequestException("Vị trí thực tập (INTERNSHIP) bắt buộc phải gắn với một kỳ thực tập");
        }


        User createdBy =
                userRepository
                        .findById(createdByUserId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("User", "id", createdByUserId));
        ResourceAuthorization.require(
                createdByUserId.equals(securityGuard.currentUser().getId())
                        && ResourceAuthorization.canManageJobs(createdBy, company.getId()));

        media.job(
                company.getId(),
                request.getBannerUrl(),
                request.getAttachmentUrls(),
                List.of(),
                request.getDescription());
        OffsetDateTime deadline = request.getApplicationDeadline() != null
                ? request.getApplicationDeadline()
                : (term != null ? term.getApplicationDeadline() : null);

        boolean isAutoApproved = ResourceAuthorization.actsForSchool(createdBy);

        JobPosition.JobPositionBuilder jobBuilder =
                JobPosition.builder()
                        .company(company)
                        .jobType(jobType)
                        .term(term)
                        .title(request.getTitle().trim())
                        .workFormat(request.getWorkFormat())
                        .location("")
                        .applicationDeadline(deadline)
                        .vacancies(request.getVacancies())
                        .description(request.getDescription().trim())
                        .bannerUrl(request.getBannerUrl())
                        .attachmentUrls(
                                request.getAttachmentUrls() != null
                                        ? request.getAttachmentUrls()
                                        : List.of())
                        .targetProgramCodes(
                                request.getTargetProgramCodes() != null
                                        ? request.getTargetProgramCodes()
                                        : List.of())
                        .targetLearningOutcomes(
                                request.getTargetLearningOutcomes() != null
                                        ? request.getTargetLearningOutcomes()
                                        : List.of())
                        .benefits(request.getBenefits() != null ? request.getBenefits() : List.of())
                        .stipendAmount(request.getStipendAmount())
                        .createdBy(createdBy);

        if (isAutoApproved) {
            jobBuilder.status(JobStatus.APPROVED)
                    .approvedBy(createdBy)
                    .approvedAt(OffsetDateTime.now());
        } else {
            jobBuilder.status(JobStatus.DRAFT);
        }

        JobPosition job = jobBuilder.build();
        locations.apply(job, request);
        externalApply.applyOnCreate(job, request, createdBy);
        JobPosition savedJob = jobPositionRepository.save(job);
        jobSkillAssignmentHandler.saveJobSkills(savedJob, request);

        return jobPositionMapper.toResponse(savedJob);
    }

    @Transactional
    public JobPositionResponse updateJob(UUID id, JobPositionRequest request) {
        JobPosition job =
                jobPositionRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", id));
        TermGuard.requireNotClosed(job.getTerm());
        User actor = access.currentActor();
        ResourceAuthorization.require(ResourceAuthorization.canManageJobs(actor, job.getCompany().getId()));

        if (job.getStatus() == JobStatus.CLOSED) {
            throw new BadRequestException("Không thể chỉnh sửa vị trí tuyển dụng đã đóng");
        }
        boolean isStaff = ResourceAuthorization.actsForSchool(actor);

        if (!isStaff && job.getStatus() != JobStatus.DRAFT && job.getStatus() != JobStatus.REJECTED) {
            throw new BadRequestException(
                    "Chỉ có thể chỉnh sửa vị trí ở trạng thái DRAFT hoặc REJECTED");
        }

        boolean termMismatch = (job.getTerm() == null && request.getTermId() != null)
                || (job.getTerm() != null && !job.getTerm().getId().equals(request.getTermId()));

        if (!job.getCompany().getId().equals(request.getCompanyId())
                || termMismatch) {
            throw new BadRequestException(
                    "Không thể đổi doanh nghiệp hoặc kỳ của vị trí đã tạo");
        }

        media.job(
                job.getCompany().getId(),
                request.getBannerUrl(),
                request.getAttachmentUrls(),
                job.getAttachmentUrls(),
                request.getDescription());
        job.setBannerUrl(request.getBannerUrl());
        job.setAttachmentUrls(
                request.getAttachmentUrls() != null ? request.getAttachmentUrls() : List.of());
        job.setTitle(request.getTitle().trim());
        job.setWorkFormat(request.getWorkFormat());
        locations.apply(job, request);
        if (request.getApplicationDeadline() != null) {
            job.setApplicationDeadline(request.getApplicationDeadline());
        }
        job.setVacancies(request.getVacancies());
        job.setDescription(request.getDescription().trim());
        job.setTargetProgramCodes(
                request.getTargetProgramCodes() != null
                        ? request.getTargetProgramCodes()
                        : List.of());
        job.setTargetLearningOutcomes(
                request.getTargetLearningOutcomes() != null
                        ? request.getTargetLearningOutcomes()
                        : List.of());
        job.setBenefits(request.getBenefits() != null ? request.getBenefits() : List.of());
        job.setStipendAmount(request.getStipendAmount());
        externalApply.applyOnUpdate(job, request);

        JobPosition updatedJob = jobPositionRepository.save(job);

        // Cập nhật lại kỹ năng yêu cầu
        jobSkillRepository.deleteByIdJobId(id);
        jobSkillAssignmentHandler.saveJobSkills(updatedJob, request);

        return jobPositionMapper.toResponse(updatedJob);
    }
}
