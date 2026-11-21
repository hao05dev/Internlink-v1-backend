package com.internlink.core.presentation.placement.dto.response;

import com.internlink.core.shared.enums.AgreementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningAgreementResponse {

    private UUID id;
    private UUID offerId;
    private UUID termId;
    private UUID studentId;
    private String studentName;
    private String studentCode;
    private UUID companyId;
    private String companyName;
    private UUID departmentId;
    private String departmentName;
    private Integer targetCredits;
    private String learningObjectives;
    private AgreementStatus status;
    private Map<String, Object> studentSignature;
    private Map<String, Object> companySignature;
    private Map<String, Object> facultySignature;
    private UUID documentId;
    /** Lấy từ offer để giao diện hiển thị đủ nội dung thỏa thuận. */
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private String jobTitle;
    private String mentorName;
    private String mentorEmail;
    /** Thời điểm từng bên ký (null = chưa ký), rút từ chữ ký đã lưu. */
    private String studentSignedAt;
    private String companySignedAt;
    private String facultySignedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
