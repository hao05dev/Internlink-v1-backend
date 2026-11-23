package com.internlink.core.presentation.recruitment.dto.response;

import com.internlink.core.shared.enums.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplicationResponse {

    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private String companyName;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    /** Chỉ có trong danh sách ứng viên của DN. */
    private String studentCode;
    private String programName;
    private UUID submittedCvDocumentId;
    private String coverLetter;
    private Map<String, Object> aiMatchDetail;
    private List<InterviewRoundDto> interviewRounds;
    private UUID termId;
    private String termName;
    private String academicYear;
    private String semester;
    private ApplicationStatus status;
    private OffsetDateTime submittedAt;
    private OffsetDateTime updatedAt;
}