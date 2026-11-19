package com.internlink.core.infrastructure.persistence.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Văn bản chính thức (quy định, đề cương) dùng làm nguồn tri thức cho trợ lý CapyLink. */
@Repository
@RequiredArgsConstructor
public class AssistantDocumentRepository {
    private final JdbcTemplate jdbc;

    public record Document(UUID id, String title, String category, String sourceUrl, String content,
                           String visibility, LocalDate issuedOn) {}

    /** Dòng hiển thị ở màn hình quản lý: không kèm toàn văn để danh sách nhẹ. */
    public record Summary(UUID id, String title, String category, String sourceUrl, String visibility,
                          LocalDate issuedOn, boolean active, int contentLength, OffsetDateTime updatedAt) {}

    public record Detail(Summary summary, String content) {}

    public record Draft(String title, String category, String sourceUrl, String content, String visibility,
                        LocalDate issuedOn, boolean active) {}

    private static final String SUMMARY_COLUMNS = "id, title, category, source_url, visibility, issued_on, is_active,"
            + " char_length(content), updated_at";

    public List<Document> activeDocuments() {
        return jdbc.query("SELECT id, title, category, source_url, content, visibility, issued_on"
                        + " FROM assistant_knowledge_documents WHERE is_active ORDER BY issued_on DESC NULLS LAST",
                (rs, i) -> new Document(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getObject(7, LocalDate.class)));
    }

    public List<Summary> findAll() {
        return jdbc.query("SELECT " + SUMMARY_COLUMNS + " FROM assistant_knowledge_documents"
                + " ORDER BY is_active DESC, issued_on DESC NULLS LAST, title", (rs, i) -> summary(rs));
    }

    public Optional<Detail> findById(UUID id) {
        return jdbc.query("SELECT " + SUMMARY_COLUMNS + ", content FROM assistant_knowledge_documents WHERE id = ?",
                (rs, i) -> new Detail(summary(rs), rs.getString(10)), id).stream().findFirst();
    }

    public UUID insert(Draft draft) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assistant_knowledge_documents"
                        + " (id, title, category, source_url, content, visibility, issued_on, is_active) VALUES (?,?,?,?,?,?,?,?)",
                id, draft.title(), draft.category(), draft.sourceUrl(), draft.content(), draft.visibility(),
                draft.issuedOn(), draft.active());
        return id;
    }

    /** Cập nhật thông tin; {@code content} null nghĩa là giữ nguyên toàn văn cũ. */
    public boolean update(UUID id, Draft draft) {
        return jdbc.update("UPDATE assistant_knowledge_documents SET title = ?, category = ?, source_url = ?,"
                        + " content = COALESCE(?, content), visibility = ?, issued_on = ?, is_active = ?, updated_at = now()"
                        + " WHERE id = ?",
                draft.title(), draft.category(), draft.sourceUrl(), draft.content(), draft.visibility(),
                draft.issuedOn(), draft.active(), id) > 0;
    }

    public boolean setActive(UUID id, boolean active) {
        return jdbc.update("UPDATE assistant_knowledge_documents SET is_active = ?, updated_at = now() WHERE id = ?",
                active, id) > 0;
    }

    public boolean delete(UUID id) {
        return jdbc.update("DELETE FROM assistant_knowledge_documents WHERE id = ?", id) > 0;
    }

    private static Summary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Summary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getObject(6, LocalDate.class), rs.getBoolean(7), rs.getInt(8),
                rs.getObject(9, OffsetDateTime.class));
    }
}
