package com.internlink.core.presentation.organization.dto.response;

import java.util.UUID;

/**
 * Một kỳ trước mà sinh viên (cùng MSSV) đã có tên trong danh sách học phần thực tập.
 * outcome: PASSED | FAILED | TERMINATED | IN_PROGRESS | NO_PLACEMENT.
 */
public record RosterPriorAttemptResponse(UUID termId, String termName, String outcome) {
}
