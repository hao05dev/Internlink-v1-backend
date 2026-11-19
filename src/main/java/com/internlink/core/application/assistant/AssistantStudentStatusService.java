package com.internlink.core.application.assistant;

import com.internlink.core.infrastructure.persistence.jdbc.StudentInternshipSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Diễn giải tiến độ thực tập của chính sinh viên thành các dòng "nhãn: giá trị" cho trợ lý. */
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
        List<String> own = new ArrayList<>();
        snapshots.latestEnrollment(userId).ifPresentOrElse(enrollment -> {
            items.add(item("Đợt thực tập", enrollment.termName()));
            items.add(item("Giấy giới thiệu", enrollment.letterReceived()
                    ? "Doanh nghiệp đã nhận giấy giới thiệu" : "Chưa ghi nhận giấy giới thiệu"));
            if (enrollment.studentCode() != null) own.add(enrollment.studentCode());
        }, () -> items.add(item("Đợt thực tập", "Chưa có tên trong danh sách đợt nào")));
        Map<String, Long> applications = snapshots.applicationCounts(userId);
        items.add(item("Hồ sơ ứng tuyển", applications.isEmpty() ? "Chưa nộp hồ sơ nào" : describe(applications, APPLICATION)));
        snapshots.latestPlacement(userId).ifPresentOrElse(placement -> {
            items.add(item("Nơi thực tập", placement.companyName() + " — "
                    + PLACEMENT.getOrDefault(placement.status(), placement.status())));
            if (placement.startDate() != null && placement.endDate() != null) {
                items.add(item("Thời gian", DATE.format(placement.startDate()) + " – " + DATE.format(placement.endDate())));
            }
            Map<String, Long> logbooks = snapshots.logbookCounts(placement.id());
            items.add(item("Nhật ký tuần", logbooks.isEmpty() ? "Chưa có tuần nào" : describe(logbooks, LOGBOOK)));
        }, () -> items.add(item("Nơi thực tập", "Chưa được xác nhận nơi thực tập")));
        return new Snapshot(items, own);
    }

    private static String describe(Map<String, Long> counts, Map<String, String> labels) {
        return counts.entrySet().stream()
                .map(e -> e.getValue() + " " + labels.getOrDefault(e.getKey(), e.getKey().toLowerCase()))
                .collect(Collectors.joining(", "));
    }

    private static Map<String, String> item(String label, String value) {
        return Map.of("label", label, "value", value == null ? "" : value);
    }
}
