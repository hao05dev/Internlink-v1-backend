package com.internlink.core.infrastructure.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class AiServiceClient {
    private final WebClient webClient;
    @Value("${internlink.ai-service.api-key:}")
    private String serviceApiKey = "";

    public AiServiceClient(WebClient.Builder builder,
            @Value("${internlink.ai-service.base-url:http://localhost:8001}") String url) {
        webClient = builder.baseUrl(url).codecs(config -> config.defaultCodecs().maxInMemorySize(4 * 1024 * 1024)).build();
    }

    public Map<String, Object> health() {
        try {
            return webClient.get().uri("/").retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(3)).blockOptional().orElse(Map.of("status", "unavailable"));
        } catch (Exception ex) {
            return Map.of("status", "unavailable");
        }
    }

    public Map<String, Object> infer(String endpoint, Map<String, Object> body) {
        return infer(endpoint, body, Duration.ofSeconds(20));
    }

    /** Trợ lý CapyLink: có thể gọi embedding + LLM nên cho thời gian dài hơn một lượt suy luận thường. */
    public Map<String, Object> assistantChat(Map<String, Object> body) {
        return infer("/api/v1/assistant/chat", body, Duration.ofSeconds(35));
    }

    private Map<String, Object> infer(String endpoint, Map<String, Object> body, Duration timeout) {
        try {
            return webClient.post().uri(endpoint).header("X-InternLink-AI-Key", serviceApiKey)
                .bodyValue(body).retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(timeout).blockOptional()
                .orElse(Map.of("status", "FAILED", "error_code", "empty_response"));
        } catch (WebClientResponseException ex) {
            // Return only known parser errors, never an upstream message containing CV text.
            String code = "ai_unavailable";
            int status = ex.getStatusCode().value();
            for (String known : List.of("ocr_required", "encrypted_pdf", "unsupported_document_type",
                    "document_too_large", "document_text_too_large", "unreadable_document", "too_many_pages", "invalid_base64")) {
                if (status == 422 && ex.getResponseBodyAsString().contains("\"" + known + "\"")) code = known;
            }
            for (String known : List.of("form_reader_disabled", "form_reader_failed")) {
                if (status == 503 && ex.getResponseBodyAsString().contains("\"" + known + "\"")) code = known;
            }
            return Map.of("status", "FAILED", "error_code", code);
        } catch (Exception ex) {
            log.warn("AI request failed: {}", ex.getClass().getSimpleName());
            return Map.of("status", "FAILED", "error_code", "ai_unavailable");
        }
    }

    public Map<String, Object> extractSkills(String text) {
        return extractSkills(text, false);
    }

    public Map<String, Object> extractSkills(String text, boolean allowCloud) {
        Map<String, Object> result = new HashMap<>(infer("/api/v1/skills/extract", Map.of("text", text == null ? "" : text, "allow_cloud", allowCloud)));
        result.putIfAbsent("normalized_skills", List.of());
        return result;
    }

    public Map<String, Object> extractDocument(String name, String base64, boolean allowCloud) {
        return infer("/api/v1/skills/extract-document",
            Map.of("filename", name, "content_base64", base64, "allow_cloud", allowCloud));
    }

    /** Tách chữ tài liệu cho kho tri thức CapyLink; PDF lớn có thể mất vài giây nên cho thời gian chờ dài hơn. */
    public Map<String, Object> extractKnowledge(String name, String base64, String title) {
        return infer("/api/v1/assistant/extract-knowledge",
            Map.of("filename", name, "content_base64", base64, "title", title == null ? "" : title), Duration.ofSeconds(60));
    }

    /** Đọc ảnh Phiếu đánh giá của cơ quan (M-TT-03); model đa phương thức có thể mất 10–40 giây. */
    public Map<String, Object> readM03(String mimeType, String base64) {
        return infer("/api/v1/assistance/read-m03", Map.of("mime_type", mimeType, "content_base64", base64), Duration.ofSeconds(60));
    }

    public Map<String, Object> parseRubric(String name, String base64) {
        return infer("/api/v1/assistance/parse-rubric", Map.of("filename", name, "content_base64", base64, "allow_cloud", false));
    }

    public Map<String, Object> parseSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return Map.of("parsed", false, "raw_query", "", "cleaned_keywords", "", "summary_message", "");
        }
        Map<String, Object> result = infer("/api/v1/assistance/parse-query", Map.of("query", query));
        if ("FAILED".equals(result.get("status"))) {
            return Map.of(
                "parsed", false,
                "raw_query", query,
                "cleaned_keywords", query,
                "summary_message", "Tìm kiếm tự do: " + query
            );
        }
        return result;
    }

    public List<Map<String, Object>> rankJobs(Map<String, Object> student, List<Map<String, Object>> jobs) {
        if (jobs.isEmpty()) return List.of();
        Map<String, Object> response = infer("/api/v1/matching/rank-jobs", Map.of("student", student, "jobs", jobs));
        Map<String, Map<?, ?>> byId = new HashMap<>();
        if (response.get("rankings") instanceof List<?> rows) {
            for (Object row : rows) if (row instanceof Map<?, ?> map && map.get("job_id") instanceof String id) byId.put(id, map);
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> job : jobs) {
            String id = String.valueOf(job.get("id"));
            Map<String, Object> normalized = byId.containsKey(id) ? normalizeRankingResponse(byId.get(id))
                : fallbackMatchScore(strings(student.get("skill_ids")), strings(job.get("mandatory_skill_ids")), strings(job.get("optional_skill_ids")));
            Map<String, Object> row = new HashMap<>(normalized);
            row.put("job_id", id);
            results.add(row);
        }
        return results;
    }

    public Map<String, Object> calculateMatchScore(UUID studentId, UUID jobId, String title,
            String description, String bio, List<String> skills, List<String> mandatory, List<String> optional) {
        return rankJobs(Map.of("id", studentId.toString(), "skill_ids", skills, "bio_summary", bio == null ? "" : bio),
            List.of(Map.of("id", jobId.toString(), "title", title == null || title.isBlank() ? "Internship" : title,
                "description", description == null ? "" : description, "mandatory_skill_ids", mandatory,
                "optional_skill_ids", optional))).getFirst();
    }

    public Map<String, Object> calculateMatchScore(UUID studentId, UUID jobId, List<String> skills, List<String> required) {
        return calculateMatchScore(studentId, jobId, "Internship", "", "", skills, required, List.of());
    }

    public Map<String, Object> normalizeRankingResponse(Map<?, ?> ranking) {
        Map<String, Object> result = new HashMap<>();
        result.put("match_score", value(ranking, "match_percentage", value(ranking, "match_score", 0.0)));
        result.put("matched_skills", value(ranking, "matched_skills", List.of()));
        result.put("missing_skills", value(ranking, "missing_skills", List.of()));
        result.put("mode", value(ranking, "mode", "unknown"));
        result.put("score_available", value(ranking, "score_available", false));
        result.put("semantic_available", value(ranking, "semantic_available", false));
        result.put("algorithm_version", value(ranking, "algorithm_version", "unknown"));
        result.put("warnings", value(ranking, "warnings", List.of()));
        Map<String, Object> explanation = new HashMap<>();
        for (String key : List.of("final_score", "skill_score", "semantic_score", "preference_score", "academic_score",
                "recommendation", "constraints", "effective_weights", "component_availability")) {
            if (ranking.get(key) != null) explanation.put(key, ranking.get(key));
        }
        result.put("explanation", explanation);
        return result;
    }

    private static Object value(Map<?, ?> map, String key, Object fallback) {
        return map.get(key) == null ? fallback : map.get(key);
    }

    private static List<String> strings(Object raw) {
        return raw instanceof List<?> items ? items.stream().filter(Objects::nonNull).map(Object::toString).toList() : List.of();
    }

    public Map<String, Object> fallbackMatchScore(List<String> student, List<String> required, List<String> desired) {
        Set<String> skills = new TreeSet<>(student == null ? List.of() : student);
        Set<String> mandatory = new TreeSet<>(required == null ? List.of() : required);
        Set<String> optional = new TreeSet<>(desired == null ? List.of() : desired);
        optional.removeAll(mandatory);
        double requiredRatio = mandatory.isEmpty() ? 0 : (double) mandatory.stream().filter(skills::contains).count() / mandatory.size();
        double optionalRatio = optional.isEmpty() ? 0 : (double) optional.stream().filter(skills::contains).count() / optional.size();
        double score = mandatory.isEmpty() ? optionalRatio : optional.isEmpty() ? requiredRatio : 0.8 * requiredRatio + 0.2 * optionalRatio;
        boolean available = !skills.isEmpty() && (!mandatory.isEmpty() || !optional.isEmpty());
        List<String> missing = mandatory.stream().filter(id -> !skills.contains(id)).toList();
        Map<String, Object> raw = new HashMap<>();
        raw.put("match_percentage", Math.round(score * 1000) / 10.0);
        raw.put("skill_score", score);
        raw.put("final_score", score);
        raw.put("semantic_score", 0.0);
        raw.put("academic_score", 0.0);
        raw.put("matched_skills", skills.stream().filter(id -> mandatory.contains(id) || optional.contains(id)).toList());
        raw.put("missing_skills", missing);
        raw.put("mode", available ? "skill_only" : "insufficient_data");
        raw.put("score_available", available);
        raw.put("semantic_available", false);
        raw.put("algorithm_version", "skill-semantic-v2");
        raw.put("warnings", List.of("ai_unavailable", "semantic_unavailable", "preferences_not_evaluated"));
        raw.put("recommendation", available
            ? "Điểm theo kỹ năng đã xác nhận; dịch vụ AI tạm thời không khả dụng. Cần xem thêm điều kiện thực tập."
            : "Chưa đủ dữ liệu để tính điểm phù hợp.");
        Map<String, Object> result = normalizeRankingResponse(raw);
        @SuppressWarnings("unchecked")
        Map<String, Object> explanation = (Map<String, Object>) result.get("explanation");
        explanation.put("note", "Fallback Rule-Based Calculator (Mandatory 80% + Optional 20%)");
        return result;
    }
}
