package com.internlink.core.application.announcement;

import com.internlink.core.application.announcement.AnnouncementDtos.*;
import com.internlink.core.application.meeting.MeetingAccessPolicy;
import com.internlink.core.application.meeting.SupervisedStudentDirectory;
import com.internlink.core.application.meeting.SupervisedStudentDirectory.SupervisedStudent;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.announcement.LecturerAnnouncement;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaLecturerAnnouncementRecipientRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaLecturerAnnouncementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnnouncementServiceTest {
    JpaLecturerAnnouncementRepository announcements = mock(JpaLecturerAnnouncementRepository.class);
    JpaLecturerAnnouncementRecipientRepository recipients = mock(JpaLecturerAnnouncementRecipientRepository.class);
    JpaUserRepository users = mock(JpaUserRepository.class);
    SupervisedStudentDirectory directory = mock(SupervisedStudentDirectory.class);
    NotificationService notifications = mock(NotificationService.class);
    SecurityGuard guard = mock(SecurityGuard.class);
    AnnouncementService service = new AnnouncementService(
            announcements, recipients, users, new MeetingAccessPolicy(directory), notifications,
            mock(AuditLogService.class), guard);

    User lecturer = user("GV Sơn");
    User an = user("An");
    User binh = user("Bình");
    User outsider = user("Người ngoài");

    @BeforeEach
    void setUp() {
        signIn(lecturer);
        when(directory.forLecturer(lecturer.getId())).thenReturn(List.of(
                new SupervisedStudent(an.getId(), "An", "an@x.vn", "B1", "HK1"),
                new SupervisedStudent(binh.getId(), "Bình", "binh@x.vn", "B2", "HK1")));
        when(users.findAllById(any())).thenAnswer(call -> {
            Set<UUID> ids = new HashSet<>();
            ((Iterable<UUID>) call.getArgument(0)).forEach(ids::add);
            return List.of(lecturer, an, binh, outsider).stream().filter(u -> ids.contains(u.getId())).toList();
        });
        when(announcements.saveAndFlush(any())).thenAnswer(call -> {
            LecturerAnnouncement a = call.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
    }

    @Test
    void sendsInAppAndEmailToEachSelectedStudent() {
        AnnouncementView view = service.send(new AnnouncementRequest(
                "  Nộp nhật ký tuần 3 ", "Các em nộp trước thứ Sáu.", List.of(an.getId(), binh.getId(), an.getId()), true));

        verify(announcements).saveAndFlush(argThat(a -> a.getTitle().equals("Nộp nhật ký tuần 3")
                && a.getRecipientCount() == 2 && a.isSendEmail() && a.getSenderId().equals(lecturer.getId())));
        verify(recipients).saveAll(argThat(rows -> ((Collection<?>) rows).size() == 2));
        for (User s : List.of(an, binh)) {
            verify(notifications).publishNotification(eq(s.getId()), eq("LECTURER_ANNOUNCEMENT"),
                    eq("Thông báo từ GVHD: Nộp nhật ký tuần 3"),
                    argThat(m -> m.contains("GV Sơn") && m.contains("Các em nộp trước thứ Sáu.")),
                    eq("/student/announcements"), eq(true));
        }
        assertThat(view.recipients()).extracting(Recipient::fullName).containsExactly("An", "Bình");
    }

    @Test
    void inAppOnlyWhenEmailNotRequested() {
        service.send(new AnnouncementRequest("Họp nhóm", "Nội dung", List.of(an.getId()), false));

        verify(notifications).publishNotification(eq(an.getId()), any(), any(), any(), any(), eq(false));
    }

    @Test
    void rejectsStudentsTheLecturerDoesNotSupervise() {
        assertThatThrownBy(() -> service.send(new AnnouncementRequest(
                "X", "Y", List.of(an.getId(), outsider.getId()), true)))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(notifications);
        verify(announcements, never()).saveAndFlush(any());
    }

    @Test
    void studentListShowsSenderButHidesRecipients() {
        signIn(an);
        LecturerAnnouncement a = LecturerAnnouncement.builder().id(UUID.randomUUID()).senderId(lecturer.getId())
                .title("T").body("B").recipientCount(2).build();
        when(announcements.findForRecipient(eq(an.getId()), any())).thenReturn(List.of(a));

        List<AnnouncementView> mine = service.listMine();

        assertThat(mine).singleElement().satisfies(v -> {
            assertThat(v.senderName()).isEqualTo("GV Sơn");
            assertThat(v.recipients()).isEmpty();
        });
    }

    private void signIn(User u) {
        CustomUserDetail principal = mock(CustomUserDetail.class);
        when(principal.getId()).thenReturn(u.getId());
        when(principal.getFullName()).thenReturn(u.getFullName());
        when(guard.currentUser()).thenReturn(principal);
    }

    private static User user(String name) {
        User u = User.builder().fullName(name).email(name + "@x.vn").build();
        u.setId(UUID.randomUUID());
        return u;
    }
}
