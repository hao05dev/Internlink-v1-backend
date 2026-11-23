package com.internlink.core.application.assistant;

import com.internlink.core.domain.placement.InternshipDurationRule;
import com.internlink.core.infrastructure.persistence.jdbc.StudentInternshipSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Diễn giải tiến độ thực tập của chính sinh viên thành các dòng "khoá/nhãn: giá trị" cho trợ lý.
 * Khoá (identity, lecturer, mentor…) giúp trợ lý chỉ trả lời đúng dòng được hỏi.
 */
@Service
@RequiredArgsConstructor
public class AssistantStudentStatusService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> APPLICATION = Map.of(
            "SUBMITTED", "đã nộp", "REVIEWING", "đang xét", "INTERVIEWING", "đang phỏng vấn",
            "OFFERED", "được mời nhận", "REJECTED", "không đạt", "WITHDRAWN", "đã rút");
    private static final Map<String, String> PLACEMENT = Map.of(
            "PREPARING", "đang chuẩn bị", "ACTIVE", "đang thực tập", "PAUSED", "tạm dừng",
            "COMPLETED", "đã hoàn thành", "TERMINATED", "đã chấm dứt", "TRANSFERRED", "đã chuyển nơi");
    private static final Map<String, String> LOGBOOK = Map.of(
            "DRAFT", "nháp", "SUBMITTED", "chờ mentor duyệt", "APPROVED_BY_MENTOR", "mentor đã duyệt",
            "APPROVED_BY_LECTURER", "giảng viên đã duyệt", "REVISION_REQUESTED", "cần sửa");

    private final StudentInternshipSnapshotRepository snapshots;

    public record Snapshot(List<Map<String, String>> items, List<String> ownIdentifiers) {}

    @Transactional(readOnly = true)
    public Snapshot forStudent(UUID userId) {
        List<Map<String, String>> items = new ArrayList<>();
        Set<String> own = new LinkedHashSet<>();
        snapshots.profile(userId).ifPresent(profile -> {
            String who = profile.fullName();
            if (profile.studentCode() != null) who += " — MSSV " + profile.studentCode();
            if (profile.programName() != null) who += ", ngành " + profile.programName();
            items.add(item("identity", "Bạn là", who));
            addIfPresent(own, profile.email());
            addIfPresent(own, profile.studentCode());
        });
        Optional<StudentInternshipSnapshotRepository.Enrollment> enrollment = snapshots.latestEnrollment(userId);
        enrollment.ifPresentOrElse(e -> {
            items.add(item("term", "Đợt thực tập", e.termName()));
            Integer credits = e.internshipCredits();
            if (credits != null && (credits == 2 || credits == 3 || credits == 5)) {
                // Trợ lý dùng số tuần để chọn đúng mốc kế hoạch dành cho học phần 8 hoặc 12 tuần.
                items.add(item("course", "Học phần thực tập", credits + " tín chỉ — "
                        + InternshipDurationRule.requiredWeeks(credits) + " tuần"));
            }
            items.add(item("letter", "Giấy giới thiệu", e.letterReceived()
                    ? "Doanh nghiệp đã nhận giấy giới thiệu" : "Chưa ghi nhận giấy giới thiệu"));
            addIfPresent(own, e.studentCode());
        }, () -> items.add(item("term", "Đợt thực tập", "Chưa có tên trong danh sách đợt nào")));
        Map<String, Long> applications = snapshots.applicationCounts(userId);
        items.add(item("applications", "Hồ sơ ứng tuyển",
                applications.isEmpty() ? "Chưa nộp hồ sơ nào" : describe(applications, APPLICATION)));
        Optional<StudentInternshipSnapshotRepository.Placement> placement = snapshots.latestPlacement(userId);
        placement.ifPresentOrElse(p -> {
            items.add(item("placement", "Nơi thực tập", p.companyName() + " — "
                    + PLACEMENT.getOrDefault(p.status(), p.status())));
            if (p.startDate() != null && p.endDate() != null) {
                items.add(item("period", "Thời gian", DATE.format(p.startDate()) + " – " + DATE.format(p.endDate())));
            }
            Map<String, Long> logbooks = snapshots.logbookCounts(p.id());
            items.add(item("logbook", "Nhật ký tuần", logbooks.isEmpty() ? "Chưa có tuần nào" : describe(logbooks, LOGBOOK)));
        }, () -> items.add(item("placement", "Nơi thực tập", "Chưa được xác nhận nơi thực tập")));

        // GVHD: ưu tiên người ghi trên nơi thực tập, sau đó tới phân công trong danh sách đợt.
        // Tên và email luôn lấy cùng một nguồn để không ghép nhầm hai người.
        boolean fromPlacement = placement.map(p -> p.lecturerName() != null).orElse(false);
        String lecturerName = fromPlacement ? placement.get().lecturerName()
                : enrollment.map(StudentInternshipSnapshotRepository.Enrollment::lecturerName).orElse(null);
        String lecturerEmail = fromPlacement ? placement.get().lecturerEmail()
                : enrollment.map(StudentInternshipSnapshotRepository.Enrollment::lecturerEmail).orElse(null);
        items.add(item("lecturer", "Giảng viên hướng dẫn", lecturerName == null
                ? "Chưa được phân công giảng viên hướng dẫn" : withEmail(lecturerName, lecturerEmail)));
        addIfPresent(own, lecturerEmail);
        String mentorName = placement.map(StudentInternshipSnapshotRepository.Placement::mentorName).orElse(null);
        String mentorEmail = placement.map(StudentInternshipSnapshotRepository.Placement::mentorEmail).orElse(null);
        items.add(item("mentor", "Mentor doanh nghiệp", mentorName != null ? withEmail(mentorName, mentorEmail)
                : placement.isPresent() ? "Nơi thực tập này chưa có mentor trên hệ thống"
                : "Chưa có (mentor được gán khi bạn được xác nhận nơi thực tập)"));
        addIfPresent(own, mentorEmail);
        return new Snapshot(items, List.copyOf(own));
    }

    private static String withEmail(String name, String email) {
        return email == null || email.isBlank() ? name : name + " (" + email + ")";
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isBlank()) target.add(value);
    }

    private static String describe(Map<String, Long> counts, Map<String, String> labels) {
        return counts.entrySet().stream()
                .map(e -> e.getValue() + " " + labels.getOrDefault(e.getKey(), e.getKey().toLowerCase()))
                .collect(Collectors.joining(", "));
    }

    private static Map<String, String> item(String key, String label, String value) {
        return Map.of("key", key, "label", label, "value", value == null || value.isBlank() ? "—" : value);
    }
}
