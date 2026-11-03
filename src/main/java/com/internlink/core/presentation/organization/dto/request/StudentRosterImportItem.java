package com.internlink.core.presentation.organization.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRosterImportItem {

    @NotNull(message = "Ngành học không được để trống")
    private UUID programId;

    @NotBlank(message = "Mã số sinh viên không được để trống")
    private String studentCode;

    @NotBlank(message = "Email CTU không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String officialEmail;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotBlank(message = "Khóa học không được để trống")
    private String academicYear;

    private String classCode;

    @NotBlank(message = "Mã học phần thực tập không được để trống")
    private String internshipCourseCode;

    /** Số tín chỉ học phần thực tập (2, 3 hoặc 5), không bắt buộc. */
    private Integer internshipCredits;

    private String lecturerCode;

    private String lecturerName;

    private String lecturerEmail;

    @NotNull(message = "Điểm GPA không được để trống")
    @DecimalMin(value = "0.0", message = "Điểm GPA phải nằm trong khoảng 0.0 - 4.0")
    @DecimalMax(value = "4.0", message = "Điểm GPA phải nằm trong khoảng 0.0 - 4.0")
    private java.math.BigDecimal gpa;

    private String phoneNumber;
}
