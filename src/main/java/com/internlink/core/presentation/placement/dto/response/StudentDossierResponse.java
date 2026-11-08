package com.internlink.core.presentation.placement.dto.response;

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
    private int completionPercentage;
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
        private String downloadTemplateUrl;
        private String previewTemplateUrl;
        private String fileUrl;
    }
}
