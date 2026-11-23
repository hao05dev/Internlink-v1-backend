package com.internlink.core.application.placement;

import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.TermMilestone;
import com.internlink.core.domain.placement.InternshipDurationRule;
import com.internlink.core.infrastructure.persistence.jpa.JpaTermMilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Hạn nộp Phiếu tiếp nhận lấy từ Kế hoạch TTTT đã công bố (mốc ACCEPTANCE_FORM_DUE).
 * Mặc định quá hạn vẫn được nộp và bị đánh dấu trễ; VP Trường có thể chọn khóa nộp.
 */
@Component
@RequiredArgsConstructor
public class AcceptanceFormDeadline {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JpaTermMilestoneRepository milestones;

    public enum Outcome { ON_TIME, LATE, LOCKED }

    public record Decision(Outcome outcome, OffsetDateTime dueAt) {
        static final Decision NO_DEADLINE = new Decision(Outcome.ON_TIME, null);

        public String dueDay() { return dueAt == null ? null : DAY.format(dueAt); }
    }

    public Decision check(InternshipTerm term, Integer internshipCredits, OffsetDateTime now) {
        if (term.getPlanPublishedAt() == null) return Decision.NO_DEADLINE;
        return decide(milestones.findByTermIdOrderBySortOrderAscStartAtAscEndAtAsc(term.getId()),
            InternshipDurationRule.requiredWeeks(internshipCredits), now);
    }

    /**
     * Mốc riêng cho học phần của SV (8/12 tuần) được ưu tiên hơn mốc chung "Mọi SV".
     * Hạn tính hết phút cuối (form lưu 23:59) nên nộp lúc 23:59:30 vẫn đúng hạn.
     */
    static Decision decide(List<TermMilestone> plan, Integer weeks, OffsetDateTime now) {
        var due = plan.stream()
            .filter(m -> TermMilestone.ACCEPTANCE_FORM_DUE.equals(m.getKind()) && m.getEndAt() != null)
            .filter(m -> m.getRequiredWeeks() == null || Objects.equals(m.getRequiredWeeks(), weeks))
            .max(Comparator.comparing((TermMilestone m) -> m.getRequiredWeeks() != null)
                .thenComparing(TermMilestone::getEndAt));
        if (due.isEmpty()) return Decision.NO_DEADLINE;
        var milestone = due.get();
        OffsetDateTime dueAt = milestone.getEndAt();
        if (!now.isAfter(dueAt.plusMinutes(1))) return new Decision(Outcome.ON_TIME, dueAt);
        return new Decision(TermMilestone.LOCK.equals(milestone.getLatePolicy()) ? Outcome.LOCKED : Outcome.LATE, dueAt);
    }
}
