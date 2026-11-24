package com.internlink.core.application.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantTermInfoRepository;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantTermInfoRepository.Milestone;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantTermInfoRepository.Term;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AssistantTermInfoProviderTest {
    private final AssistantTermInfoRepository repository = mock(AssistantTermInfoRepository.class);
    private final AssistantTermInfoProvider provider = new AssistantTermInfoProvider(repository, new ObjectMapper());

    private static Term term(String settings) {
        return new Term(UUID.randomUUID(), "HE-2026", "Học kỳ Hè 2026-2027", "2026-2027", "3", "APPLICATION_OPEN", null,
                LocalDate.of(2026, 11, 15), LocalDate.of(2027, 2, 13), null, settings);
    }

    @Test
    void rowUsesVietnameseLabelsAndLocalDates() {
        Map<String, Object> row = provider.toRow(term(null), List.of("M-TT-01 (bắt buộc)"));
        assertThat(row.get("semester")).isEqualTo("Học kỳ hè");
        assertThat(row.get("status_label")).isEqualTo("đang nhận hồ sơ ứng tuyển");
        assertThat(row.get("internship_period")).isEqualTo("15/11/2026 – 13/02/2027");
        assertThat(row).doesNotContainKey("registration"); // hạn đăng ký học phần do hệ thống của Trường lo
        assertThat(row.get("application_deadline")).isNull();
        assertThat(row.get("forms")).isEqualTo(List.of("M-TT-01 (bắt buộc)"));
    }

    @Test
    void planMilestonesUseVietnamDatesAndKeepLatePolicyOnlyForAcceptanceDeadline() {
        var due = new Milestone("ACCEPTANCE_FORM_DUE", "Hạn nộp Phiếu tiếp nhận", null, null,
                OffsetDateTime.of(2026, 12, 5, 16, 59, 0, 0, ZoneOffset.UTC), 8, "LOCK"); // = 23:59 05/12 giờ VN
        var briefing = new Milestone("PROCESS_BRIEFING", "Phổ biến quy trình", "Hội trường 1",
                OffsetDateTime.of(2026, 11, 19, 17, 30, 0, 0, ZoneOffset.UTC), null, null, "ALLOW_LATE"); // = 00:30 20/11

        @SuppressWarnings("unchecked")
        var plan = (List<Map<String, Object>>) provider.toRow(term(null), List.of(), List.of(due, briefing)).get("plan");

        assertThat(plan.get(0)).containsEntry("end", "2026-12-05").containsEntry("required_weeks", 8)
                .containsEntry("late_policy", "LOCK").doesNotContainKey("start");
        assertThat(plan.get(1)).containsEntry("start", "2026-11-20").containsEntry("note", "Hội trường 1")
                .doesNotContainKey("late_policy");
    }

    @Test
    void onlyKnownSettingsBecomeRules() {
        List<String> rules = provider.rules("""
                {"requiredHours":320,"weeklyLogFrequency":"WEEKLY","mentorWeight":0.6,"lecturerWeight":0.4,
                 "appealWindowDays":7,"internalSecret":"x"}""");
        assertThat(rules).containsExactly(
                "Số giờ thực tập tối thiểu: 320 giờ",
                "Nhật ký thực tập nộp hằng tuần",
                "Tỷ trọng điểm: doanh nghiệp/mentor 60%, giảng viên hướng dẫn 40%",
                "Thời hạn phúc khảo: 7 ngày sau khi có điểm");
        assertThat(String.join(" ", rules)).doesNotContain("internalSecret");
    }

    @Test
    void brokenSettingsAreIgnored() {
        assertThat(provider.rules("{not json")).isEmpty();
        assertThat(provider.rules(null)).isEmpty();
    }

    @Test
    void resultIsCachedBetweenCalls() {
        when(repository.publishedTerms(anyInt())).thenReturn(List.of(term("{}")));
        when(repository.activeForms(any())).thenReturn(Map.of());
        provider.publishedTerms();
        provider.publishedTerms();
        verify(repository, times(1)).publishedTerms(anyInt());
    }
}
