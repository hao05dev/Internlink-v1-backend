package com.internlink.core.presentation.evaluation.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PaperScanResponse(
        UUID id,
        UUID placementId,
        UUID documentId,
        String model,
        Map<String, Object> reading,
        List<Map<String, Object>> warnings,
        BigDecimal suggestedScore,
        Map<String, Object> confirmed,
        BigDecimal confirmedScore,
        Integer editedFields,
        OffsetDateTime confirmedAt,
        OffsetDateTime createdAt) {}
