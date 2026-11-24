package com.internlink.core.presentation.placement.dto.response;

import com.internlink.core.shared.enums.IntroductionLetterStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record IntroductionLetterResponse(
        UUID id,
        UUID studentId,
        String studentName,
        String studentCode,
        UUID termId,
        String termName,
        String targetCompanyName,
        String targetPosition,
        String reason,
        IntroductionLetterStatus status,
        String issuedCode,
        String issueNote,
        String processedByName,
        OffsetDateTime processedAt,
        OffsetDateTime receivedAt,
        OffsetDateTime createdAt,
        UUID rosterId,
        String classCode,
        String officialEmail,
        boolean letterReceived,
        OffsetDateTime letterReceivedAt,
        /** Khoa quản lý ngành của SV (VP Trường lọc theo khoa). */
        String departmentName
) {
    public IntroductionLetterResponse(
            UUID id,
            UUID studentId,
            String studentName,
            String studentCode,
            UUID termId,
            String termName,
            String targetCompanyName,
            String targetPosition,
            String reason,
            IntroductionLetterStatus status,
            String issuedCode,
            String issueNote,
            String processedByName,
            OffsetDateTime processedAt,
            OffsetDateTime receivedAt,
            OffsetDateTime createdAt
    ) {
        this(
                id,
                studentId,
                studentName,
                studentCode,
                termId,
                termName,
                targetCompanyName,
                targetPosition,
                reason,
                status,
                issuedCode,
                issueNote,
                processedByName,
                processedAt,
                receivedAt,
                createdAt,
                null,
                null,
                null,
                status == IntroductionLetterStatus.ISSUED,
                receivedAt,
                null
        );
    }
}
