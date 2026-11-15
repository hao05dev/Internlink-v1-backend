package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.meeting.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface JpaMeetingRepository extends JpaRepository<Meeting, UUID> {
    List<Meeting> findByOrganizerIdAndStartAtBetweenOrderByStartAt(
            UUID organizerId, OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT m FROM Meeting m WHERE m.startAt BETWEEN :from AND :to AND m.id IN "
            + "(SELECT a.meetingId FROM MeetingAttendee a WHERE a.userId = :userId) ORDER BY m.startAt")
    List<Meeting> findForAttendee(
            @Param("userId") UUID userId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
