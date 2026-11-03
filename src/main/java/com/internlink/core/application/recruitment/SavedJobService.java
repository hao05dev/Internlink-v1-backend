package com.internlink.core.application.recruitment;

import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;

import java.util.List;
import java.util.UUID;

public interface SavedJobService {

    /** Saved jobs that are still publicly visible, newest first. */
    List<JobPositionResponse> getSavedJobs(UUID studentId);

    List<UUID> getSavedJobIds(UUID studentId);

    void saveJob(UUID studentId, UUID jobId);

    void unsaveJob(UUID studentId, UUID jobId);
}
