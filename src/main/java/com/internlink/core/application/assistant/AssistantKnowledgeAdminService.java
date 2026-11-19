package com.internlink.core.application.assistant;

import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Detail;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Draft;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Summary;
import com.internlink.core.presentation.assistant.AssistantKnowledgeRequest;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Ban quản lý Khoa tự nạp và quản lý tài liệu cho trợ lý CapyLink, không cần viết migration. */
@Service
@RequiredArgsConstructor
public class AssistantKnowledgeAdminService {
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MIN_CONTENT = 50;
    private static final Set<String> TYPES = Set.of("pdf", "docx", "md", "txt");
    private static final Map<String, String> ERRORS = Map.of(
            "ocr_required", "Tệp PDF này là bản scan (không có lớp chữ). Hãy chạy OCR rồi tải lên tệp .md/.txt đã làm sạch.",
            "encrypted_pdf", "Tệp PDF đang được đặt mật khẩu, hãy gỡ mật khẩu rồi tải lại.",
            "too_many_pages", "Tài liệu dài quá 50 trang. Hãy tách thành nhiều phần.",
            "document_text_too_large", "Nội dung vượt quá 100.000 ký tự. Hãy tách thành nhiều phần.",
            "unsupported_document_type", "Chỉ hỗ trợ tệp PDF, DOCX, MD hoặc TXT.",
            "empty_document", "Tệp không có nội dung chữ.");

    private final AssistantDocumentRepository documents;
    private final AssistantKnowledgeProvider knowledge;
    private final AiServiceClient ai;

    public List<Summary> list() {
        return documents.findAll();
    }

    public Detail get(UUID id) {
        return documents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu."));
    }

    /** Chỉ tách chữ để Khoa xem trước; chưa lưu gì vào kho. */
    public Map<String, Object> extract(String filename, byte[] data, String title) {
        String name = filename == null ? "" : filename.strip();
        String type = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!TYPES.contains(type)) throw new BadRequestException(ERRORS.get("unsupported_document_type"));
        if (data.length == 0 || data.length > MAX_BYTES) throw new BadRequestException("Tệp phải có dung lượng từ 1 byte đến 10MB.");
        Map<String, Object> result = ai.extractKnowledge(name, Base64.getEncoder().encodeToString(data), title);
        if ("FAILED".equals(result.get("status"))) {
            throw new BadRequestException("Dịch vụ AI đang tạm ngưng, chưa tách được chữ. Hãy thử lại sau.");
        }
        if (!Boolean.TRUE.equals(result.get("ok"))) {
            String code = String.valueOf(result.get("error"));
            throw new BadRequestException(ERRORS.getOrDefault(code, "Không đọc được tệp (" + code + ")."));
        }
        return result;
    }

    public Detail create(AssistantKnowledgeRequest request) {
        String content = request.content() == null ? "" : request.content().strip();
        if (content.length() < MIN_CONTENT) throw new BadRequestException("Nội dung tài liệu quá ngắn (tối thiểu 50 ký tự).");
        UUID id = documents.insert(draft(request, content));
        knowledge.invalidate();
        return get(id);
    }

    public Detail update(UUID id, AssistantKnowledgeRequest request) {
        String content = request.content() == null || request.content().isBlank() ? null : request.content().strip();
        if (content != null && content.length() < MIN_CONTENT) {
            throw new BadRequestException("Nội dung tài liệu quá ngắn (tối thiểu 50 ký tự).");
        }
        if (!documents.update(id, draft(request, content))) throw new ResourceNotFoundException("Không tìm thấy tài liệu.");
        knowledge.invalidate();
        return get(id);
    }

    public void setActive(UUID id, boolean active) {
        if (!documents.setActive(id, active)) throw new ResourceNotFoundException("Không tìm thấy tài liệu.");
        knowledge.invalidate();
    }

    public void delete(UUID id) {
        if (!documents.delete(id)) throw new ResourceNotFoundException("Không tìm thấy tài liệu.");
        knowledge.invalidate();
    }

    private static Draft draft(AssistantKnowledgeRequest request, String content) {
        String category = request.category() == null || request.category().isBlank() ? "REGULATIONS_GUIDELINES" : request.category();
        String url = request.sourceUrl() == null || request.sourceUrl().isBlank() ? null : request.sourceUrl().strip();
        return new Draft(request.title().strip(), category, url, content, request.visibility(), request.issuedOn(),
                !Boolean.FALSE.equals(request.active()));
    }
}
