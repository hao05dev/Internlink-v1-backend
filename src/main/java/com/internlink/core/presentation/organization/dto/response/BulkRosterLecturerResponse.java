package com.internlink.core.presentation.organization.dto.response;

import java.util.List;

/** Outcome of a bulk lecturer assignment: rows changed, rows already on that lecturer, and per-student failures. */
public record BulkRosterLecturerResponse(int updated, int unchanged, List<String> failures) {}
