package com.internlink.core.presentation.student.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileResponse {

    private UUID userId;
    private String studentCode;
    private String fullName;
    private String email;
    private String phoneNumber;
    private UUID programId;
    private String programCode;
    private String programName;
    private String departmentName;
    private BigDecimal gpa;
    private String bio;
    private List<Map<String, Object>> certificates;
    private List<Map<String, Object>> passedCourses;
    private Map<String, Object> preferences;
    private UUID cvDocumentId;
    private String cvFileName;
    private OffsetDateTime updatedAt;
}
