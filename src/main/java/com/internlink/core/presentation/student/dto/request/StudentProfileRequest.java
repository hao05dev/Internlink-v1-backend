package com.internlink.core.presentation.student.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileRequest {

    @NotNull(message = "Ngành đào tạo không được để trống")
    private UUID programId;

    @NotBlank(message = "Mã số sinh viên không được để trống")
    private String studentCode;

    @DecimalMin(value = "0.0", message = "GPA tối thiểu là 0.0")
    @DecimalMax(value = "4.0", message = "GPA tối đa là 4.0")
    private BigDecimal gpa;

    private String bio;

    @Builder.Default
    private List<Map<String, Object>> certificates = List.of();

    @Builder.Default
    private List<Map<String, Object>> passedCourses = List.of();

    @Builder.Default
    private Map<String, Object> preferences = Map.of();
}