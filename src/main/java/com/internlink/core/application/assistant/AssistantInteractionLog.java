package com.internlink.core.application.assistant;

import com.internlink.core.infrastructure.persistence.jdbc.AssistantInteractionRepository;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantInteractionRepository.Entry;
import com.internlink.core.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ghi nhật ký hỏi đáp ẩn danh và nhận đánh giá 👍/👎.
 * Không lưu ID người hỏi; MSSV, email, số điện thoại trong câu hỏi bị che trước khi ghi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantInteractionLog {
    private static final Pattern STUDENT_ID = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]\\d{7}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?84|0)(?:[\\s.-]?\\d){9,10}(?!\\d)");
    private static final int MAX_QUESTION = 1000;
    private static final int MAX_NOTE = 500;

    private final AssistantInteractionRepository repository;

    static String anonymize(String text) {
        if (text == null) return "";
        String masked = EMAIL.matcher(text.strip()).replaceAll("[email]");
        masked = STUDENT_ID.matcher(masked).replaceAll("[MSSV]");
        masked = PHONE.matcher(masked).replaceAll("[SĐT]");
        return masked.length() > MAX_QUESTION ? masked.substring(0, MAX_QUESTION) : masked;
    }

    /** Ghi một lượt hỏi đáp; lỗi ghi nhật ký không được làm hỏng câu trả lời nên chỉ trả null. */
    public UUID record(String question, String audience, Map<String, Object> response, long latencyMs) {
        try {
            UUID id = UUID.randomUUID();
            Object intents = response.get("intents");
            Object citations = response.get("citations");
            repository.insert(new Entry(id, audience, anonymize(question),
                    intents instanceof Collection<?> list ? String.join(",", list.stream().map(String::valueOf).toList()) : "",
                    String.valueOf(response.getOrDefault("status", "ANSWERED")),
                    String.valueOf(response.getOrDefault("mode", "extractive")),
                    citations instanceof Collection<?> c ? c.size() : 0,
                    (int) Math.min(latencyMs, Integer.MAX_VALUE)));
            return id;
        } catch (RuntimeException e) {
            log.warn("Không ghi được nhật ký CapyLink: {}", e.getMessage());
            return null;
        }
    }

    public void rate(UUID id, boolean helpful, String note) {
        String cleaned = note == null || note.isBlank() ? null : anonymize(note);
        if (cleaned != null && cleaned.length() > MAX_NOTE) cleaned = cleaned.substring(0, MAX_NOTE);
        if (!repository.rate(id, helpful, cleaned)) {
            throw new BadRequestException("Câu trả lời này đã được đánh giá hoặc đã quá hạn đánh giá.");
        }
    }

    public Map<String, Object> insights(int days) {
        AssistantInteractionRepository.Stats stats = repository.stats(days);
        List<AssistantInteractionRepository.Flagged> flagged = repository.flagged(20);
        return Map.of("days", days, "stats", stats, "flagged", flagged);
    }
}
