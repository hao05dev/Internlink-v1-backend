package com.internlink.core.application.placement;

import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.*;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.placement.dto.request.StudentFoundRequest;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Phía sinh viên của Phiếu tiếp nhận: điền Form, đính kèm bản scan, nộp và theo dõi. */
@Service @RequiredArgsConstructor
public class StudentFoundApplicationService {
    private final JpaStudentFoundApplicationRepository applications;
    private final JpaInternshipPlacementRepository placements;
    private final JpaStudentRosterRepository rosters;
    private final JpaInternshipTermRepository terms;
    private final JpaDocumentRepository documents;
    private final JpaLearningAgreementRepository agreements;
    private final JpaJobPositionRepository jobs;
    private final AcceptanceFormLookup lookup;
    private final AcceptanceFormMapper mapper;
    private final AcceptanceFormNotifier notifier;

    @Transactional
    public StudentFoundResponse create(StudentFoundRequest request) {
        var student = lookup.actor();
        ResourceAuthorization.require(student.getRole() == UserRole.STUDENT);
        var term = terms.findById(request.termId())
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", request.termId()));
        TermGuard.requireNotClosed(term);
        var agreement = request.agreementId() == null ? null : requireUsableAgreement(request.agreementId(), student, term);
        if (agreement == null && term.getStatus() != TermStatus.REGISTRATION_OPEN && term.getStatus() != TermStatus.APPLICATION_OPEN)
            throw new BadRequestException("Kỳ thực tập chưa mở hoặc đã đóng đăng ký nơi tự tìm");
        var roster = requireRoster(term, student);
        if (applications.findByTermIdAndStudentId(term.getId(), student.getId()).isPresent()
            || !placements.findByTermIdAndStudentId(term.getId(), student.getId()).isEmpty())
            throw new BadRequestException("Sinh viên đã có Phiếu tiếp nhận hoặc lần thực tập trong kỳ");
        requireValidPeriod(request, term, roster);
        var app = StudentFoundApplication.builder().term(term).student(student)
            .source(agreement != null ? AcceptanceFormSource.SYSTEM_OFFER : AcceptanceFormSource.SELF_FOUND)
            .agreement(agreement).status(AcceptanceFormStatus.DRAFT).build();
        app.setSourceJob(resolveSourceJob(request));
        applyForm(app, request);
        return mapper.toResponse(applications.save(app));
    }

    @Transactional
    public StudentFoundResponse update(UUID id, StudentFoundRequest request) {
        var app = lookup.get(id);
        TermGuard.requireNotClosed(app.getTerm());
        ResourceAuthorization.require(lookup.actor().getId().equals(app.getStudent().getId()));
        if (!AcceptanceFormStatus.EDITABLE.contains(app.getStatus()))
            throw new BadRequestException("Phiếu đã nộp hoặc đã duyệt, không thể sửa");
        UUID currentAgreement = app.getAgreement() != null ? app.getAgreement().getId() : null;
        if (!app.getTerm().getId().equals(request.termId()) || !Objects.equals(currentAgreement, request.agreementId()))
            throw new BadRequestException("Không thể đổi kỳ hoặc nguồn của Phiếu tiếp nhận");
        requireValidPeriod(request, app.getTerm(), requireRoster(app.getTerm(), app.getStudent()));
        app.setSourceJob(resolveSourceJob(request));
        applyForm(app, request);
        app.setStatus(AcceptanceFormStatus.DRAFT);
        app.setAcceptanceDocument(null);
        app.setReviewNote(null);
        return mapper.toResponse(applications.save(app));
    }

