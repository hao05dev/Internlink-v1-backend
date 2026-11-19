package com.internlink.core.infrastructure.persistence.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Nhật ký hỏi đáp ẩn danh của trợ lý CapyLink (không có cột người hỏi). */
@Repository
@RequiredArgsConstructor
public class AssistantInteractionRepository {
    private final JdbcTemplate jdbc;

    public record Entry(UUID id, String audience, String question, String intents, String status, String mode,
                        int citationCount, int latencyMs) {}

    public record Stats(long total, long rated, long helpful, long unhelpful, long blocked, double avgLatencyMs) {}

    public record Flagged(UUID id, String audience, String question, String intents, String status,
                          String note, OffsetDateTime createdAt) {}

    public void insert(Entry e) {
        jdbc.update("INSERT INTO assistant_interactions (id, audience, question, intents, status, mode, citation_count,"
                        + " latency_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                e.id(), e.audience(), e.question(), e.intents(), e.status(), e.mode(), e.citationCount(), e.latencyMs());
    }

    /** Chỉ chấm một lần cho mỗi câu trả lời; trả về false nếu không có hoặc đã chấm. */
    public boolean rate(UUID id, boolean helpful, String note) {
        return jdbc.update("UPDATE assistant_interactions SET helpful = ?, feedback_note = ?, rated_at = now()"
                + " WHERE id = ? AND helpful IS NULL AND created_at > now() - interval '1 day'", helpful, note, id) == 1;
    }

    public Stats stats(int days) {
        return jdbc.queryForObject("SELECT count(*), count(helpful), count(*) FILTER (WHERE helpful),"
                        + " count(*) FILTER (WHERE helpful = FALSE),"
                        + " count(*) FILTER (WHERE status IN ('BLOCKED_PRIVACY', 'OUT_OF_SCOPE')),"
                        + " coalesce(avg(latency_ms), 0) FROM assistant_interactions"
                        + " WHERE created_at > now() - make_interval(days => ?)",
                (rs, i) -> new Stats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        rs.getDouble(6)), days);
    }

    /** Câu hỏi bị chấm 👎 hoặc không tìm được nguồn: gợi ý Khoa bổ sung tài liệu. */
    public List<Flagged> flagged(int limit) {
        return jdbc.query("SELECT id, audience, question, intents, status, feedback_note, created_at"
                        + " FROM assistant_interactions WHERE helpful = FALSE OR status = 'NO_SOURCE'"
                        + " ORDER BY created_at DESC LIMIT ?",
                (rs, i) -> new Flagged(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class)), limit);
    }
}
