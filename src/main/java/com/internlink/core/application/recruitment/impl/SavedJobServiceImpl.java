package com.internlink.core.application.recruitment.impl;

import com.internlink.core.application.recruitment.SavedJobService;
import com.internlink.core.domain.recruitment.SavedJob;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobPositionRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaSavedJobRepository;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedJobServiceImpl implements SavedJobService {

    private final JpaSavedJobRepository savedJobRepository;
    private final JpaJobPositionRepository jobPositionRepository;
    private final JobPositionMapper jobPositionMapper;

    @Override
    @Transactional(readOnly = true)
    public List<JobPositionResponse> getSavedJobs(UUID studentId) {
        // Jobs pulled back to draft or rejected after saving are hidden, not deleted
        return savedJobRepository.findByStudentIdAndJobStatusOrderByCreatedAtDesc(studentId, JobStatus.APPROVED).stream()
            .map(SavedJob::getJob)
            .map(jobPositionMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getSavedJobIds(UUID studentId) {
        return savedJobRepository.findJobIdsByStudentIdAndJobStatus(studentId, JobStatus.APPROVED);
    }

    @Override
    @Transactional
    public void saveJob(UUID studentId, UUID jobId) {
        // Only publicly visible jobs can be saved; same 404 as the public detail page
        jobPositionRepository.findById(jobId)
            .filter(job -> job.getStatus() == JobStatus.APPROVED)
            .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", jobId));
        savedJobRepository.insertIfAbsent(studentId, jobId);
    }

    @Override
    @Transactional
    public void unsaveJob(UUID studentId, UUID jobId) {
        savedJobRepository.deleteByStudentIdAndJobId(studentId, jobId);
    }
}
