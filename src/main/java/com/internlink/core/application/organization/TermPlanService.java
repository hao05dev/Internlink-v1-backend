package com.internlink.core.application.organization;

import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.organization.TermMilestone;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaTermMilestoneRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Kế hoạch TTTT của kỳ: Văn phòng Trường nhập các mốc theo văn bản của Trường rồi công bố.
 * Trước khi công bố chỉ VP Trường/Admin thấy; sau khi công bố mọi người dùng thấy và bộ nhắc tự động dùng các mốc.
 */
@Service
@RequiredArgsConstructor
public class TermPlanService {
    static final int MAX_MILESTONES = 40;

    private final JpaInternshipTermRepository terms;
    private final JpaTermMilestoneRepository milestones;
    private final JpaStudentRosterRepository rosters;
    private final JpaUserRepository users;
    private final SecurityGuard security;
    private final NotificationService notifications;
    private final AuditLogService audit;
    private final TermPlanDocumentService documents;
    private final TermPlanNewsPublisher news;

    public record MilestoneItem(UUID id, String kind, String label, String note,
                                OffsetDateTime startAt, OffsetDateTime endAt, Integer requiredWeeks, int sortOrder,
                                String latePolicy) {}

    /** document: tệp văn bản gốc; newsSlug: bài tin công khai (/career-guide/{slug}) sau khi công bố. */
    public record TermPlan(UUID termId, String termName, String documentRef, OffsetDateTime publishedAt,
                           boolean editable, List<MilestoneItem> milestones,
                           TermPlanDocumentService.PlanDocument document, String newsSlug) {}

    /** latePolicy: ALLOW_LATE (mặc định) hoặc LOCK; chỉ giữ cho mốc hạn nộp Phiếu tiếp nhận. */
    public record MilestoneInput(String kind, String label, String note,
                                 OffsetDateTime startAt, OffsetDateTime endAt, Integer requiredWeeks, String latePolicy) {
        public MilestoneInput(String kind, String label, String note,
                              OffsetDateTime startAt, OffsetDateTime endAt, Integer requiredWeeks) {
            this(kind, label, note, startAt, endAt, requiredWeeks, null);
        }
    }

    /** documentMediaId: tệp văn bản đã tải lên (POST_ATTACHMENT); null = gỡ tệp. */
    public record SavePlanRequest(String documentRef, List<MilestoneInput> milestones, UUID documentMediaId) {
        public SavePlanRequest(String documentRef, List<MilestoneInput> milestones) {
            this(documentRef, milestones, null);
        }
    }

    public record PublishResult(int students, int staff, TermPlan plan) {}

    @Transactional(readOnly = true)
    public TermPlan get(UUID termId) {
        var term = term(termId);
        boolean school = ResourceAuthorization.actsForSchool(actor());
        // Kế hoạch chưa công bố chỉ VP Trường thấy để soạn; người khác nhận danh sách rỗng.
        boolean visible = school || term.getPlanPublishedAt() != null;
        return view(term, school, visible ? items(termId) : List.of(), visible);
    }

    /** Ghi đè toàn bộ mốc của kỳ (form nhập theo bảng của văn bản). Không đổi trạng thái công bố. */
    @Transactional
    public TermPlan save(UUID termId, SavePlanRequest request) {
        var term = term(termId);
        requireSchool();
        TermGuard.requireNotClosed(term);
        if (request == null || request.milestones() == null) throw new BadRequestException("Thiếu danh sách mốc");
        if (request.milestones().size() > MAX_MILESTONES)
            throw new BadRequestException("Kế hoạch tối đa " + MAX_MILESTONES + " mốc");
        List<TermMilestone> rows = new ArrayList<>();
        int order = 0;
        for (MilestoneInput input : request.milestones()) rows.add(validated(term, input, order++));
        term.setPlanDocumentRef(trimToNull(request.documentRef(), 100, "Số văn bản"));
        if (request.documentMediaId() != null) documents.requireDocument(request.documentMediaId());
        term.setPlanDocumentMediaId(request.documentMediaId());
        milestones.deleteByTermId(termId);
        milestones.saveAll(rows);
        terms.save(term);
        audit.logAction(security.currentUser().getId(), "SAVE_TERM_PLAN", "InternshipTerm", termId, "SUCCESS",
            Map.of("milestones", rows.size()), null);
        return view(term, true, items(termId), true);
    }

