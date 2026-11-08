package com.internlink.core.application.recruitment.impl;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.shared.api.PageParam;
import com.internlink.core.shared.api.PageResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobPositionQueryService {
    private final JpaJobPositionRepository jobPositionRepository;
    private final JobPositionMapper jobPositionMapper;
    private final com.internlink.core.shared.api.PaginatedResponseMapper paginatedResponseMapper;
    private final com.internlink.core.application.recruitment.JobPositionAccessPolicy access;
    private final JpaJobApplicationRepository applications;

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getAllJobs() {
        return getAllJobs(null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getAllJobs(String keyword, UUID termId, UUID companyId) {
        return getAllJobs(keyword, termId, companyId, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getAllJobs(
            String keyword, UUID termId, UUID companyId, com.internlink.core.shared.enums.JobStatus status, String location, com.internlink.core.shared.enums.WorkFormat workFormat
    ) {
        User actor = access.currentActor();
        return jobPositionRepository.searchJobs(keyword, termId, companyId, status, location, workFormat).stream()
                .filter(job -> access.canReadJob(actor, job))
                .map(jobPositionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getJobsByCompany(UUID companyId) {
        User actor = access.currentActor();
        return jobPositionRepository.searchJobs(null, null, companyId, null).stream()
                .filter(job -> access.canReadJob(actor, job))
                .map(jobPositionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getJobsByTerm(UUID termId) {
        User actor = access.currentActor();
        return jobPositionRepository.searchJobs(null, termId, null, null).stream()
                .filter(job -> access.canReadJob(actor, job))
                .map(jobPositionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getApprovedJobs(UUID termId) {
        return getApprovedJobs(termId, null);
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getApprovedJobs(UUID termId, String keyword) {
        return jobPositionRepository.searchJobs(keyword, termId, null, JobStatus.APPROVED).stream()
                .map(jobPositionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getPublicJobs(String keyword, UUID termId, UUID companyId) {
        return getPublicJobs(keyword, termId, companyId, null, null);
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> getPublicJobs(
            String keyword, UUID termId, UUID companyId, String location, com.internlink.core.shared.enums.WorkFormat workFormat
    ) {
        List<JobPositionResponse> rows = jobPositionRepository
                .searchActivePublicJobs(keyword, termId, companyId, location, workFormat, OffsetDateTime.now())
                .stream()
                .map(jobPositionMapper::toResponse)
                .toList();
        return withApplicantCounts(rows);
    }

    private List<JobPositionResponse> withApplicantCounts(List<JobPositionResponse> rows) {
        if (rows.isEmpty()) return rows;
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : applications.countApplicantsByJobIds(rows.stream().map(JobPositionResponse::getId).toList())) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        rows.forEach(row -> row.setApplicantCount(counts.getOrDefault(row.getId(), 0L)));
        return rows;
    }

    @Transactional(readOnly = true)
    public PageResponse<JobPositionResponse> getPublicJobsPage(
            String keyword, UUID termId, UUID companyId, String location, com.internlink.core.shared.enums.WorkFormat workFormat, PageParam pageParam
    ) {
        org.springframework.data.domain.Page<JobPosition> page = jobPositionRepository
                .searchActivePublicJobsPage(keyword, termId, companyId, location, workFormat, OffsetDateTime.now(), pageParam.toPageable());
        return paginatedResponseMapper.toPageResponse(page, jobPositionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public JobPositionResponse getPublicJobById(UUID id) {
        JobPosition job =
                jobPositionRepository
                        .findById(id)
                        .filter(position -> position.getStatus() == JobStatus.APPROVED)
                        .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", id));
        return jobPositionMapper.toResponse(job);
    }

    @Transactional(readOnly = true)
    public JobPositionResponse getJobById(UUID id) {
        JobPosition job =
                jobPositionRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", id));
        if (job.getStatus() != JobStatus.APPROVED) {
            ResourceAuthorization.require(access.canReadJob(access.currentActor(), job));
        }
        return jobPositionMapper.toResponse(job);
    }

}
