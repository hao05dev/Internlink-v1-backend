package com.internlink.core.application.system.notification;

import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jdbc.EmailTemplateRepository;
import com.internlink.core.infrastructure.persistence.jdbc.EmailTemplateRepository.Template;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.system.dto.request.EmailTemplateRequest;
import com.internlink.core.presentation.system.dto.response.EmailTemplateResponse;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {
    public record Resolved(UUID templateId, UUID versionId, int version, String subject, String body) {}

    private static final Set<String> PURPOSES = Set.of(
            "GENERAL", "FACULTY_NOTICE", "INTRODUCTION_LETTER", "INTERVIEW_INVITATION");
    private final EmailTemplateRepository repository;
    private final EmailTemplateRenderer renderer;
    private final JpaUserRepository users;
    private final SecurityGuard guard;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public List<EmailTemplateResponse.Item> list(String purpose) {
        User actor = actor();
        Scope scope = scope(actor);
        String effectivePurpose = actor.getRole() == UserRole.COMPANY_REP
                ? "INTERVIEW_INVITATION" : purpose;
        return repository.available(
                        effectivePurpose, scope.type(), scope.id(), actor.getRole() == UserRole.ADMIN)
                .stream()
                .filter(template -> canRead(actor, template))
                .filter(template -> actor.getRole() != UserRole.FACULTY_ADMIN
                        || !"INTERVIEW_INVITATION".equals(template.purpose()))
                .map(this::response)
                .toList();
    }

    @Transactional
    public EmailTemplateResponse.Item create(EmailTemplateRequest.Save request) {
        validate(request);
        User actor = actor();
        Scope scope = requestedScope(actor, request.ownerScope(), request.ownerId());
        requirePurpose(actor, request.purpose());
        Template saved = repository.create(
                normalizeCode(request.code()), request.name().strip(), request.purpose(), scope.type(), scope.id(),
                request.subjectTemplate(), request.bodyTemplate(),
                renderer.variables(request.subjectTemplate(), request.bodyTemplate()), actor.getId());
        audit(actor, "CREATE_EMAIL_TEMPLATE", saved.id(), Map.of("scope", scope.type(), "purpose", saved.purpose()));
        return response(saved);
    }

    @Transactional
    public EmailTemplateResponse.Item update(UUID id, EmailTemplateRequest.Save request) {
        validate(request);
        User actor = actor();
        Template current = requireOwned(actor, id);
        if (!current.code().equals(normalizeCode(request.code())) || !current.purpose().equals(request.purpose()))
            throw new BadRequestException("Không thể đổi mã hoặc mục đích của template đã tạo");
        Template saved = repository.update(current, request.name().strip(), request.subjectTemplate(),
                request.bodyTemplate(), renderer.variables(request.subjectTemplate(), request.bodyTemplate()), actor.getId());
        audit(actor, "UPDATE_EMAIL_TEMPLATE", id, Map.of("version", saved.version()));
        return response(saved);
    }

    @Transactional
    public void archive(UUID id) {
        User actor = actor();
        Template current = requireOwned(actor, id);
        repository.archive(id);
        audit(actor, "ARCHIVE_EMAIL_TEMPLATE", id, Map.of("version", current.version()));
    }

    @Transactional(readOnly = true)
    public EmailTemplateResponse.Preview preview(EmailTemplateRequest.Preview request) {
        Resolved value = resolve(request.templateId(), null, request.variables());
        return new EmailTemplateResponse.Preview(value.subject(), value.body(), value.version());
    }

    @Transactional(readOnly = true)
    public Resolved resolve(UUID id, String purpose, Map<String, String> values) {
        User actor = actor();
        Template template = repository.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", "id", id));
        if (!canRead(actor, template) || !"ACTIVE".equals(template.status()))
            throw new ForbiddenException("Template email không khả dụng");
        if (purpose != null && !purpose.equals(template.purpose()))
            throw new BadRequestException("Template không đúng mục đích gửi");
        return new Resolved(template.id(), template.versionId(), template.version(),
                renderer.render(template.subject(), values).replace('\r', ' ').replace('\n', ' '),
                renderer.render(template.body(), values));
    }

    @Transactional(readOnly = true)
    public UUID versionForCampaign(UUID id) {
        if (id == null) return null;
        User actor = actor();
        Template template = repository.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", "id", id));
        if (!canRead(actor, template) || !"ACTIVE".equals(template.status())
                || "INTERVIEW_INVITATION".equals(template.purpose()))
            throw new ForbiddenException("Template không khả dụng cho chiến dịch này");
        if (!Set.of("studentName", "studentCode", "termName").containsAll(template.variables()))
            throw new BadRequestException(
                    "Template campaign chỉ hỗ trợ {{studentName}}, {{studentCode}}, {{termName}}");
        return template.versionId();
    }

    private Template requireOwned(User actor, UUID id) {
        Template template = repository.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", "id", id));
        if (actor.getRole() != UserRole.ADMIN && !owns(actor, template))
            throw new ForbiddenException("Bạn không có quyền chỉnh sửa template này");
        return template;
    }

    private boolean canRead(User actor, Template template) {
        return "SYSTEM".equals(template.ownerScope()) || actor.getRole() == UserRole.ADMIN || owns(actor, template);
    }

    private boolean owns(User actor, Template template) {
        Scope scope = scope(actor);
        return scope.type().equals(template.ownerScope()) && Objects.equals(scope.id(), template.ownerId());
    }

    private Scope requestedScope(User actor, String type, UUID id) {
        if (actor.getRole() != UserRole.ADMIN) return scope(actor);
        String value = type == null || type.isBlank() ? "SYSTEM" : type;
        if (!Set.of("SYSTEM", "DEPARTMENT", "COMPANY").contains(value))
            throw new BadRequestException("Phạm vi template không hợp lệ");
        if (("SYSTEM".equals(value) && id != null) || (!"SYSTEM".equals(value) && id == null))
            throw new BadRequestException("Chủ sở hữu template không hợp lệ");
        return new Scope(value, id);
    }

    private Scope scope(User actor) {
        return switch (actor.getRole()) {
            case FACULTY_ADMIN -> {
                if (actor.getDepartment() == null) throw new ForbiddenException("Tài khoản chưa có khoa phụ trách");
                yield new Scope("DEPARTMENT", actor.getDepartment().getId());
            }
            case COMPANY_REP -> {
                if (actor.getCompany() == null) throw new ForbiddenException("Tài khoản chưa thuộc doanh nghiệp");
                yield new Scope("COMPANY", actor.getCompany().getId());
            }
            case ADMIN -> new Scope("SYSTEM", null);
            default -> throw new ForbiddenException("Vai trò không được quản lý template email");
        };
    }

    private void requirePurpose(User actor, String purpose) {
        if (actor.getRole() == UserRole.COMPANY_REP && !"INTERVIEW_INVITATION".equals(purpose))
            throw new ForbiddenException("Doanh nghiệp chỉ được tạo template mời phỏng vấn");
        if (actor.getRole() == UserRole.FACULTY_ADMIN && "INTERVIEW_INVITATION".equals(purpose))
            throw new ForbiddenException("Template mời phỏng vấn thuộc phạm vi doanh nghiệp");
    }

    private void validate(EmailTemplateRequest.Save request) {
        if (!PURPOSES.contains(request.purpose()) || request.subjectTemplate().contains("\r")
                || request.subjectTemplate().contains("\n"))
            throw new BadRequestException("Nội dung hoặc mục đích template không hợp lệ");
        Set<String> allowed = "INTERVIEW_INVITATION".equals(request.purpose())
                ? Set.of(
                        "student.fullName", "company.name", "job.title", "interview.scheduledAt",
                        "interview.format", "interview.destination", "interview.interviewer",
                        "interview.notes", "actionUrl")
                : Set.of("studentName", "studentCode", "termName");
        var invalid = new LinkedHashSet<>(
                renderer.variables(request.subjectTemplate(), request.bodyTemplate()));
        invalid.removeAll(allowed);
        if (!invalid.isEmpty())
            throw new BadRequestException("Biến template không được hỗ trợ: " + String.join(", ", invalid));
    }

    private User actor() {
        guard.requireRole(UserRole.ADMIN, UserRole.FACULTY_ADMIN, UserRole.COMPANY_REP);
        return users.findById(guard.currentUser().getId())
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new ForbiddenException("Tài khoản không hoạt động"));
    }

    private void audit(User actor, String action, UUID id, Map<String, Object> details) {
        audit.logAction(actor.getId(), action, "EmailTemplate", id, "SUCCESS", details, null);
    }

    private EmailTemplateResponse.Item response(Template t) {
        return new EmailTemplateResponse.Item(t.id(), t.code(), t.name(), t.purpose(), t.ownerScope(),
                t.ownerId(), t.status(), t.version(), t.versionId(), t.subject(), t.body(),
                t.variables(), t.updatedAt());
    }

    private String normalizeCode(String value) {
        String code = value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        if (code.isBlank()) throw new BadRequestException("Mã template không hợp lệ");
        return code;
    }

    private record Scope(String type, UUID id) {}
}
