package com.internlink.core.presentation.organization.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Assigns or reassigns the supervising lecturer of roster rows. {@code rosterIds} is only read by the
 * bulk endpoint; {@code reason} is required when a student already interning changes lecturer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RosterLecturerAssignRequest {
    @NotNull
    private UUID lecturerId;

    @Size(max = 500)
    private String reason;

    private List<UUID> rosterIds;
}
