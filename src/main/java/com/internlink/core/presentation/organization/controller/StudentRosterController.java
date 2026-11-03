package com.internlink.core.presentation.organization.controller;

import com.internlink.core.application.organization.GoogleSheetRosterService;
import com.internlink.core.application.organization.RosterLecturerAssignmentService;
import com.internlink.core.application.organization.StudentRosterService;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.organization.dto.request.RosterLecturerAssignRequest;
import com.internlink.core.presentation.organization.dto.request.RosterSelectionRequest;
import com.internlink.core.presentation.organization.dto.request.StudentRosterImportItem;
import com.internlink.core.presentation.organization.dto.response.BatchProvisionResponse;
import com.internlink.core.presentation.organization.dto.response.BulkRosterLecturerResponse;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;
import com.internlink.core.shared.api.ApiResponse;
import com.internlink.core.shared.validation.ImportRowLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rosters")
@RequiredArgsConstructor
public class StudentRosterController {

    private final StudentRosterService rosterService;
    private final GoogleSheetRosterService googleSheetRosterService;
    private final RosterLecturerAssignmentService lecturerAssignmentService;

    public record GoogleSheetImportRequest(String spreadsheetId, String range) {}

    @PostMapping("/import-sheet/{termId}")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<List<StudentRosterResponse>>> importGoogleSheet(
        @PathVariable UUID termId, @RequestBody GoogleSheetImportRequest request
    ) {
        var responses = googleSheetRosterService.importSheet(termId, request.spreadsheetId(), request.range());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Đã import " + responses.size() + " sinh viên từ Google Sheets", responses));
    }

    @GetMapping("/by-term/{termId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_ADMIN', 'LECTURER')")
    public ResponseEntity<ApiResponse<List<StudentRosterResponse>>> getRostersByTerm(
            @PathVariable UUID termId
    ) {
        List<StudentRosterResponse> rosters = rosterService.getRostersByTerm(termId);
        return ResponseEntity.ok(ApiResponse.success(rosters));
    }

    @PostMapping("/import/{termId}")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<List<StudentRosterResponse>>> importRosterList(
            @PathVariable UUID termId,
            @Valid @RequestBody List<StudentRosterImportItem> items
    ) {
        ImportRowLimit.check(items);
        List<StudentRosterResponse> responses = rosterService.importRosterList(termId, items);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Import danh sách sinh viên thành công (" + responses.size() + " sinh viên)", responses));
    }

    @PostMapping("/{rosterId}/provision-account")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<StudentRosterResponse>> provisionAccount(
            @PathVariable UUID rosterId
    ) {
        StudentRosterResponse response = rosterService.provisionAccount(rosterId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài khoản sinh viên thành công", response));
    }

    @PostMapping("/by-term/{termId}/batch-provision")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<BatchProvisionResponse>> batchProvisionEligibleAccounts(
            @PathVariable UUID termId,
            @RequestBody(required = false) RosterSelectionRequest request
    ) {
        BatchProvisionResponse response = rosterService.batchProvisionEligibleAccounts(
                termId, request != null ? request.getRosterIds() : null);
        String msg = String.format("Đã xử lý tạo tài khoản cho %d sinh viên (Mới: %d, Đã liên kết: %d)",
                response.getTotalWithoutAccount(), response.getNewlyCreatedCount(), response.getLinkedExistingCount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(msg, response));
    }

    @PatchMapping("/{rosterId}/lecturer")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<StudentRosterResponse>> assignLecturer(
            @PathVariable UUID rosterId,
            @Valid @RequestBody RosterLecturerAssignRequest request
    ) {
        StudentRosterResponse response = lecturerAssignmentService.assign(rosterId, request.getLecturerId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Đã phân công GVHD", response));
    }

    @PatchMapping("/by-term/{termId}/lecturer")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<BulkRosterLecturerResponse>> assignLecturerBulk(
            @PathVariable UUID termId,
            @Valid @RequestBody RosterLecturerAssignRequest request
    ) {
        BulkRosterLecturerResponse response = lecturerAssignmentService.assignBulk(
                termId, request.getRosterIds(), request.getLecturerId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(
                "Đã phân công GVHD cho " + response.updated() + " sinh viên", response));
    }

    @DeleteMapping("/{rosterId}/lecturer")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<StudentRosterResponse>> unassignLecturer(@PathVariable UUID rosterId) {
        StudentRosterResponse response = lecturerAssignmentService.unassign(rosterId);
        return ResponseEntity.ok(ApiResponse.success("Đã gỡ phân công GVHD", response));
    }

    @GetMapping("/my-supervision")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentRosterResponse>> getMySupervisionInfo(
            @AuthenticationPrincipal CustomUserDetail principal
    ) {
        StudentRosterResponse response = rosterService.getMySupervisionInfo(principal.getId(), principal.getEmail());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
