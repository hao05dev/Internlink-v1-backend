package com.internlink.core.application.placement;

import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.*;
import com.internlink.core.domain.placement.*;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.placement.dto.request.AcceptanceFormDetails;
import com.internlink.core.presentation.placement.dto.request.StudentFoundRequest;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentFoundApplicationServiceTest {
    @Mock JpaStudentFoundApplicationRepository applications;
    @Mock JpaInternshipPlacementRepository placements;
    @Mock JpaStudentRosterRepository rosters;
    @Mock JpaInternshipTermRepository terms;
    @Mock JpaDocumentRepository documents;
    @Mock JpaLearningAgreementRepository agreements;
    @Mock JpaJobPositionRepository jobs;
    @Mock JpaUserRepository users;
    @Mock SecurityGuard security;
    @Mock NotificationService notifications;
    @Mock InternshipPlacementService placementService;
    @Mock JpaTermMilestoneRepository milestones;

    StudentFoundApplicationService studentService;
    AcceptanceFormOfficeService officeService;
    AcceptanceFormReviewService reviewService;
    Department department;
    InternshipTerm term;
    User student;
    User facultyAdmin;
    User schoolOffice;
    User lecturer;
    StudentRoster roster;

    @BeforeEach
    void setUp() {
        var lookup = new AcceptanceFormLookup(applications, users, security);
        var mapper = new AcceptanceFormMapper(placements, rosters);
        var notifier = new AcceptanceFormNotifier(notifications, users);
        var eligibility = new PlacementEligibilityService(placements, rosters, null);
        studentService = new StudentFoundApplicationService(applications, placements, rosters, terms, documents,
            agreements, jobs, lookup, mapper, notifier, new AcceptanceFormDeadline(milestones));
        officeService = new AcceptanceFormOfficeService(applications, lookup, mapper, notifier);
        reviewService = new AcceptanceFormReviewService(applications, placements, rosters, terms, users,
            eligibility, placementService, lookup, mapper, notifier,
            new com.internlink.core.application.organization.TermDepartmentScope(rosters, placements, applications));

        department = Department.builder().code("CICT").name("CNTT&TT").build();
        department.setId(UUID.randomUUID());
        student = user(UserRole.STUDENT, department);
        facultyAdmin = user(UserRole.FACULTY_ADMIN, department);
        schoolOffice = user(UserRole.SCHOOL_OFFICE, null);
        lecturer = user(UserRole.LECTURER, department);
        term = InternshipTerm.builder().status(TermStatus.APPLICATION_OPEN).termName("HK1")
            .startDate(LocalDate.of(2026, 9, 7)).endDate(LocalDate.of(2026, 12, 31)).build();
        term.setId(UUID.randomUUID());
        roster = StudentRoster.builder().term(term).claimedUser(student).assignedLecturer(lecturer)
            .studentCode("B2100001").internshipCourseCode("CT471").internshipCredits(3).build();
        when(terms.findById(term.getId())).thenReturn(Optional.of(term));
        when(rosters.findByTermIdAndClaimedUserId(term.getId(), student.getId())).thenReturn(Optional.of(roster));
        when(applications.save(any())).thenAnswer(invocation -> {
            StudentFoundApplication value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
        when(placements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findByRole(UserRole.SCHOOL_OFFICE)).thenReturn(List.of(schoolOffice));
    }

    @Test
    void selfFoundFormGoesThroughSchoolOfficeThenFacultyThenPublication() {
        var app = createAndSubmit();
        assertThat(app.getStatus()).isEqualTo(AcceptanceFormStatus.SUBMITTED);
        verify(notifications).sendNotification(eq(schoolOffice.getId()), eq("ACCEPTANCE_FORM_SUBMITTED"),
            anyString(), anyString(), eq("/school-office/acceptance-forms"));

        actAs(facultyAdmin);
        assertThatThrownBy(() -> reviewService.review(app.getId(), "APPROVED", null, null))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Văn phòng Trường");

        actAs(schoolOffice);
        officeService.confirmReceived(app.getId());
        assertThat(app.getStatus()).isEqualTo(AcceptanceFormStatus.RECEIVED);
        assertThat(app.getReceivedBy()).isEqualTo(schoolOffice);

        actAs(facultyAdmin);
        reviewService.review(app.getId(), "APPROVED", null, null);
        assertThat(app.getStatus()).isEqualTo(AcceptanceFormStatus.APPROVED);
        ArgumentCaptor<InternshipPlacement> placement = ArgumentCaptor.forClass(InternshipPlacement.class);
        verify(placements).save(placement.capture());
        assertThat(placement.getValue().getSource()).isEqualTo("STUDENT_FOUND");
        assertThat(placement.getValue().getCompany()).isNull();
        assertThat(placement.getValue().getLecturer()).isEqualTo(lecturer);

        actAs(schoolOffice);
        assertThatThrownBy(() -> officeService.confirmDocumentsCollected(app.getId()))
            .isInstanceOf(BadRequestException.class);

        actAs(facultyAdmin);
        when(applications.findByTermIdAndStatusAndStudentDepartmentId(term.getId(), AcceptanceFormStatus.APPROVED, department.getId())).thenReturn(List.of(app));
        assertThat(reviewService.publish(term.getId())).isEqualTo(1);
        assertThat(app.getStatus()).isEqualTo(AcceptanceFormStatus.PUBLISHED);

        actAs(schoolOffice);
        officeService.confirmDocumentsCollected(app.getId());
        assertThat(app.getDocumentsCollectedAt()).isNotNull();
    }

    @Test
    void submittingAfterThePublishedDeadlineIsAllowedButMarkedLate() {
        publishPlanWithDeadline(java.time.OffsetDateTime.now().minusDays(2), TermMilestone.ALLOW_LATE);
        var app = createAndSubmit();
        assertThat(app.getStatus()).isEqualTo(AcceptanceFormStatus.SUBMITTED);
        assertThat(app.isSubmittedLate()).isTrue();
        assertThat(app.getSubmittedAt()).isNotNull();
    }

    @Test
    void submittingBeforeTheDeadlineIsOnTime() {
        publishPlanWithDeadline(java.time.OffsetDateTime.now().plusDays(2), TermMilestone.LOCK);
        var app = createAndSubmit();
        assertThat(app.isSubmittedLate()).isFalse();
    }

    @Test
    void lockedDeadlineRejectsLateSubmission() {
        publishPlanWithDeadline(java.time.OffsetDateTime.now().minusDays(2), TermMilestone.LOCK);
        assertThatThrownBy(this::createAndSubmit)
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("không nhận nộp trễ");
    }

    @Test
    void unpublishedPlanDoesNotAffectSubmission() {
        publishPlanWithDeadline(java.time.OffsetDateTime.now().minusDays(2), TermMilestone.LOCK);
        term.setPlanPublishedAt(null);
        assertThat(createAndSubmit().isSubmittedLate()).isFalse();
    }

    @Test
    void resubmissionAfterRevisionKeepsTheFirstSubmissionVerdict() {
        var app = createAndSubmit();
        var firstSubmittedAt = app.getSubmittedAt();
        publishPlanWithDeadline(java.time.OffsetDateTime.now().minusDays(2), TermMilestone.LOCK);
        app.setStatus(AcceptanceFormStatus.REVISION_REQUIRED);
        var scan = activeDocument(app.getId());
        when(documents.findById(scan.getId())).thenReturn(Optional.of(scan));
        studentService.submit(app.getId(), scan.getId());
        assertThat(app.getStatus()).isEqualTo(AcceptanceFormStatus.SUBMITTED);
        assertThat(app.isSubmittedLate()).isFalse();
        assertThat(app.getSubmittedAt()).isEqualTo(firstSubmittedAt);
    }

    @Test
    void facultyAdminCannotConfirmThePaperCopy() {
        var app = createAndSubmit();
        actAs(facultyAdmin);
        assertThatThrownBy(() -> officeService.confirmReceived(app.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void schoolOfficeReturnsMismatchedPaperForRevision() {
        var app = createAndSubmit();
        actAs(schoolOffice);
        assertThatThrownBy(() -> officeService.returnToStudent(app.getId(), " ")).isInstanceOf(BadRequestException.class);
        officeService.returnToStudent(app.getId(), "Thiếu dấu mộc đơn vị");
        assertThat(app.getStatus()).isEqualTo(AcceptanceFormStatus.REVISION_REQUIRED);
        assertThat(app.getReviewNote()).isEqualTo("Thiếu dấu mộc đơn vị");
    }

    @Test
    void threeCreditCourseRequiresEightWeeks() {
        actAs(student);
        var tooShort = request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 9), null);
        assertThatThrownBy(() -> studentService.create(tooShort))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("8 tuần");
    }

    @Test
    void fewerThanSixSessionsPerWeekIsRejected() {
        actAs(student);
        var tooFew = withDetails(new AcceptanceFormDetails("Cần Thơ", null, null, 5, true, true, null, null));
        assertThatThrownBy(() -> studentService.create(tooFew))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("6 buổi/tuần");
    }

    @Test
    void googleFormFieldsAreStoredAndReturned() {
        actAs(student);
        var details = new AcceptanceFormDetails(" Cần Thơ ", "0292 123", "0901 000 111", 10, true, false,
            List.of("Tìm hiểu dự án", " Viết API ", "", ""), " Phụ cấp ăn trưa ");

        var created = studentService.create(withDetails(details));

        assertThat(created.details().hostProvince()).isEqualTo("Cần Thơ");
        assertThat(created.details().sessionsPerWeek()).isEqualTo(10);
        assertThat(created.details().hasComputer()).isFalse();
        assertThat(created.details().weeklyPlan()).containsExactly("Tìm hiểu dự án", "Viết API");
        assertThat(created.details().otherSupport()).isEqualTo("Phụ cấp ăn trưa");
        assertThat(created.courseCode()).isEqualTo("CT471");
    }

    @Test
    void selfFoundFormRecordsTheExternalJobItCameFrom() {
        var job = job(ApplicationMethod.EXTERNAL, JobStatus.CLOSED);
        actAs(student);

        var created = studentService.create(withSource(job.getId()));

        assertThat(created.sourceJobId()).isEqualTo(job.getId());
        assertThat(created.sourceJobTitle()).isEqualTo("Thực tập sinh Java");
        assertThat(created.sourceJobCompanyName()).isEqualTo("Công ty Ngoài");
        assertThat(created.source()).isEqualTo(AcceptanceFormSource.SELF_FOUND);
    }

    @Test
    void sourceJobMustBeAPublishedExternalJob() {
        var inApp = job(ApplicationMethod.IN_APP, JobStatus.APPROVED);
        var draft = job(ApplicationMethod.EXTERNAL, JobStatus.DRAFT);
        actAs(student);

        assertThatThrownBy(() -> studentService.create(withSource(inApp.getId())))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("ứng tuyển ngoài");
        assertThatThrownBy(() -> studentService.create(withSource(draft.getId())))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("ứng tuyển ngoài");
    }

    @Test
    void systemOfferApprovalActivatesPlacementFromAgreement() {
        var agreement = LearningAgreement.builder().student(student).status(AgreementStatus.APPROVED).build();
        agreement.setId(UUID.randomUUID());
        var app = StudentFoundApplication.builder().term(term).student(student).hostName("Công ty A")
            .source(AcceptanceFormSource.SYSTEM_OFFER).agreement(agreement)
            .status(AcceptanceFormStatus.RECEIVED).acceptanceDocument(activeDocument(UUID.randomUUID())).build();
        app.setId(UUID.randomUUID());
        when(applications.findById(app.getId())).thenReturn(Optional.of(app));
        actAs(facultyAdmin);

        reviewService.review(app.getId(), "APPROVED", null, lecturer.getId());

        verify(placementService).activatePlacementFromAgreement(agreement.getId(), lecturer.getId());
        verify(placements, never()).save(any());
        assertThat(app.getStatus()).isEqualTo(AcceptanceFormStatus.APPROVED);
    }

    private void publishPlanWithDeadline(java.time.OffsetDateTime dueAt, String policy) {
        term.setPlanPublishedAt(java.time.OffsetDateTime.now().minusDays(30));
        var due = TermMilestone.builder().term(term).kind(TermMilestone.ACCEPTANCE_FORM_DUE).label("Nộp phiếu")
            .endAt(dueAt).latePolicy(policy).build();
        when(milestones.findByTermIdOrderBySortOrderAscStartAtAscEndAtAsc(term.getId())).thenReturn(List.of(due));
    }

    private StudentFoundApplication createAndSubmit() {
        actAs(student);
        var created = studentService.create(request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 30), null));
        ArgumentCaptor<StudentFoundApplication> saved = ArgumentCaptor.forClass(StudentFoundApplication.class);
        verify(applications).save(saved.capture());
        var app = saved.getValue();
        assertThat(app.getId()).isEqualTo(created.id());
        when(applications.findById(app.getId())).thenReturn(Optional.of(app));
        var scan = activeDocument(app.getId());
        when(documents.findById(scan.getId())).thenReturn(Optional.of(scan));
        studentService.submit(app.getId(), scan.getId());
        return app;
    }

    private StudentFoundRequest request(LocalDate start, LocalDate end, UUID agreementId) {
        return new StudentFoundRequest(term.getId(), "Đơn vị X", "Cần Thơ", "Người nhận",
            "host@example.com", "Làm dự án", start, end, agreementId);
    }

    private StudentFoundRequest withDetails(AcceptanceFormDetails details) {
        return new StudentFoundRequest(term.getId(), "Đơn vị X", "Cần Thơ", "Người nhận", "host@example.com",
            "Làm dự án", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 30), null, null, details);
    }

    private StudentFoundRequest withSource(UUID sourceJobId) {
        return new StudentFoundRequest(term.getId(), "Công ty Ngoài", "Cần Thơ", "Người nhận",
            "host@example.com", "Làm dự án", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 30), null, sourceJobId);
    }

    private com.internlink.core.domain.recruitment.JobPosition job(ApplicationMethod method, JobStatus status) {
        var company = com.internlink.core.domain.company.Company.builder().companyName("Công ty Ngoài").build();
        company.setId(UUID.randomUUID());
        var job = com.internlink.core.domain.recruitment.JobPosition.builder().company(company)
            .title("Thực tập sinh Java").applicationMethod(method).status(status).build();
        job.setId(UUID.randomUUID());
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    private Document activeDocument(UUID contextId) {
        var document = Document.builder().owner(student).contextType(ContextType.SELF_FOUND).contextId(contextId)
            .documentType(DocumentType.ACCEPTANCE_LETTER).status("ACTIVE").build();
        document.setId(UUID.randomUUID());
        return document;
    }

    private User user(UserRole role, Department dept) {
        var value = User.builder().role(role).department(dept).fullName(role.name()).email(role.name() + "@ctu.edu.vn").build();
        value.setId(UUID.randomUUID());
        when(users.findById(value.getId())).thenReturn(Optional.of(value));
        return value;
    }

    private void actAs(User actor) {
        when(security.currentUser()).thenReturn(CustomUserDetail.create(actor));
    }
}
