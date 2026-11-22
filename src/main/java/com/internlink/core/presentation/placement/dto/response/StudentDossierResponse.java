package com.internlink.core.presentation.placement.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDossierResponse {

    private UUID placementId;
    private String placementTitle;
    private String companyName;
    private int totalRequired;
    private int submittedCount;
    private int missingCount;
    /** Số mục bắt buộc đã được GVHD duyệt (hoặc Văn phòng đã nhận với Mẫu 04). */
    private int approvedCount;
    /** Đủ toàn bộ mục bắt buộc và đã được duyệt: điều kiện để GVHD chấm điểm. */
    @JsonProperty("isComplete")
    private boolean isComplete;
    private String studentName;
    private int completionPercentage;
    @JsonProperty("isLocked")
    private boolean isLocked;
    private String lockReason;
    private OffsetDateTime deadline;

    @Builder.Default
    private List<StudentDossierItemDto> items = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentDossierItemDto {
        private String templateCode;
        private String title;
        private String description;
        private String phase; // ONBOARDING, IN_PROGRESS, CLOSING
        private String signer;
        private boolean isMandatory;
        private boolean isSubmitted;
        private String status; // MISSING, PENDING, APPROVED, REVISION_REQUIRED
        private UUID documentId;
        private String submittedFileName;
        private Long submittedFileSizeBytes;
        private OffsetDateTime submittedAt;
        private String feedback;
        private String reviewerName;
        private OffsetDateTime reviewedAt;
        /** Mẫu 04 được tính đạt vì Văn phòng đã nhận bản giấy phiếu tiếp nhận. */
        private boolean approvedByOffice;
        private String downloadTemplateUrl;
        private String previewTemplateUrl;
        private String fileUrl;
    }
}
