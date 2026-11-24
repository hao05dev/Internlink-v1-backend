package com.internlink.core.application.placement;

import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.IntroductionLetterRequest;
import com.internlink.core.presentation.placement.dto.response.IntroductionLetterResponse;
import com.internlink.core.shared.enums.IntroductionLetterStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

class IntroductionLetterResponseMapper {

    IntroductionLetterResponse fromRoster(StudentRoster roster) {
        return fromRoster(roster, null);
    }

    IntroductionLetterResponse fromRoster(StudentRoster roster, IntroductionLetterRequest request) {
        UUID id = request != null ? request.getId() : roster.getId();
        UUID studentId = roster.getClaimedUser() != null ? roster.getClaimedUser().getId() : null;
        IntroductionLetterStatus status = roster.isLetterReceived()
                ? IntroductionLetterStatus.ISSUED
                : request != null ? request.getStatus() : IntroductionLetterStatus.PENDING;
        String code = request != null && request.getIssuedCode() != null
                ? request.getIssuedCode() : "GGT-" + roster.getStudentCode();
        String note = roster.getLetterNotes() != null
                ? roster.getLetterNotes() : request != null ? request.getIssueNote() : null;
        String processor = request != null && request.getProcessedBy() != null
                ? request.getProcessedBy().getFullName() : null;
        OffsetDateTime createdAt = request != null ? request.getCreatedAt() : roster.getCreatedAt();

        return new IntroductionLetterResponse(
                id, studentId, roster.getFullName(), roster.getStudentCode(),
                roster.getTerm().getId(), roster.getTerm().getTermName(),
                request != null && request.getTargetCompanyName() != null ? request.getTargetCompanyName() : "",
                request != null ? request.getTargetPosition() : null,
                request != null ? request.getReason() : null,
                status, code, note, processor,
                request != null ? request.getProcessedAt() : null,
                roster.getLetterReceivedAt(), createdAt, roster.getId(), roster.getClassCode(),
                roster.getOfficialEmail(), roster.isLetterReceived(), roster.getLetterReceivedAt(),
                roster.getProgram() != null && roster.getProgram().getDepartment() != null
                        ? roster.getProgram().getDepartment().getName() : null);
    }

    IntroductionLetterResponse fromRequest(IntroductionLetterRequest request, String studentCode) {
        return new IntroductionLetterResponse(
                request.getId(), request.getStudent().getId(), request.getStudent().getFullName(),
                studentCode, request.getTerm().getId(), request.getTerm().getTermName(),
                request.getTargetCompanyName(), request.getTargetPosition(), request.getReason(),
                request.getStatus(), request.getIssuedCode(), request.getIssueNote(),
                request.getProcessedBy() != null ? request.getProcessedBy().getFullName() : null,
                request.getProcessedAt(), request.getReceivedAt(), request.getCreatedAt(),
                null, null, null, request.getStatus() == IntroductionLetterStatus.ISSUED,
                request.getReceivedAt(), null);
    }
}