    @Transactional
    public StudentFoundResponse submit(UUID id, UUID documentId) {
        var app = lookup.get(id);
        TermGuard.requireNotClosed(app.getTerm());
        ResourceAuthorization.require(lookup.actor().getId().equals(app.getStudent().getId()));
        if (!AcceptanceFormStatus.DRAFT.equals(app.getStatus()) && !AcceptanceFormStatus.REVISION_REQUIRED.equals(app.getStatus()))
            throw new BadRequestException("Phiếu không ở trạng thái được nộp");
        Document document = documents.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
        if (document.getContextType() != ContextType.SELF_FOUND || !document.getContextId().equals(id)
            || document.getDocumentType() != DocumentType.ACCEPTANCE_LETTER
            || !document.getOwner().getId().equals(app.getStudent().getId()) || !"ACTIVE".equals(document.getStatus()))
            throw new BadRequestException("Bản scan Phiếu tiếp nhận phải là tài liệu hợp lệ của phiếu này");
        app.setAcceptanceDocument(document);
        app.setStatus(AcceptanceFormStatus.SUBMITTED);
        app.setReviewNote(null);
        app.setReceivedAt(null);
        app.setReceivedBy(null);
        var saved = applications.save(app);
        notifier.submitted(saved);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StudentFoundResponse> mine() {
        return applications.findByStudentId(lookup.actor().getId()).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StudentFoundResponse byId(UUID id) {
        var app = lookup.get(id);
        var user = lookup.actor();
        ResourceAuthorization.require(user.getId().equals(app.getStudent().getId())
            || ResourceAuthorization.actsForSchool(user)
            || ResourceAuthorization.managesDepartment(user, DepartmentScope.idOf(app))
            || user.getRole() == UserRole.LECTURER && isSupervisedBy(app, user));
        return mapper.toResponse(app);
    }

    private boolean isSupervisedBy(StudentFoundApplication app, User lecturer) {
        var placement = app.getAgreement() != null
            ? placements.findByAgreementId(app.getAgreement().getId())
            : placements.findByStudentFoundApplicationId(app.getId());
        return placement.map(p -> p.getLecturer() != null && p.getLecturer().getId().equals(lecturer.getId())).orElse(false);
    }

    private LearningAgreement requireUsableAgreement(UUID agreementId, User student, InternshipTerm term) {
        var agreement = agreements.findById(agreementId)
            .orElseThrow(() -> new ResourceNotFoundException("LearningAgreement", "id", agreementId));
        ResourceAuthorization.require(agreement.getStudent().getId().equals(student.getId()));
        if (agreement.getStatus() != AgreementStatus.APPROVED)
            throw new BadRequestException("Thỏa thuận 3 bên phải được ký đủ trước khi nộp Phiếu tiếp nhận");
        var job = agreement.getOffer() != null && agreement.getOffer().getApplication() != null
            ? agreement.getOffer().getApplication().getJob() : null;
        if (job == null || job.getTerm() == null || !job.getTerm().getId().equals(term.getId()))
            throw new BadRequestException("Thỏa thuận không thuộc kỳ thực tập đã chọn");
        if (applications.findByAgreementId(agreementId).isPresent())
            throw new BadRequestException("Thỏa thuận này đã có Phiếu tiếp nhận");
        return agreement;
    }

    /** Tin nguồn chỉ dành cho nơi tự tìm, và phải là tin EXTERNAL đã công bố. */
    private JobPosition resolveSourceJob(StudentFoundRequest request) {
        if (request.sourceJobId() == null) return null;
        if (request.agreementId() != null)
            throw new BadRequestException("Phiếu từ thỏa thuận trong hệ thống không gắn tin ứng tuyển ngoài");
        var job = jobs.findById(request.sourceJobId())
            .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", request.sourceJobId()));
        if (!job.isExternalApply() || (job.getStatus() != JobStatus.APPROVED && job.getStatus() != JobStatus.CLOSED))
            throw new BadRequestException("Tin nguồn phải là tin ứng tuyển ngoài hệ thống đã được công bố");
        return job;
    }

    private StudentRoster requireRoster(InternshipTerm term, User student) {
        var roster = rosters.findByTermIdAndClaimedUserId(term.getId(), student.getId())
            .orElseThrow(() -> new BadRequestException("Sinh viên chưa có tên trong danh sách học phần thực tập của kỳ"));
        if (roster.getInternshipCourseCode() == null || roster.getInternshipCourseCode().isBlank())
            throw new BadRequestException("Danh sách sinh viên chưa có mã học phần thực tập");
        return roster;
    }

    private static void requireValidPeriod(StudentFoundRequest request, InternshipTerm term, StudentRoster roster) {
        if (request.startDate().isAfter(request.endDate()) || request.startDate().isBefore(term.getStartDate())
            || request.endDate().isAfter(term.getEndDate()))
            throw new BadRequestException("Thời gian thực tập không nằm trong kỳ");
        InternshipDurationRule.requireDuration(request.startDate(), request.endDate(), roster.getInternshipCredits());
    }

    private static void applyForm(StudentFoundApplication app, StudentFoundRequest request) {
        app.setHostName(request.hostName().trim());
        app.setHostAddress(request.hostAddress().trim());
        app.setContactName(request.contactName().trim());
        app.setContactEmail(request.contactEmail().trim());
        app.setWorkDescription(request.workDescription().trim());
        app.setStartDate(request.startDate());
        app.setEndDate(request.endDate());
    }
}
