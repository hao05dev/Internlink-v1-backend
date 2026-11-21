package com.internlink.core.presentation.evaluation.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;

public record AssessmentSchemeRequest(
    @NotNull UUID termId,
    @NotNull UUID programId,
    @NotBlank String cohortCode,
    @NotBlank String courseCode,
    @NotNull @Min(1) Integer revision,
    @NotBlank String sourceReference,
    @NotEmpty List<Map<String, Object>> components,
    @NotNull @Min(0) Integer requiredLogbookWeeks,
    @NotNull @Min(0) Integer weeklyGraceDays,
    @NotNull Boolean requireMidtermReport,
    @NotNull Boolean requireFinalReport,
    OffsetDateTime midtermReportDueAt,
    OffsetDateTime finalReportDueAt
) {}
