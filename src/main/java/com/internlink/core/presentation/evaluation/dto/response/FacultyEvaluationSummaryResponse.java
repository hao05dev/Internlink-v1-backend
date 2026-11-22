package com.internlink.core.presentation.evaluation.dto.response;

import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.ResultStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyEvaluationSummaryResponse {

    private UUID placementId;
    private UUID studentId;
    private String studentCode;
    private String studentName;
    private String studentEmail;
    private String classCode;
    private UUID programId;
    private String programName;
    private String academicYear;

    private UUID companyId;
    private String companyName;
    private UUID mentorId;
    private String mentorName;
    private String mentorEmail;
    private BigDecimal mentorScore;
    private String mentorFeedback;

    private UUID lecturerId;
    private String lecturerName;
    private String lecturerEmail;
    private BigDecimal lecturerScore;
    private String lecturerFeedback;

    private BigDecimal complianceScore;
    private BigDecimal finalScore;
    private BigDecimal scoreScale4;
    private String letterGrade;
    private String classification;
    private ResultStatus resultStatus;
    private PlacementStatus placementStatus;

    private Boolean isFinalized;
    private Boolean isPublished;
    private OffsetDateTime publishedAt;
    private OffsetDateTime finalizedAt;

    private String formM04Status; // Doanh nghiệp (M-TT-04 / M03 in portfolio)
    private String formM05Status; // Giảng viên (M-TT-05 / M04 in portfolio)

    /** Hồ sơ nộp Khoa: số file bắt buộc đã được GVHD duyệt / tổng số file bắt buộc. */
    private Integer dossierApprovedCount;
    private Integer dossierRequiredCount;
    private Boolean dossierComplete;
    /** Vấn đề còn lại, vd "M03 chưa nộp", "M01 chờ GVHD duyệt". */
    private List<String> dossierMissing;
}
