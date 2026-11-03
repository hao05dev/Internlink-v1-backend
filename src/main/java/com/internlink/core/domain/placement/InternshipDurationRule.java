package com.internlink.core.domain.placement;

import com.internlink.core.shared.exception.BadRequestException;
import java.time.LocalDate;

/**
 * Thời lượng thực tập theo số tín chỉ học phần:
 * 2-3 TC (Thực tập thực tế) = 8 tuần, 5 TC (Thực tập doanh nghiệp) = 12 tuần.
 */
public final class InternshipDurationRule {
    /** Cho phép tuần cuối kết thúc vào thứ Sáu. */
    private static final int LAST_WEEK_SLACK_DAYS = 3;

    private InternshipDurationRule() {}

    public static Integer requiredWeeks(Integer credits) {
        if (credits == null) return null;
        return switch (credits) {
            case 2, 3 -> 8;
            case 5 -> 12;
            default -> throw new BadRequestException("Số tín chỉ thực tập chỉ nhận 2, 3 hoặc 5");
        };
    }

    public static void requireValidCredits(Integer credits) {
        requiredWeeks(credits);
    }

    public static void requireDuration(LocalDate start, LocalDate end, Integer credits) {
        Integer weeks = requiredWeeks(credits);
        if (weeks == null || start == null || end == null) return;
        if (end.isBefore(start.plusWeeks(weeks).minusDays(LAST_WEEK_SLACK_DAYS)))
            throw new BadRequestException("Học phần " + credits + " tín chỉ yêu cầu thực tập tối thiểu " + weeks + " tuần");
    }
}
