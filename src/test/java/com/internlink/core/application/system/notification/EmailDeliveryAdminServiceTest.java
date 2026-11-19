package com.internlink.core.application.system.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

class EmailDeliveryAdminServiceTest {
    final EmailOutboxStore store = mock(EmailOutboxStore.class);
    final SecurityGuard guard = mock(SecurityGuard.class);
    final EmailDeliveryAdminService service =
            new EmailDeliveryAdminService(store, new EmailProperties(), guard, mock(AuditLogService.class));
    final UUID userId = UUID.randomUUID();

    private void signIn(UserRole role) {
        CustomUserDetail user = mock(CustomUserDetail.class);
        when(user.getId()).thenReturn(userId);
        when(user.getRole()).thenReturn(role);
        when(guard.currentUser()).thenReturn(user);
    }

    private EmailDeliveryFilter pageFilter() {
        var filter = ArgumentCaptor.forClass(EmailDeliveryFilter.class);
        service.page("", "", "", "", "", 0, 20);
        verify(store).page(filter.capture(), eq(0), eq(20));
        return filter.getValue();
    }

    @Test
    void facultyOnlySeesOwnDepartment() {
        signIn(UserRole.FACULTY_ADMIN);
        assertThat(pageFilter().departmentViewerId()).isEqualTo(userId);
        service.summary();
        verify(store).summary(userId);
        verify(store).metrics(userId);
    }

    @Test
    void adminSeesWholeSystem() {
        signIn(UserRole.ADMIN);
        assertThat(pageFilter().departmentViewerId()).isNull();
        service.summary();
        verify(store).summary(null);
    }

    @Test
    void facultyCannotRetryEmailOutsideDepartment() {
        signIn(UserRole.FACULTY_ADMIN);
        UUID id = UUID.randomUUID();
        when(store.visibleTo(id, userId)).thenReturn(false);
        assertThatThrownBy(() -> service.retry(id, true, "Gửi lại"))
                .isInstanceOf(ForbiddenException.class);
        verify(store, never()).retry(any(), anyBoolean());
    }

    @Test
    void facultyCanRetryEmailInDepartment() {
        signIn(UserRole.FACULTY_ADMIN);
        UUID id = UUID.randomUUID();
        when(store.visibleTo(id, userId)).thenReturn(true);
        when(store.retry(id, true)).thenReturn(true);
        service.retry(id, true, "Gửi lại");
        verify(store).retry(id, true);
    }

    @Test
    void facultyCannotChangeGlobalControls() {
        signIn(UserRole.FACULTY_ADMIN);
        doThrow(new ForbiddenException("x")).when(guard).requireRole(UserRole.ADMIN);
        assertThatThrownBy(() -> service.controls(true, 100, "Lý do"))
                .isInstanceOf(ForbiddenException.class);
        verify(store, never()).updateControls(anyBoolean(), anyInt());
    }
}
