package com.internlink.core.application.meeting;

import com.internlink.core.application.meeting.MeetingDtos.AttendeeView;
import com.internlink.core.application.meeting.MeetingDtos.MeetingView;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.meeting.Meeting;
import com.internlink.core.domain.meeting.MeetingAttendee;
import com.internlink.core.infrastructure.persistence.jpa.JpaMeetingAttendeeRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ghép cuộc họp với người tổ chức và danh sách người dự bằng 2 truy vấn gộp (tránh N+1). */
@Component
@RequiredArgsConstructor
public class MeetingViewAssembler {
    private final JpaMeetingAttendeeRepository attendees;
    private final JpaUserRepository users;

    /** includeAttendees=false khi người xem là sinh viên: không lộ email của bạn cùng nhóm. */
    public List<MeetingView> toViews(List<Meeting> meetings, boolean includeAttendees) {
        if (meetings.isEmpty()) return List.of();
        List<UUID> ids = meetings.stream().map(Meeting::getId).toList();
        Map<UUID, List<MeetingAttendee>> byMeeting = attendees.findByMeetingIdIn(ids).stream()
                .collect(Collectors.groupingBy(MeetingAttendee::getMeetingId));
        Set<UUID> userIds = new HashSet<>();
        meetings.forEach(m -> userIds.add(m.getOrganizerId()));
        if (includeAttendees) byMeeting.values().forEach(list -> list.forEach(a -> userIds.add(a.getUserId())));
        Map<UUID, User> people = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return meetings.stream()
                .map(m -> toView(m, byMeeting.getOrDefault(m.getId(), List.of()), people, includeAttendees))
                .toList();
    }

    public MeetingView toView(Meeting meeting, boolean includeAttendees) {
        return toViews(List.of(meeting), includeAttendees).get(0);
    }

    private static MeetingView toView(
            Meeting m, List<MeetingAttendee> list, Map<UUID, User> people, boolean includeAttendees) {
        List<AttendeeView> attendeeViews = includeAttendees
                ? list.stream()
                        .map(a -> {
                            User u = people.get(a.getUserId());
                            return new AttendeeView(
                                    a.getUserId(),
                                    u == null ? "" : u.getFullName(),
                                    u == null ? "" : u.getEmail(),
                                    a.getAttendance());
                        })
                        .sorted(Comparator.comparing(AttendeeView::fullName))
                        .toList()
                : List.of();
        User organizer = people.get(m.getOrganizerId());
        boolean cancelled = Meeting.STATUS_CANCELLED.equals(m.getStatus());
        return new MeetingView(
                m.getId(), m.getTitle(), m.getAgenda(), m.getStartAt(), m.getEndAt(),
                cancelled ? null : m.getMeetUrl(), m.getLinkMode(), m.getStatus(), m.getCancelReason(),
                m.getOrganizerId(), organizer == null ? "" : organizer.getFullName(),
                list.size(), attendeeViews);
    }
}
