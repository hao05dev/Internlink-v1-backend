package com.internlink.core.application.meeting;

import com.internlink.core.application.meeting.MeetingDtos.*;
import com.internlink.core.application.meeting.MeetingNotifier.Change;
import com.internlink.core.application.meeting.SupervisedStudentDirectory.SupervisedStudent;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.meeting.Meeting;
import com.internlink.core.domain.meeting.MeetingAttendee;
import com.internlink.core.infrastructure.persistence.jpa.JpaMeetingAttendeeRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaMeetingRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetingServiceTest {
    JpaMeetingRepository meetings = mock(JpaMeetingRepository.class);
    JpaMeetingAttendeeRepository attendees = mock(JpaMeetingAttendeeRepository.class);
    JpaUserRepository users = mock(JpaUserRepository.class);
    SupervisedStudentDirectory directory = mock(SupervisedStudentDirectory.class);
    MeetingNotifier notifier = mock(MeetingNotifier.class);
    MeetingViewAssembler assembler = mock(MeetingViewAssembler.class);
    SecurityGuard guard = mock(SecurityGuard.class);
    MeetingService service = new MeetingService(
            meetings, attendees, users, new MeetingAccessPolicy(directory), notifier, assembler,
            directory, mock(AuditLogService.class), guard);

    User lecturer = user("GV Sơn");
    User an = user("An");
    User binh = user("Bình");
    User outsider = user("Người ngoài");

    @BeforeEach
    void setUp() {
        signIn(lecturer.getId());
        when(directory.forLecturer(lecturer.getId())).thenReturn(List.of(
                new SupervisedStudent(an.getId(), "An", "an@student.ctu.edu.vn", "B1", "HK1"),
                new SupervisedStudent(binh.getId(), "Bình", "binh@student.ctu.edu.vn", "B2", "HK1")));
        when(users.findAllById(any())).thenAnswer(call -> {
            Set<UUID> ids = new HashSet<>();
            ((Iterable<UUID>) call.getArgument(0)).forEach(ids::add);
            return List.of(lecturer, an, binh, outsider).stream().filter(u -> ids.contains(u.getId())).toList();
        });
        when(meetings.saveAndFlush(any())).thenAnswer(call -> {
            Meeting m = call.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            m.setVersion(m.getVersion() == null ? 0 : m.getVersion() + 1);
            return m;
        });
    }

    @Test
    void createsMeetingWithNormalizedLinkAndInvitesEachStudent() {
        service.create(request(an.getId(), binh.getId()));

        verify(meetings).saveAndFlush(argThat(m -> m.getMeetUrl().equals("https://meet.google.com/abc-defg-hij")
                && m.getLinkMode().equals("MANUAL")
                && m.getStatus().equals("SCHEDULED")
                && m.getOrganizerId().equals(lecturer.getId())
                && m.getEndAt().equals(m.getStartAt().plusMinutes(45))));
        verify(attendees, times(2)).save(any(MeetingAttendee.class));
        verify(notifier).notify(any(), eq(lecturer), eq(an), eq(Change.SCHEDULED));
        verify(notifier).notify(any(), eq(lecturer), eq(binh), eq(Change.SCHEDULED));
    }

    @Test
    void refusesToInviteStudentsTheLecturerDoesNotSupervise() {
        assertThatThrownBy(() -> service.create(request(an.getId(), outsider.getId())))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(notifier);
        verify(meetings, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStartTimeInThePast() {
        var past = new MeetingRequest("Họp", null, OffsetDateTime.now().minusHours(1), 30,
                "meet.google.com/abc-defg-hij", List.of(an.getId()));
        assertThatThrownBy(() -> service.create(past)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateInvitesNewcomersNotifiesRemovedAndUpdatesTheRest() {
        Meeting existing = scheduledMeeting(lecturer.getId());
        when(meetings.findById(existing.getId())).thenReturn(Optional.of(existing));
        MeetingAttendee anRow = attendee(existing, an);
        when(attendees.findByMeetingId(existing.getId())).thenReturn(List.of(anRow));

        service.update(existing.getId(), request(binh.getId()));

        verify(attendees).delete(anRow);
        verify(notifier).notify(existing, lecturer, an, Change.CANCELLED);
        verify(notifier).notify(existing, lecturer, binh, Change.SCHEDULED);
        verify(notifier, never()).notify(any(), any(), any(), eq(Change.UPDATED));
    }

    @Test
    void onlyTheOrganizerCanCancel() {
        Meeting other = scheduledMeeting(UUID.randomUUID());
        when(meetings.findById(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.cancel(other.getId(), new CancelRequest("x")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(other.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void cancelMarksMeetingAndNotifiesEveryAttendee() {
        Meeting existing = scheduledMeeting(lecturer.getId());
        when(meetings.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(attendees.findByMeetingId(existing.getId())).thenReturn(List.of(attendee(existing, an), attendee(existing, binh)));

        service.cancel(existing.getId(), new CancelRequest("  Trùng lịch  "));

        assertThat(existing.getStatus()).isEqualTo("CANCELLED");
        assertThat(existing.getCancelReason()).isEqualTo("Trùng lịch");
        verify(notifier).notify(existing, lecturer, an, Change.CANCELLED);
        verify(notifier).notify(existing, lecturer, binh, Change.CANCELLED);
    }

    @Test
    void cancelledMeetingCannotBeEdited() {
        Meeting existing = scheduledMeeting(lecturer.getId());
        existing.setStatus("CANCELLED");
        when(meetings.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), request(an.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void attendanceOnlyAfterStartAndOnlyForInvitedPeople() {
        Meeting existing = scheduledMeeting(lecturer.getId());
        when(meetings.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(attendees.findByMeetingId(existing.getId())).thenReturn(List.of(attendee(existing, an)));
        var present = new AttendanceRequest(List.of(new AttendanceItem(an.getId(), "PRESENT")));

        assertThatThrownBy(() -> service.recordAttendance(existing.getId(), present))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("sau khi");

        existing.setStartAt(OffsetDateTime.now().minusMinutes(10));
        var stranger = new AttendanceRequest(List.of(new AttendanceItem(outsider.getId(), "PRESENT")));
        assertThatThrownBy(() -> service.recordAttendance(existing.getId(), stranger))
                .isInstanceOf(BadRequestException.class);

        service.recordAttendance(existing.getId(), present);
        verify(attendees).saveAll(argThat(rows -> rows.iterator().next().getAttendance().equals("PRESENT")));
    }

    @Test
    void studentListsOnlyOwnMeetingsWithoutClassmateDetails() {
        UUID student = an.getId();
        signIn(student);
        when(meetings.findForAttendee(eq(student), any(), any())).thenReturn(List.of());

        service.listMine(null, null);

        verify(meetings).findForAttendee(eq(student), any(), any());
        verify(assembler).toViews(List.of(), false);
    }

    private MeetingRequest request(UUID... ids) {
        return new MeetingRequest(" Báo cáo tuần 3 ", "  ", OffsetDateTime.now().plusDays(1), 45,
                "meet.google.com/abc-defg-hij", List.of(ids));
    }

    private void signIn(UUID id) {
        CustomUserDetail principal = mock(CustomUserDetail.class);
        when(principal.getId()).thenReturn(id);
        when(guard.currentUser()).thenReturn(principal);
    }

    private static Meeting scheduledMeeting(UUID organizerId) {
        OffsetDateTime start = OffsetDateTime.now().plusDays(2);
        return Meeting.builder().id(UUID.randomUUID()).organizerId(organizerId).status("SCHEDULED")
                .contextType("SUPERVISION").linkMode("MANUAL").title("Họp").startAt(start).endAt(start.plusMinutes(30))
                .meetUrl("https://meet.google.com/abc-defg-hij").version(0).build();
    }

    private static MeetingAttendee attendee(Meeting meeting, User user) {
        return MeetingAttendee.builder().meetingId(meeting.getId()).userId(user.getId()).attendance("UNKNOWN").build();
    }

    private static User user(String name) {
        User u = User.builder().fullName(name).email(name + "@x.vn").build();
        u.setId(UUID.randomUUID());
        return u;
    }
}
