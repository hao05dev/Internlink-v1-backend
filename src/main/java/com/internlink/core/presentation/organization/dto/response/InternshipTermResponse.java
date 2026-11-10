package com.internlink.core.presentation.organization.dto.response;

import com.internlink.core.shared.enums.TermStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternshipTermResponse {

    private UUID id;
    private String code;
    private String termName;
    private String academicYear;
    private String semester;
    private OffsetDateTime registrationOpenAt;
    private OffsetDateTime registrationCloseAt;
    private LocalDate startDate;
    private LocalDate endDate;
    private OffsetDateTime applicationDeadline;
    private OffsetDateTime evaluationDeadline;
    private TermStatus status;
    private Map<String, Object> settings;
    private OffsetDateTime createdAt;
}