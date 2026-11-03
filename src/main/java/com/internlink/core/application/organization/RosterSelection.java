package com.internlink.core.application.organization;

import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.shared.exception.BadRequestException;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Optional subset of a term's roster picked by the faculty admin.
 * {@code null} keeps the old behaviour (whole term); an empty list is rejected.
 */
public final class RosterSelection {

    private RosterSelection() {
    }

    public static Predicate<StudentRoster> of(Collection<UUID> rosterIds) {
        if (rosterIds == null) return roster -> true;
        if (rosterIds.isEmpty()) throw new BadRequestException("Chưa chọn sinh viên nào");
        Set<UUID> ids = Set.copyOf(rosterIds);
        return roster -> ids.contains(roster.getId());
    }

    public static boolean isPartial(Collection<UUID> rosterIds) {
        return rosterIds != null;
    }
}
