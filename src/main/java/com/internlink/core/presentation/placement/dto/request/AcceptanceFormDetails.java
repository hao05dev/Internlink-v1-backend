package com.internlink.core.presentation.placement.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Các field của Google Form "Thực tập thực tế" mà Form Phiếu tiếp nhận gốc chưa có.
 * {@code weeklyPlan}: công việc dự kiến từng tuần (không bắt buộc, tối đa 12 dòng).
 */
public record AcceptanceFormDetails(
    @Size(max = 100) String hostProvince,
    @Size(max = 30) String hostPhone,
    @Size(max = 30) String contactPhone,
    @Min(1) @Max(14) Integer sessionsPerWeek,
    Boolean hasWorkspace,
    Boolean hasComputer,
    @Size(max = 12) List<@Size(max = 1000) String> weeklyPlan,
    @Size(max = 2000) String otherSupport) {

    /** Quy định của Khoa: làm việc tại đơn vị tối thiểu 6 buổi mỗi tuần. */
    public static final int MIN_SESSIONS_PER_WEEK = 6;
}
