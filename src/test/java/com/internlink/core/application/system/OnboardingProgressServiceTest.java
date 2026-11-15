package com.internlink.core.application.system;

import com.internlink.core.domain.system.OnboardingProgress;
import com.internlink.core.infrastructure.persistence.jpa.JpaOnboardingProgressRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnboardingProgressServiceTest {
    JpaOnboardingProgressRepository repository = mock(JpaOnboardingProgressRepository.class);
    SecurityGuard guard = mock(SecurityGuard.class);
    OnboardingProgressService service = new OnboardingProgressService(repository, guard);
    UUID me = UUID.randomUUID();

    @BeforeEach
    void signIn() {
        CustomUserDetail user = mock(CustomUserDetail.class);
        when(user.getId()).thenReturn(me);
        when(guard.currentUser()).thenReturn(user);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void savesProgressForCurrentUserOnly() {
        var result = service.save("welcome", new OnboardingProgressService.UpdateRequest(2, "COMPLETED", 4));

        verify(repository).save(argThat(row -> row.getUserId().equals(me)
                && row.getTourKey().equals("welcome")
                && row.getTourVersion() == 2
                && row.getStatus().equals("COMPLETED")
                && row.getLastStep() == 4
                && row.getUpdatedAt() != null));
        assertThat(result.tourKey()).isEqualTo("welcome");
        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    void listsOnlyCurrentUsersRows() {
        var row = OnboardingProgress.builder().userId(me).tourKey("daily-logs").tourVersion(1)
                .status("DISMISSED").updatedAt(OffsetDateTime.now()).build();
        when(repository.findByUserIdOrderByTourKey(me)).thenReturn(List.of(row));

        assertThat(service.list()).extracting(OnboardingProgressService.Progress::tourKey).containsExactly("daily-logs");
        verify(repository).findByUserIdOrderByTourKey(me);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Welcome", "../etc", "a b", "-start"})
    void rejectsInvalidTourKeys(String key) {
        var request = new OnboardingProgressService.UpdateRequest(1, "COMPLETED", null);
        assertThatThrownBy(() -> service.save(key, request)).isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }
}
