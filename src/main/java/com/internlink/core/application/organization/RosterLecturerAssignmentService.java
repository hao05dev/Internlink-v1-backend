package com.internlink.core.application.organization;

import com.internlink.core.presentation.organization.dto.response.BulkRosterLecturerResponse;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;

import java.util.List;
import java.util.UUID;

/**
 * Supervising lecturer (GVHD) of a roster row. The roster import already carries the lecturer chosen at
 * course registration; this service fills gaps and reassigns when a student or lecturer needs a change,
 * keeping the student's live placement on the same lecturer.
 */
public interface RosterLecturerAssignmentService {
    StudentRosterResponse assign(UUID rosterId, UUID lecturerId, String reason);
    BulkRosterLecturerResponse assignBulk(UUID termId, List<UUID> rosterIds, UUID lecturerId, String reason);
    StudentRosterResponse unassign(UUID rosterId);
}
