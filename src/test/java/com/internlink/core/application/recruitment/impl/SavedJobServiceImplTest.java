package com.internlink.core.application.recruitment.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.recruitment.SavedJob;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobPositionRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaSavedJobRepository;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class SavedJobServiceImplTest {
    @Mock JpaSavedJobRepository savedJobRepository;
    @Mock JpaJobPositionRepository jobPositionRepository;
    @Mock JobPositionMapper jobPositionMapper;
    @InjectMocks SavedJobServiceImpl service;

    private final UUID studentId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @Test void savesApprovedJobIdempotently() {
        when(jobPositionRepository.findById(jobId)).thenReturn(Optional.of(job(JobStatus.APPROVED)));

        service.saveJob(studentId, jobId);

        verify(savedJobRepository).insertIfAbsent(studentId, jobId);
    }

    @Test void rejectsJobsThatAreNotPublic() {
        when(jobPositionRepository.findById(jobId)).thenReturn(Optional.of(job(JobStatus.DRAFT)));

        assertThatThrownBy(() -> service.saveJob(studentId, jobId)).isInstanceOf(ResourceNotFoundException.class);
        verify(savedJobRepository, never()).insertIfAbsent(any(), any());
    }

    @Test void rejectsMissingJob() {
        when(jobPositionRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveJob(studentId, jobId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void unsaveDeletesOnlyThisStudentsRow() {
        service.unsaveJob(studentId, jobId);

        verify(savedJobRepository).deleteByStudentIdAndJobId(studentId, jobId);
    }

    @Test void listsOnlyApprovedSavedJobsInRepositoryOrder() {
        JobPosition newer = job(JobStatus.APPROVED);
        JobPosition older = job(JobStatus.APPROVED);
        JobPositionResponse newerDto = JobPositionResponse.builder().title("Newer").build();
        JobPositionResponse olderDto = JobPositionResponse.builder().title("Older").build();
        when(savedJobRepository.findByStudentIdAndJobStatusOrderByCreatedAtDesc(studentId, JobStatus.APPROVED))
            .thenReturn(List.of(SavedJob.builder().job(newer).build(), SavedJob.builder().job(older).build()));
        when(jobPositionMapper.toResponse(newer)).thenReturn(newerDto);
        when(jobPositionMapper.toResponse(older)).thenReturn(olderDto);

        assertThat(service.getSavedJobs(studentId)).containsExactly(newerDto, olderDto);
    }

    @Test void savedIdsAreScopedToApprovedJobs() {
        when(savedJobRepository.findJobIdsByStudentIdAndJobStatus(studentId, JobStatus.APPROVED)).thenReturn(List.of(jobId));

        assertThat(service.getSavedJobIds(studentId)).containsExactly(jobId);
    }

    private JobPosition job(JobStatus status) {
        JobPosition job = new JobPosition();
        job.setId(UUID.randomUUID());
        job.setStatus(status);
        return job;
    }
}
