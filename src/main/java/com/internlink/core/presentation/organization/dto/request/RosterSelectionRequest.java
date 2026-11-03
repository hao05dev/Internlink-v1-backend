package com.internlink.core.presentation.organization.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Roster rows to act on; omit {@code rosterIds} to act on the whole term. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RosterSelectionRequest {
    private List<UUID> rosterIds;
}
