package com.internlink.core.application.meeting;

import com.internlink.core.application.meeting.MeetingDtos.*;
import com.internlink.core.application.meeting.MeetingNotifier.Change;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.meeting.Meeting;
import com.internlink.core.domain.meeting.MeetingAttendee;
import com.internlink.core.infrastructure.persistence.jpa.JpaMeetingAttendeeRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaMeetingRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tạo, đổi, hủy và liệt kê cuộc họp Google Meet (chế độ dán link thủ công). */
@Service
@RequiredArgsConstructor
public class MeetingService {
    private static final Duration PAST_TOLERANCE = Duration.ofMinutes(5);
    private static final Duration MAX_AHEAD = Duration.ofDays(366);
    private static final Duration MAX_RANGE = Duration.ofDays(400);

    private final JpaMeetingRepository meetings;
    private final JpaMeetingAttendeeRepository attendees;
    private final JpaUserRepository users;
    private final MeetingAccessPolicy policy;
    private final MeetingNotifier notifier;
    private final MeetingViewAssembler assembler;
    private final SupervisedStudentDirectory directory;
    private final AuditLogService audit;
    private final SecurityGuard guard;

    @Transactional(readOnly = true)
    public List<SupervisedStudentDirectory.SupervisedStudent> supervisedStudents() {
        return directory.forLecturer(me());
    }

    @Transactional
    public MeetingView create(MeetingRequest request) {
        UUID organizerId = me();
        Set<UUID> invited = validate(request, organizerId);
        Meeting meeting = meetings.saveAndFlush(Meeting.builder()
                .organizerId(organizerId)
                .contextType(Meeting.CONTEXT_SUPERVISION)
                .linkMode(Meeting.LINK_MANUAL)
                .status(Meeting.STATUS_SCHEDULED)
                .title(request.title().strip())
                .agenda(blankToNull(request.agenda()))
                .startAt(request.startAt())
                .endAt(request.startAt().plusMinutes(request.durationMinutes()))
                .meetUrl(MeetLinks.normalize(request.meetUrl()))
                .build());
        Map<UUID, User> people = loadUsers(invited, organizerId);
        invited.forEach(id -> addAttendee(meeting, people.get(organizerId), people.get(id), Change.SCHEDULED));
        log(organizerId, "MEETING_CREATED", meeting, Map.of("attendees", invited.size()));
        return assembler.toView(meeting, true);
    }

    @Transactional
    public MeetingView update(UUID id, MeetingRequest request) {
        UUID organizerId = me();
        Meeting meeting = ownedScheduled(id, organizerId);
        Set<UUID> invited = validate(request, organizerId);
        meeting.setTitle(request.title().strip());
        meeting.setAgenda(blankToNull(request.agenda()));
        meeting.setStartAt(request.startAt());
        meeting.setEndAt(request.startAt().plusMinutes(request.durationMinutes()));
        meeting.setMeetUrl(MeetLinks.normalize(request.meetUrl()));
        meetings.saveAndFlush(meeting);

        Map<UUID, MeetingAttendee> current = attendeeMap(id);
        Set<UUID> everyone = new LinkedHashSet<>(invited);
        everyone.addAll(current.keySet());
        Map<UUID, User> people = loadUsers(everyone, organizerId);
        User organizer = people.get(organizerId);
        for (UUID userId : everyone) {
            MeetingAttendee existing = current.get(userId);
            if (existing == null) {
                addAttendee(meeting, organizer, people.get(userId), Change.SCHEDULED);
            } else if (!invited.contains(userId)) {
                attendees.delete(existing);
                notifier.notify(meeting, organizer, people.get(userId), Change.CANCELLED);
            } else {
                notifier.notify(meeting, organizer, people.get(userId), Change.UPDATED);
            }
        }
        log(organizerId, "MEETING_UPDATED", meeting, Map.of("attendees", invited.size()));
        return assembler.toView(meeting, true);
    }

