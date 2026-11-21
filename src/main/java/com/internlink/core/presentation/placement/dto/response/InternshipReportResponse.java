package com.internlink.core.presentation.placement.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InternshipReportResponse(UUID id, UUID placementId, String reportType, UUID documentId,
    String status, Boolean wasLate, String lecturerFeedback, OffsetDateTime submittedAt, OffsetDateTime reviewedAt,
    String fileName, Long fileSizeBytes) {}
