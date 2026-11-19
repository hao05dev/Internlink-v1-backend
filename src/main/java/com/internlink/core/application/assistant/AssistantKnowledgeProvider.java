package com.internlink.core.application.assistant;

import com.internlink.core.application.recruitment.JobPositionService;
import com.internlink.core.application.system.CareerGuidePostService;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Document;
import com.internlink.core.infrastructure.persistence.jdbc.JobSkillTrendRepository;
import com.internlink.core.infrastructure.persistence.jdbc.JobSkillTrendRepository.TermTotal;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.presentation.recruitment.dto.response.JobSkillResponse;
import com.internlink.core.presentation.system.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * Gom dữ liệu CÔNG KHAI cho trợ lý: văn bản chính thức của trường, bài cẩm nang đã xuất bản,
 * tin đã duyệt còn nhận hồ sơ, số liệu kỹ năng.
 * Có cache ngắn để mỗi câu hỏi không phải đọc lại toàn bộ bảng.
 */
@Component
@RequiredArgsConstructor
public class AssistantKnowledgeProvider {
    static final long TTL_MILLIS = 60_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CareerGuidePostService posts;
    private final JobPositionService jobs;
    private final JobSkillTrendRepository trends;
    private final AssistantDocumentRepository documents;
    private final Map<String, Cached> cache = new HashMap<>();

    private record Cached(long at, Object value) {}

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> knowledge(boolean includeInternal) {
        return (List<Map<String, Object>>) cached("knowledge:" + includeInternal, () -> {
            List<Map<String, Object>> docs = new ArrayList<>();
            documents.activeDocuments().stream().filter(d -> includeInternal || "PUBLIC".equals(d.visibility()))
                    .limit(50).map(AssistantKnowledgeProvider::toDoc).forEach(docs::add);
            posts.getPublishedPosts(null, null).stream()
                    .filter(p -> includeInternal || p.getVisibility() == null || "PUBLIC".equals(p.getVisibility()))
                    .limit(300).map(AssistantKnowledgeProvider::toDoc).forEach(docs::add);
            return docs;
        });
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> openJobs() {
        return (List<Map<String, Object>>) cached("jobs", () -> jobs.getPublicJobs(null, null, null, null, null)
                .stream().limit(500).map(AssistantKnowledgeProvider::toJob).toList());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> trendSnapshot() {
        return (Map<String, Object>) cached("trends", this::loadTrends);
    }

    /** Gọi sau khi Khoa thêm/sửa tài liệu để câu hỏi kế tiếp dùng ngay nội dung mới. */
    public synchronized void invalidate() {
        cache.remove("knowledge:true");
        cache.remove("knowledge:false");
    }

    private synchronized Object cached(String key, Supplier<Object> loader) {
        Cached hit = cache.get(key);
        long now = System.currentTimeMillis();
        if (hit == null || now - hit.at() > TTL_MILLIS) {
            hit = new Cached(now, loader.get());
            cache.put(key, hit);
        }
        return hit.value();
    }

    private Map<String, Object> loadTrends() {
        List<TermTotal> terms = trends.latestTerms();
        if (terms.isEmpty()) return null;
        TermTotal current = terms.getFirst();
        TermTotal previous = terms.size() > 1 ? terms.get(1) : null;
        Map<String, Long> before = previous == null ? Map.of() : trends.skillCounts(previous.termId(), 200);
        List<Map<String, Object>> skills = new ArrayList<>();
        trends.skillCounts(current.termId(), 20).forEach((name, count) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("name", name);
            row.put("count", count);
            row.put("previous_count", previous == null ? null : before.getOrDefault(name, 0L));
            skills.add(row);
        });
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("period_label", current.label());
        snapshot.put("previous_label", previous == null ? null : previous.label());
        snapshot.put("total_jobs", current.totalJobs());
        snapshot.put("previous_total_jobs", previous == null ? null : previous.totalJobs());
        snapshot.put("top_skills", skills);
        snapshot.put("work_formats", trends.countBy(current.termId(), "work_format"));
        snapshot.put("job_types", trends.countBy(current.termId(), "job_type"));
        return snapshot;
    }

    static Map<String, Object> toDoc(PostResponse post) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", post.getId().toString());
        doc.put("title", cut(post.getTitle(), 300));
        doc.put("category", post.getCategory() == null ? "GENERAL" : post.getCategory().name());
        doc.put("url", "/career-guide/" + post.getSlug());
        doc.put("updated_at", post.getPublishedAt() == null ? null : DATE.format(post.getPublishedAt()));
        String summary = post.getSummary() == null ? "" : post.getSummary() + "\n\n";
        doc.put("content", cut(summary + Objects.requireNonNullElse(post.getContent(), ""), 100_000));
        return doc;
    }

    static Map<String, Object> toDoc(Document document) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "doc-" + document.id());
        doc.put("title", cut(document.title(), 300));
        doc.put("category", Objects.requireNonNullElse(document.category(), "REGULATIONS_GUIDELINES"));
        doc.put("url", document.sourceUrl());
        doc.put("updated_at", document.issuedOn() == null ? null : DATE.format(document.issuedOn()));
        doc.put("content", cut(document.content(), 100_000));
        return doc;
    }

    static Map<String, Object> toJob(JobPositionResponse job) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", job.getId().toString());
        row.put("title", cut(job.getTitle(), 300));
        row.put("company", cut(job.getCompanyName(), 300));
        row.put("location", cut(job.getLocation(), 300));
        row.put("work_format", job.getWorkFormat() == null ? null : job.getWorkFormat().name());
        // AI service dùng nhãn INTERN/FRESHER của bộ phân tích câu hỏi.
        row.put("job_type", job.getJobType() == null ? null : job.getJobType().name().replace("INTERNSHIP", "INTERN"));
        row.put("stipend", stipend(job.getStipendAmount()));
        row.put("deadline", job.getApplicationDeadline() == null ? null : DATE.format(job.getApplicationDeadline()));
        row.put("skills", job.getSkills().stream().map(JobSkillResponse::getSkillName).filter(Objects::nonNull).limit(60).toList());
        row.put("description", cut(job.getDescription(), 1500));
        row.put("url", "/jobs/" + job.getId());
        return row;
    }

    private static String cut(String text, int limit) {
        String value = text == null ? "" : text;
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private static String stipend(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return null;
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " đ/tháng";
    }
}
