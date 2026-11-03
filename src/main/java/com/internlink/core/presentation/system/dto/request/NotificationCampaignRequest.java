package com.internlink.core.presentation.system.dto.request;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record NotificationCampaignRequest(
        @NotNull UUID termId,
        @NotNull UUID requestKey,
        @NotBlank @Size(max = 30) String purpose,
        @NotBlank @Size(max = 40) String audience,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 20000) String body,
        @NotBlank String channel) {}
