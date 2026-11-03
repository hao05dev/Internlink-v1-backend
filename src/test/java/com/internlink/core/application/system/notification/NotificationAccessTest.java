package com.internlink.core.application.system.notification;
import com.internlink.core.application.system.impl.NotificationServiceImpl;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.system.*;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class NotificationAccessTest {
    JpaNotificationRepository repository=mock(JpaNotificationRepository.class);
    JpaUserRepository users=mock(JpaUserRepository.class);
    EmailOutboxService outbox=mock(EmailOutboxService.class);
    NotificationServiceImpl service=new NotificationServiceImpl(repository,users,outbox);
    @ParameterizedTest @EnumSource(UserRole.class)
    void eachRoleReceivesNotificationAfterFlushingParent(UserRole role) {
        User user=User.builder().role(role).isActive(true).build(); user.setId(UUID.randomUUID());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(any())).thenAnswer(call->{ Notification n=call.getArgument(0); n.setId(UUID.randomUUID()); return n; });
        when(outbox.enqueue(any())).thenReturn("QUEUED");
        service.publishNotification(user.getId(),"SYSTEM_UPDATE","Cập nhật","Nội dung","//example.com",true);
        var ordered=inOrder(repository,outbox);
        ordered.verify(repository).saveAndFlush(any());
        ordered.verify(outbox).enqueue(argThat(n->n.getId()!=null && n.getActionUrl().equals(NotificationLinks.home(role))));
        ordered.verify(repository).save(argThat(n->"QUEUED".equals(n.getDeliveryStatus().get("email"))));
    }
    @Test void genericNotificationDoesNotEnqueueEmail() {
        User user=User.builder().role(UserRole.STUDENT).isActive(true).build();
        user.setId(UUID.randomUUID());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(any())).thenAnswer(call->{
            Notification n=call.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        service.sendNotification(user.getId(),"APPLICATION_SUBMITTED","Đã ứng tuyển","Nội dung","/student/applications");

        verifyNoInteractions(outbox);
        verify(repository).save(argThat(n->"SEPARATE_NOTICE".equals(n.getDeliveryStatus().get("email"))));
    }
    @Test void anotherRecipientCannotMarkRead() {
        var owner=User.builder().build(); owner.setId(UUID.randomUUID());
        var n=Notification.builder().id(UUID.randomUUID()).recipient(owner).build();
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));
        assertThatThrownBy(()->service.markAsRead(n.getId(),UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
        verify(repository,never()).save(any());
    }
    @Test void anotherRecipientCannotViewDetail() {
        var owner=User.builder().build(); owner.setId(UUID.randomUUID());
        var n=Notification.builder().id(UUID.randomUUID()).recipient(owner).build();
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));
        assertThatThrownBy(()->service.getById(n.getId(),UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
    }
    @Test void ownerViewsDetailWithoutMarkingRead() {
        var owner=User.builder().build(); owner.setId(UUID.randomUUID());
        var n=Notification.builder().id(UUID.randomUUID()).recipient(owner).notificationType("LOGBOOK_APPROVED")
            .title("Nhật ký đã duyệt").message("Nội dung").actionUrl("/student/weekly-logs").isRead(false).build();
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));
        var detail=service.getById(n.getId(),owner.getId());
        assertThat(detail.getId()).isEqualTo(n.getId());
        assertThat(detail.getActionUrl()).isEqualTo("/student/weekly-logs");
        assertThat(detail.getIsRead()).isFalse();
        verify(repository,never()).save(any());
    }
    @Test void repeatedReadPreservesOriginalTimestamp() {
        var owner=User.builder().build(); owner.setId(UUID.randomUUID()); var timestamp=OffsetDateTime.now().minusDays(1);
        var n=Notification.builder().id(UUID.randomUUID()).recipient(owner).notificationType("SYSTEM_TEST").isRead(true).readAt(timestamp).build();
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));
        assertThat(service.markAsRead(n.getId(),owner.getId()).getReadAt()).isEqualTo(timestamp);
        verify(repository,never()).save(any());
    }
    @ParameterizedTest @EnumSource(UserRole.class)
    void preferencesAreAlwaysOwnedByPrincipal(UserRole role) {
        var prefs=mock(JpaNotificationPreferenceRepository.class); var guard=mock(SecurityGuard.class);
        var settings=new EmailProperties(); var user=User.builder().role(role).build(); user.setId(UUID.randomUUID());
        when(guard.currentUser()).thenReturn(com.internlink.core.infrastructure.security.CustomUserDetail.create(user));
        when(prefs.save(any())).thenAnswer(call->call.getArgument(0));
        var preferencesService=new NotificationPreferenceService(prefs,guard,settings);
        var result=preferencesService.update(new NotificationPreferenceService.Preferences(false,true,true,false,true,true));
        verify(prefs).save(argThat(p->user.getId().equals(p.getUserId()) && !p.isEmailEnabled()));
        assertThat(result.emailServiceEnabled()).isFalse();
    }
    @Test void announcementEmailRequiresOptIn() {
        assertThat(NotificationPolicy.wantsEmail(NotificationPreference.builder().build(),"NEW_JOB_POSTED")).isFalse();
        assertThat(NotificationPolicy.wantsEmail(NotificationPreference.builder().build(),"APPLICATION_SUBMITTED")).isTrue();
        assertThat(NotificationPolicy.wantsEmail(NotificationPreference.builder().emailEnabled(false).build(),"SYSTEM_ALERT")).isFalse();
    }
}