    /** Công bố (hoặc công bố lại sau khi sửa) và thông báo cho SV trong danh sách học phần, Khoa và GV. */
    @Transactional
    public PublishResult publish(UUID termId) {
        var term = term(termId);
        var publisher = requireSchool();
        TermGuard.requireNotClosed(term);
        var current = items(termId);
        if (current.isEmpty()) throw new BadRequestException("Kế hoạch chưa có mốc nào để công bố");
        boolean republish = term.getPlanPublishedAt() != null;
        term.setPlanPublishedAt(OffsetDateTime.now());
        term.setPlanPublishedBy(publisher);
        String slug = news.publish(term, current, publisher, documents.describe(term));
        terms.save(term);
        String link = "/career-guide/" + slug;

        String title = (republish ? "Cập nhật kế hoạch TTTT " : "Kế hoạch TTTT ") + term.getTermName();
        String message = (term.getPlanDocumentRef() != null ? "Văn bản số " + term.getPlanDocumentRef() + ". " : "")
            + "Văn phòng Trường đã " + (republish ? "cập nhật" : "công bố") + " các mốc thời gian thực tập thực tế.";
        Set<UUID> students = new LinkedHashSet<>();
        for (StudentRoster roster : rosters.findByTermId(termId))
            if (roster.getClaimedUser() != null) students.add(roster.getClaimedUser().getId());
        students.forEach(id -> notifications.sendNotification(id, "TERM_PLAN_PUBLISHED", title, message, link));
        int staff = 0;
        for (UserRole role : List.of(UserRole.FACULTY_ADMIN, UserRole.LECTURER)) {
            for (User user : users.findByRole(role)) {
                if (!Boolean.TRUE.equals(user.getIsActive())) continue;
                notifications.sendNotification(user.getId(), "TERM_PLAN_PUBLISHED", title, message, link);
                staff++;
            }
        }
        audit.logAction(publisher.getId(), republish ? "REPUBLISH_TERM_PLAN" : "PUBLISH_TERM_PLAN",
            "InternshipTerm", termId, "SUCCESS", Map.of("students", students.size(), "staff", staff), null);
        return new PublishResult(students.size(), staff, view(term, true, current, true));
    }

    static TermMilestone validated(InternshipTerm term, MilestoneInput input, int order) {
        if (input == null) throw new BadRequestException("Mốc không hợp lệ");
        String kind = input.kind() == null ? TermMilestone.OTHER : input.kind().trim().toUpperCase();
        if (!TermMilestone.KINDS.contains(kind)) throw new BadRequestException("Loại mốc không hợp lệ: " + input.kind());
        String label = trimToNull(input.label(), 500, "Nội dung mốc");
        if (label == null) throw new BadRequestException("Mốc thứ " + (order + 1) + " chưa có nội dung");
        if (input.startAt() == null && input.endAt() == null)
            throw new BadRequestException("Mốc \"" + label + "\" cần ít nhất ngày bắt đầu hoặc ngày kết thúc");
        if (input.startAt() != null && input.endAt() != null && input.startAt().isAfter(input.endAt()))
            throw new BadRequestException("Mốc \"" + label + "\" có ngày bắt đầu sau ngày kết thúc");
        Integer weeks = input.requiredWeeks();
        if (weeks != null && weeks != 8 && weeks != 12)
            throw new BadRequestException("Mốc chỉ áp dụng cho học phần 8 tuần hoặc 12 tuần");
        String policy = input.latePolicy() == null || input.latePolicy().isBlank()
            ? TermMilestone.ALLOW_LATE : input.latePolicy().trim().toUpperCase();
        if (!TermMilestone.ALLOW_LATE.equals(policy) && !TermMilestone.LOCK.equals(policy))
            throw new BadRequestException("Cách xử lý khi quá hạn không hợp lệ: " + input.latePolicy());
        if (!TermMilestone.ACCEPTANCE_FORM_DUE.equals(kind)) policy = TermMilestone.ALLOW_LATE;
        return TermMilestone.builder().term(term).kind(kind).label(label).latePolicy(policy)
            .note(trimToNull(input.note(), 1000, "Ghi chú")).startAt(input.startAt()).endAt(input.endAt())
            .requiredWeeks(weeks).sortOrder(order).build();
    }

    private static String trimToNull(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new BadRequestException(field + " tối đa " + max + " ký tự");
        return trimmed;
    }

    private TermPlan view(InternshipTerm term, boolean editable, List<MilestoneItem> items, boolean showDocument) {
        return new TermPlan(term.getId(), term.getTermName(), term.getPlanDocumentRef(), term.getPlanPublishedAt(),
            editable, items, showDocument ? documents.describe(term).orElse(null) : null,
            term.getPlanPublishedAt() == null ? null : news.slug(term).orElse(null));
    }

    private List<MilestoneItem> items(UUID termId) {
        return milestones.findByTermIdOrderBySortOrderAscStartAtAscEndAtAsc(termId).stream()
            .map(m -> new MilestoneItem(m.getId(), m.getKind(), m.getLabel(), m.getNote(), m.getStartAt(),
                m.getEndAt(), m.getRequiredWeeks(), m.getSortOrder(), m.getLatePolicy()))
            .toList();
    }

    private InternshipTerm term(UUID termId) {
        return terms.findById(termId).orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
    }

    private User actor() {
        UUID id = security.currentUser().getId();
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private User requireSchool() {
        var actor = actor();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(actor));
        return Objects.requireNonNull(actor);
    }
}
