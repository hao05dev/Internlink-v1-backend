package com.internlink.core.application.assistant;

import com.internlink.core.infrastructure.persistence.jdbc.StudentInternshipSnapshotRepository;
import com.internlink.core.infrastructure.persistence.jdbc.StudentInternshipSnapshotRepository.Enrollment;
import com.internlink.core.infrastructure.persistence.jdbc.StudentInternshipSnapshotRepository.Placement;
import com.internlink.core.infrastructure.persistence.jdbc.StudentInternshipSnapshotRepository.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AssistantStudentStatusServiceTest {
    @Mock StudentInternshipSnapshotRepository snapshots;
    @InjectMocks AssistantStudentStatusService service;

    private final UUID userId = UUID.randomUUID();

    private Map<String, String> byKey(AssistantStudentStatusService.Snapshot snapshot) {
        return snapshot.items().stream().collect(Collectors.toMap(i -> i.get("key"), i -> i.get("value")));
    }

    private void base(Optional<Enrollment> enrollment, Optional<Placement> placement) {
        when(snapshots.profile(userId)).thenReturn(Optional.of(
                new Profile("Nguyễn Văn An", "an@student.ctu.edu.vn", "B2100001", "Kỹ thuật phần mềm")));
        when(snapshots.latestEnrollment(userId)).thenReturn(enrollment);
        when(snapshots.applicationCounts(userId)).thenReturn(Map.of());
        when(snapshots.latestPlacement(userId)).thenReturn(placement);
        lenient().when(snapshots.logbookCounts(any())).thenReturn(Map.of());
    }

    @Test
    void identityAndSupervisorsComeFromPlacement() {
        base(Optional.of(new Enrollment("HK hè", "B2100001", true, "Roster GV", "roster@ctu.edu.vn")),
                Optional.of(new Placement(UUID.randomUUID(), "ABC", "ACTIVE", LocalDate.of(2026, 11, 15),
                        LocalDate.of(2027, 2, 13), "ThS. Sơn", "son@ctu.edu.vn", "Lê Thị Hoa", "hoa@abc.vn")));

        var snapshot = service.forStudent(userId);
        Map<String, String> items = byKey(snapshot);

        assertThat(items.get("identity")).isEqualTo("Nguyễn Văn An — MSSV B2100001, ngành Kỹ thuật phần mềm");
        assertThat(items.get("lecturer")).isEqualTo("ThS. Sơn (son@ctu.edu.vn)");
        assertThat(items.get("mentor")).isEqualTo("Lê Thị Hoa (hoa@abc.vn)");
        assertThat(items.get("period")).isEqualTo("15/11/2026 – 13/02/2027");
        assertThat(snapshot.ownIdentifiers())
                .containsExactly("an@student.ctu.edu.vn", "B2100001", "son@ctu.edu.vn", "hoa@abc.vn");
    }

    @Test
    void courseLengthIsSharedSoTheAssistantPicksTheRightMilestone() {
        base(Optional.of(new Enrollment("HK hè", "B2100001", false, null, null, 5)), Optional.empty());

        assertThat(byKey(service.forStudent(userId)).get("course")).isEqualTo("5 tín chỉ — 12 tuần");
    }

    @Test
    void lecturerFallsBackToRosterWithMatchingEmail() {
        base(Optional.of(new Enrollment("HK hè", "B2100001", false, "TS. Roster", "roster@ctu.edu.vn")),
                Optional.of(new Placement(UUID.randomUUID(), "ABC", "PREPARING", null, null,
                        null, "other@ctu.edu.vn", null, null)));

        Map<String, String> items = byKey(service.forStudent(userId));

        assertThat(items.get("lecturer")).isEqualTo("TS. Roster (roster@ctu.edu.vn)");
        assertThat(items.get("mentor")).isEqualTo("Nơi thực tập này chưa có mentor trên hệ thống");
    }

    @Test
    void nothingAssignedYet() {
        base(Optional.empty(), Optional.empty());

        var snapshot = service.forStudent(userId);
        Map<String, String> items = byKey(snapshot);

        assertThat(items.get("lecturer")).isEqualTo("Chưa được phân công giảng viên hướng dẫn");
        assertThat(items.get("mentor")).startsWith("Chưa có");
        assertThat(items.get("placement")).isEqualTo("Chưa được xác nhận nơi thực tập");
        assertThat(snapshot.ownIdentifiers()).containsExactly("an@student.ctu.edu.vn", "B2100001");
    }
}
