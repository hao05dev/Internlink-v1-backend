package com.internlink.core.presentation.placement.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Phiếu tiếp nhận thực tập kèm thông tin SV/kỳ để VP Trường và Khoa xử lý. */
public record StudentFoundResponse(UUID id, UUID termId, UUID studentId, String hostName,
    String hostAddress, String contactName, String contactEmail, String workDescription,
    LocalDate startDate, LocalDate endDate, UUID acceptanceDocumentId,
    String status, String reviewNote, UUID placementId,
    String source, UUID agreementId, String studentName, String studentCode, String studentEmail,
    String termName, UUID departmentId, String departmentName,
    Integer internshipCredits, Integer requiredWeeks,
    OffsetDateTime updatedAt, OffsetDateTime receivedAt, OffsetDateTime reviewedAt,
    OffsetDateTime publishedAt, OffsetDateTime documentsCollectedAt,
    UUID sourceJobId, String sourceJobTitle, String sourceJobCompanyName) {}
