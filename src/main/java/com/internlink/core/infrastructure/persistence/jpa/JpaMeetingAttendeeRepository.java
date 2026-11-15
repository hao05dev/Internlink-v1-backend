package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.meeting.MeetingAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaMeetingAttendeeRepository extends JpaRepository<MeetingAttendee, MeetingAttendee.Key> {
    List<MeetingAttendee> findByMeetingId(UUID meetingId);

    List<MeetingAttendee> findByMeetingIdIn(Collection<UUID> meetingIds);

    boolean existsByMeetingIdAndUserId(UUID meetingId, UUID userId);
}
