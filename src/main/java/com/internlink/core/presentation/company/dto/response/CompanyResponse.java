package com.internlink.core.presentation.company.dto.response;

import com.internlink.core.shared.enums.VerificationStatus;
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
public class CompanyResponse {

    private UUID id;
    private String companyName;
    private String taxCode;
    private String industry;
    private String website;
    private String logoUrl;
    private String coverImageUrl;
    private Map<String, Object> address;
    private VerificationStatus verificationStatus;
    private Map<String, Object> verificationDetail;
    private UUID verifiedByUserId;
    private OffsetDateTime verifiedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