    @Transactional
    public MeetingView cancel(UUID id, CancelRequest request) {
        UUID organizerId = me();
        Meeting meeting = ownedScheduled(id, organizerId);
        meeting.setStatus(Meeting.STATUS_CANCELLED);
        meeting.setCancelReason(request == null ? null : blankToNull(request.reason()));
        meetings.saveAndFlush(meeting);
        Map<UUID, MeetingAttendee> current = attendeeMap(id);
        Map<UUID, User> people = loadUsers(current.keySet(), organizerId);
        current.keySet().forEach(userId ->
                notifier.notify(meeting, people.get(organizerId), people.get(userId), Change.CANCELLED));
        log(organizerId, "MEETING_CANCELLED", meeting, Map.of());
        return assembler.toView(meeting, true);
    }

    @Transactional
    public MeetingView recordAttendance(UUID id, AttendanceRequest request) {
        Meeting meeting = ownedScheduled(id, me());
        if (meeting.getStartAt().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Chỉ điểm danh được sau khi cuộc họp bắt đầu");
        }
        Map<UUID, MeetingAttendee> current = attendeeMap(id);
        for (AttendanceItem item : request.items()) {
            MeetingAttendee row = current.get(item.userId());
            if (row == null) throw new BadRequestException("Có người không thuộc danh sách mời");
            row.setAttendance(item.attendance());
        }
        attendees.saveAll(current.values());
        return assembler.toView(meeting, true);
    }

    @Transactional(readOnly = true)
    public List<MeetingView> listOrganized(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime[] range = range(from, to);
        return assembler.toViews(
                meetings.findByOrganizerIdAndStartAtBetweenOrderByStartAt(me(), range[0], range[1]), true);
    }

    @Transactional(readOnly = true)
    public List<MeetingView> listMine(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime[] range = range(from, to);
        return assembler.toViews(meetings.findForAttendee(me(), range[0], range[1]), false);
    }

    private Set<UUID> validate(MeetingRequest request, UUID organizerId) {
        OffsetDateTime now = OffsetDateTime.now();
        if (request.startAt().isBefore(now.minus(PAST_TOLERANCE))) {
            throw new BadRequestException("Thời gian bắt đầu đã qua");
        }
        if (request.startAt().isAfter(now.plus(MAX_AHEAD))) {
            throw new BadRequestException("Chỉ hẹn họp trong vòng 1 năm tới");
        }
        Set<UUID> invited = new LinkedHashSet<>(request.attendeeIds());
        policy.requireSupervisedAttendees(organizerId, invited);
        return invited;
    }

    private void addAttendee(Meeting meeting, User organizer, User student, Change change) {
        attendees.save(MeetingAttendee.builder()
                .meetingId(meeting.getId())
                .userId(student.getId())
                .attendance(MeetingAttendee.UNKNOWN)
                .notifiedAt(OffsetDateTime.now())
                .build());
        notifier.notify(meeting, organizer, student, change);
    }

    private Map<UUID, MeetingAttendee> attendeeMap(UUID meetingId) {
        return attendees.findByMeetingId(meetingId).stream()
                .collect(Collectors.toMap(MeetingAttendee::getUserId, Function.identity()));
    }

    private Map<UUID, User> loadUsers(Set<UUID> ids, UUID organizerId) {
        Set<UUID> all = new HashSet<>(ids);
        all.add(organizerId);
        Map<UUID, User> found = users.findAllById(all).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (found.size() != all.size()) throw new ResourceNotFoundException("Không tìm thấy người dùng");
        return found;
    }

    private Meeting ownedScheduled(UUID id, UUID organizerId) {
        Meeting meeting = meetings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cuộc họp"));
        policy.requireOrganizer(meeting, organizerId);
        policy.requireScheduled(meeting);
        return meeting;
    }

    private static OffsetDateTime[] range(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime start = from != null ? from : OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = to != null ? to : start.plusDays(120);
        if (!end.isAfter(start) || Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw new BadRequestException("Khoảng thời gian không hợp lệ (tối đa 400 ngày)");
        }
        return new OffsetDateTime[] {start, end};
    }

    private void log(UUID actorId, String action, Meeting meeting, Map<String, Object> details) {
        audit.logAction(actorId, action, "Meeting", meeting.getId(), "SUCCESS", details, null);
    }

    private UUID me() {
        return guard.currentUser().getId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
