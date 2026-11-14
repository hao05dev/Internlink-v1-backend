package com.internlink.core.presentation.recruitment.dto.response;

import java.util.UUID;

/**
 * Mức quan tâm tới một tin EXTERNAL: số SV đã bấm sang kênh của DN, tổng lượt bấm,
 * và số Phiếu tiếp nhận SV đã báo kết quả với tin này làm nguồn.
 */
public record ExternalApplyStatsResponse(UUID jobId, long interestedStudents, long totalClicks,
    long acceptanceForms) {}
