package com.internlink.core.presentation.organization.dto.response;

import java.util.List;

/**
 * Kết quả nhập danh sách SV. {@code rosters}: các dòng đã thêm/cập nhật;
 * {@code skipped}: các dòng Khoa không được sửa (giữ theo danh sách của VP Trường hoặc SV khoa khác).
 */
public record RosterImportResponse(int created, int updated, List<StudentRosterResponse> rosters, List<SkippedRow> skipped) {
    public record SkippedRow(String studentCode, String reason) {}
}
