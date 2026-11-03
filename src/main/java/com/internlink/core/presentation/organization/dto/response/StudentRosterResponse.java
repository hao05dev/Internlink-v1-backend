package com.internlink.core.presentation.organization.dto.response;

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
public class StudentRosterResponse {

    private UUID id;
    private UUID termId;
    private String termName;
    private UUID programId;
    private String programName;
    private String studentCode;
    private String officialEmail;
    private String fullName;
    private String academicYear;
    private String classCode;
    private String internshipCourseCode;
    private Integer internshipCredits;
    private Integer requiredWeeks;
    private UUID claimedUserId;
    private OffsetDateTime claimedAt;
    private UUID assignedLecturerId;
    private String lecturerCode;
    private String lecturerName;
    private String lecturerEmail;
    private boolean letterReceived;
    private OffsetDateTime letterReceivedAt;
    private String letterNotes;
    private java.math.BigDecimal gpa;
    private String phoneNumber;
    private OffsetDateTime createdAt;
    /** Lần đăng ký học phần thực tập thứ mấy (1 = lần đầu), tính theo MSSV trong cùng khoa. */
    @Builder.Default
    private int attemptNumber = 1;
    /** Các kỳ trước của cùng MSSV, cũ nhất trước. */
    @Builder.Default
    private List<RosterPriorAttemptResponse> priorAttempts = List.of();
}
