package com.internlink.core.presentation.evaluation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Bản GVHD đã soát: điểm 10 tiêu chí (null = để trống), nhận xét và góp ý CTĐT. */
public record PaperScanConfirmRequest(
        @NotNull Map<String, BigDecimal> criteriaScores,
        @Size(max = 4000) String comments,
        List<String> ctdtOptions,
        @Size(max = 4000) String ctdtSuggestion) {}
