package com.internlink.core.application.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantTermInfoRepository;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantTermInfoRepository.Milestone;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantTermInfoRepository.Term;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/**
 * Lịch và quy định chung của các kỳ thực tập Văn phòng Trường đã công bố, dạng dữ liệu công khai cho trợ lý.
 * Mọi người hỏi (kể cả khách) đều nhận cùng một nội dung; không chứa dữ liệu cá nhân.
 */
@Component
@RequiredArgsConstructor
public class AssistantTermInfoProvider {
    static final long TTL_MILLIS = 60_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Map<String, String> STATUS = Map.of(
            "REGISTRATION_OPEN", "đang tìm nơi thực tập", "APPLICATION_OPEN", "đang nhận hồ sơ ứng tuyển",
            "ACTIVE", "đang diễn ra", "EVALUATING", "đang đánh giá", "CLOSED", "đã kết thúc");
    private static final Map<String, String> SEMESTER = Map.of("1", "Học kỳ 1", "HK1", "Học kỳ 1",
            "2", "Học kỳ 2", "HK2", "Học kỳ 2", "3", "Học kỳ hè", "HE", "Học kỳ hè");

    private final AssistantTermInfoRepository repository;
    private final ObjectMapper json;
    private volatile long loadedAt;
    private volatile List<Map<String, Object>> cached = List.of();

    public synchronized List<Map<String, Object>> publishedTerms() {
        long now = System.currentTimeMillis();
        if (loadedAt == 0 || now - loadedAt > TTL_MILLIS) {
            cached = load();
            loadedAt = now;
        }
        return cached;
    }

    private List<Map<String, Object>> load() {
        List<Term> terms = repository.publishedTerms(8);
        List<UUID> ids = terms.stream().map(Term::id).toList();
        Map<UUID, List<String>> forms = repository.activeForms(ids);
        Map<UUID, List<Milestone>> plans = repository.publishedPlans(ids);
        return terms.stream().map(t -> toRow(t, forms.getOrDefault(t.id(), List.of()),
                plans.getOrDefault(t.id(), List.of()))).toList();
    }

    Map<String, Object> toRow(Term term, List<String> forms) {
        return toRow(term, forms, List.of());
    }

    Map<String, Object> toRow(Term term, List<String> forms, List<Milestone> plan) {
        Map<String, Object> row = new HashMap<>();
        row.put("code", term.code());
        row.put("name", term.name());
        row.put("academic_year", term.academicYear());
        row.put("semester", SEMESTER.getOrDefault(term.semester(), term.semester()));
        row.put("status", term.status());
        row.put("status_label", STATUS.getOrDefault(term.status(), term.status()));
        row.put("application_deadline", date(term.applicationDeadline()));
        row.put("internship_period", range(term.startDate(), term.endDate()));
        row.put("evaluation_deadline", date(term.evaluationDeadline()));
        row.put("rules", rules(term.settingsJson()));
        row.put("forms", forms);
        row.put("plan", plan.stream().map(AssistantTermInfoProvider::milestone).toList());
        return row;
    }

    /**
     * Một mốc Kế hoạch TTTT: ngày ISO (giờ VN) để trợ lý tự tính "còn bao nhiêu ngày", kèm cách xử lý quá hạn
     * của mốc hạn nộp Phiếu tiếp nhận.
     */
    static Map<String, Object> milestone(Milestone m) {
        Map<String, Object> row = new HashMap<>();
        row.put("kind", m.kind());
        row.put("label", m.label());
        if (m.note() != null && !m.note().isBlank()) row.put("note", m.note());
        if (m.startAt() != null) row.put("start", m.startAt().atZoneSameInstant(VN).toLocalDate().toString());
        if (m.endAt() != null) row.put("end", m.endAt().atZoneSameInstant(VN).toLocalDate().toString());
        if (m.requiredWeeks() != null) row.put("required_weeks", m.requiredWeeks());
        if ("ACCEPTANCE_FORM_DUE".equals(m.kind())) row.put("late_policy", m.latePolicy());
        return row;
    }

    /** Diễn giải các khoá cấu hình kỳ đã biết; khoá lạ bị bỏ qua để không lộ cấu hình nội bộ. */
    List<String> rules(String settingsJson) {
        Map<String, Object> settings;
        try {
            settings = settingsJson == null ? Map.of() : json.readValue(settingsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
        List<String> rules = new ArrayList<>();
        Object hours = settings.get("requiredHours");
        if (hours != null) rules.add("Số giờ thực tập tối thiểu: " + hours + " giờ");
        Object log = settings.get("weeklyLogFrequency");
        if ("WEEKLY".equals(log)) rules.add("Nhật ký thực tập nộp hằng tuần");
        Object mentor = settings.get("mentorWeight");
        Object lecturer = settings.get("lecturerWeight");
        if (mentor instanceof Number m && lecturer instanceof Number l) {
            rules.add("Tỷ trọng điểm: doanh nghiệp/mentor " + percent(m) + ", giảng viên hướng dẫn " + percent(l));
        }
        Object appeal = settings.get("appealWindowDays");
        if (appeal != null) rules.add("Thời hạn phúc khảo: " + appeal + " ngày sau khi có điểm");
        Object max = settings.get("maxStudents");
        if (max != null) rules.add("Chỉ tiêu tối đa: " + max + " sinh viên");
        return rules;
    }

    private static String percent(Number weight) {
        return Math.round(weight.doubleValue() * 100) + "%";
    }

    private static String range(TemporalAccessor from, TemporalAccessor to) {
        if (from == null || to == null) return null;
        return date(from) + " – " + date(to);
    }

    private static String date(TemporalAccessor value) {
        if (value == null) return null;
        if (value instanceof java.time.OffsetDateTime odt) return DATE.format(odt.atZoneSameInstant(VN));
        return DATE.format(value);
    }
}
