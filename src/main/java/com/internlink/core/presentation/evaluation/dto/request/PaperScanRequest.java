package com.internlink.core.presentation.evaluation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaperScanRequest(@NotNull UUID placementId, @NotNull UUID documentId) {}
