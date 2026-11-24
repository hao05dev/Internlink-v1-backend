package com.internlink.core.presentation.placement.controller;

import com.internlink.core.application.placement.AcceptanceFormOfficeService;
import com.internlink.core.application.placement.EligibleListPublishService;
import com.internlink.core.application.placement.EligibleListWorkbook;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import com.internlink.core.shared.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Văn phòng Trường CNTT-TT: tiếp nhận bản giấy Phiếu tiếp nhận, công bố danh sách SV đủ điều kiện, phát giấy tờ. */
@RestController @RequestMapping("/api/v1/student-found/office") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
public class AcceptanceFormOfficeController {
    private final AcceptanceFormOfficeService service;
    private final EligibleListPublishService eligibleList;

    @GetMapping
    public ApiResponse<List<StudentFoundResponse>> intake(@RequestParam(required = false) UUID termId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID departmentId) {
        return ApiResponse.success(service.intake(termId, status, departmentId));
    }
    @PatchMapping("/{id}/receive")
    public ApiResponse<StudentFoundResponse> receive(@PathVariable UUID id) {
        return ApiResponse.success(service.confirmReceived(id));
    }
    @PatchMapping("/{id}/return")
    public ApiResponse<StudentFoundResponse> returnToStudent(@PathVariable UUID id, @RequestParam String note) {
        return ApiResponse.success(service.returnToStudent(id, note));
    }
    @PatchMapping("/{id}/documents-collected")
    public ApiResponse<StudentFoundResponse> documentsCollected(@PathVariable UUID id) {
        return ApiResponse.success(service.confirmDocumentsCollected(id));
    }

    /** Số phiếu chờ công bố, các đợt đã công bố và bài tin của kỳ. */
    @GetMapping("/term/{termId}/eligible-list")
    public ApiResponse<EligibleListPublishService.Summary> eligibleListSummary(@PathVariable UUID termId) {
        return ApiResponse.success(eligibleList.summary(termId));
    }

    /** Tệp Excel của đợt {@code batch}; bỏ trống = bản xem trước của đợt sắp công bố. */
    @GetMapping("/term/{termId}/eligible-list.xlsx")
    public ResponseEntity<byte[]> eligibleListWorkbook(@PathVariable UUID termId,
                                                       @RequestParam(required = false) Integer batch) {
        var file = eligibleList.workbook(termId, batch);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(EligibleListWorkbook.MIME))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .body(file.bytes());
    }

    @PostMapping("/term/{termId}/publish")
    public ApiResponse<EligibleListPublishService.PublishResult> publish(@PathVariable UUID termId,
                                                                         @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.success(eligibleList.publish(termId, body == null ? null : body.get("note")));
    }
}
