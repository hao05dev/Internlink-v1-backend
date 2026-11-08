package com.internlink.core.presentation.student.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ngữ cảnh cá nhân của sinh viên cho bộ lọc thông minh ở trang tìm việc.
 * Gom một lần để FE không phải gọi rời hồ sơ, kỹ năng, đơn đã nộp và tin đã lưu.
 */
public record JobSearchContextResponse(
        String programCode,
        String programName,
        List<String> confirmedSkillIds,
        List<UUID> appliedJobIds,
        List<UUID> savedJobIds,
        UUID activeTermId,
        String activeTermName,
        Map<String, Object> preferences) {}
