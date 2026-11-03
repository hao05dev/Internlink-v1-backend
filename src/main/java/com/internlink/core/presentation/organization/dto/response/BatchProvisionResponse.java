package com.internlink.core.presentation.organization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchProvisionResponse {
    private int totalWithoutAccount;
    private int newlyCreatedCount;
    private int linkedExistingCount;
    private List<ProvisionedStudentAccountDto> accounts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProvisionedStudentAccountDto {
        private String studentCode;
        private String fullName;
        private String officialEmail;
        private boolean isNewlyCreated;
    }
}
