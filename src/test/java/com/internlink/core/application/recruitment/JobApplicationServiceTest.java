package com.internlink.core.application.recruitment;

import com.internlink.core.application.recruitment.impl.JobApplicationServiceImpl;
import com.internlink.core.application.recruitment.impl.InterviewInvitationNotifier;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.recruitment.dto.request.RecordInterviewResultRequest;
import com.internlink.core.presentation.recruitment.dto.request.ScheduleInterviewRequest;
import com.internlink.core.presentation.recruitment.dto.response.JobApplicationResponse;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock private JpaJobApplicationRepository applicationRepository;
    @Mock private JpaJobPositionRepository jobPositionRepository;
    @Mock private JpaUserRepository userRepository;
    @Mock private JpaDocumentRepository documentRepository;
    @Mock private JpaStudentProfileRepository studentProfileRepository;
    @Mock private JpaStudentRosterRepository studentRosterRepository;
    @Mock private JpaPlacementOfferRepository offerRepository;
    @Mock private SecurityGuard securityGuard;
    @Mock private NotificationService notifications;

    private JobApplicationServiceImpl service;

    private User companyUser;
    private User studentUser;
    private Company company;
    private Department department;
    private InternshipTerm term;
    private JobPosition job;
    private Document cvDocument;
    private JobApplication application;

    @BeforeEach
    void setUp() {
        service = new JobApplicationServiceImpl(
            applicationRepository,
            jobPositionRepository,
            userRepository,
            securityGuard,
            notifications,
            new com.internlink.core.shared.api.PaginatedResponseMapper(),
            new InterviewInvitationNotifier(
                notifications,
                null,
                null,
                new com.internlink.core.infrastructure.integration.email.EmailProperties()),
            new com.internlink.core.application.recruitment.impl.JobApplicationSubmissionValidator(
                applicationRepository, documentRepository, studentProfileRepository,
                studentRosterRepository, offerRepository),
            new com.internlink.core.application.recruitment.impl.JobApplicationMapper()
        );

        department = Department.builder().name("Khoa CNTT").code("DI").build();
        department.setId(UUID.randomUUID());

        company = Company.builder().companyName("FPT Software").build();
        company.setId(UUID.randomUUID());

        companyUser = User.builder()
            .fullName("HR FPT")
            .email("hr@fpt.com")
            .role(UserRole.COMPANY_REP)
            .company(company)
            .build();
        companyUser.setId(UUID.randomUUID());

        studentUser = User.builder()
            .fullName("Nguyen Van An")
            .email("an@student.ctu.edu.vn")
            .role(UserRole.STUDENT)
            .build();
        studentUser.setId(UUID.randomUUID());

        term = InternshipTerm.builder()
            .code("HK1-2026-2027")
            .termName("Học kỳ 1 2026-2027")
            .status(TermStatus.ACTIVE)
            .build();
        term.setId(UUID.randomUUID());

        job = JobPosition.builder()
            .title("Java Backend Intern")
            .company(company)
            .term(term)
            .vacancies(5)
            .workFormat(WorkFormat.HYBRID)
            .location("Can Tho")
            .description("Backend development")
            .status(JobStatus.APPROVED)
            .build();
        job.setId(UUID.randomUUID());

        cvDocument = Document.builder()
            .owner(studentUser)
            .documentType(DocumentType.CV)
            .build();
        cvDocument.setId(UUID.randomUUID());

        application = JobApplication.builder()
            .job(job)
            .student(studentUser)
            .submittedCvDocument(cvDocument)
            .coverLetter("Em rất mong muốn được thực tập")
            .status(ApplicationStatus.REVIEWING)
            .interviewRounds(new ArrayList<>())
            .build();
        application.setId(UUID.randomUUID());
    }

    private void mockCurrentActor(User actor) {
        CustomUserDetail userDetail = mock(CustomUserDetail.class);
        when(userDetail.getId()).thenReturn(actor.getId());
        when(securityGuard.currentUser()).thenReturn(userDetail);
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
    }

    @Test
    @DisplayName("Lên lịch phỏng vấn Online thành công với Google Meet link")
    void scheduleInterview_online_success() {
        mockCurrentActor(companyUser);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(i -> i.getArgument(0));

        ScheduleInterviewRequest request = ScheduleInterviewRequest.builder()
            .format(InterviewFormat.ONLINE)
            .scheduledAt(OffsetDateTime.now().plusDays(3))
            .meetingUrl("https://meet.google.com/abc-defg-hij")
            .interviewer("Nguyen Van Tech Lead")
            .notes("Chuẩn bị demo sản phẩm cá nhân")
            .build();

        JobApplicationResponse response = service.scheduleInterview(application.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        assertThat(response.getInterviewRounds()).hasSize(1);
        assertThat(response.getInterviewRounds().get(0).getFormat()).isEqualTo(InterviewFormat.ONLINE);
        assertThat(response.getInterviewRounds().get(0).getMeetingUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(response.getInterviewRounds().get(0).getResult()).isEqualTo(InterviewRoundResult.PENDING);

        verify(notifications).sendInAppNotification(
            eq(studentUser.getId()),
            eq("INTERVIEW_SCHEDULED"),
            contains("Java Backend Intern"),
            contains("https://meet.google.com/abc-defg-hij"),
            eq("/student/applications")
        );
    }

    @Test
    @DisplayName("Lên lịch phỏng vấn Online lỗi khi thiếu meeting URL")
    void scheduleInterview_online_missingMeetingUrl_throwsException() {
        mockCurrentActor(companyUser);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        ScheduleInterviewRequest request = ScheduleInterviewRequest.builder()
            .format(InterviewFormat.ONLINE)
            .scheduledAt(OffsetDateTime.now().plusDays(2))
            .meetingUrl("")
            .build();

        assertThatThrownBy(() -> service.scheduleInterview(application.getId(), request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("liên kết phòng họp");
    }

    @Test
    @DisplayName("Doanh nghiệp khác không thể lên lịch phỏng vấn hồ sơ của công ty này")
    void scheduleInterview_forbiddenOtherCompany() {
        Company otherCompany = Company.builder().companyName("Other Corp").build();
        otherCompany.setId(UUID.randomUUID());

        User otherCompanyUser = User.builder()
            .fullName("Other HR")
            .role(UserRole.COMPANY_REP)
            .company(otherCompany)
            .build();
        otherCompanyUser.setId(UUID.randomUUID());

        mockCurrentActor(otherCompanyUser);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        ScheduleInterviewRequest request = ScheduleInterviewRequest.builder()
            .format(InterviewFormat.ONLINE)
            .scheduledAt(OffsetDateTime.now().plusDays(2))
            .meetingUrl("https://meet.google.com/abc-defg-hij")
            .build();

        assertThatThrownBy(() -> service.scheduleInterview(application.getId(), request))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Ghi nhận kết quả phỏng vấn ĐẠT thành công")
    void recordInterviewResult_passed_success() {
        mockCurrentActor(companyUser);

        ScheduleInterviewRequest request = ScheduleInterviewRequest.builder()
            .format(InterviewFormat.ONLINE)
            .scheduledAt(OffsetDateTime.now().plusDays(1))
            .meetingUrl("https://meet.google.com/abc-defg-hij")
            .build();

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(i -> i.getArgument(0));

        service.scheduleInterview(application.getId(), request);

        RecordInterviewResultRequest resultRequest = RecordInterviewResultRequest.builder()
            .result(InterviewRoundResult.PASSED)
            .feedback("Ứng viên nắm tốt kiến trúc Spring Boot và Clean Architecture")
            .build();

        JobApplicationResponse response = service.recordInterviewResult(application.getId(), 1, resultRequest);

        assertThat(response).isNotNull();
        assertThat(response.getInterviewRounds().get(0).getResult()).isEqualTo(InterviewRoundResult.PASSED);
        assertThat(response.getInterviewRounds().get(0).getFeedback()).contains("Clean Architecture");
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
    }

    @Test
    @DisplayName("Ghi nhận kết quả phỏng vấn KHÔNG ĐẠT tự động chuyển trạng thái REJECTED")
    void recordInterviewResult_failed_updatesStatusToRejected() {
        mockCurrentActor(companyUser);

        ScheduleInterviewRequest request = ScheduleInterviewRequest.builder()
            .format(InterviewFormat.ONLINE)
            .scheduledAt(OffsetDateTime.now().plusDays(1))
            .meetingUrl("https://meet.google.com/abc-defg-hij")
            .build();

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(i -> i.getArgument(0));

        service.scheduleInterview(application.getId(), request);

        RecordInterviewResultRequest resultRequest = RecordInterviewResultRequest.builder()
            .result(InterviewRoundResult.FAILED)
            .feedback("Ứng viên chưa nắm vững kiến thức cơ bản")
            .build();

        JobApplicationResponse response = service.recordInterviewResult(application.getId(), 1, resultRequest);

        assertThat(response).isNotNull();
        assertThat(response.getInterviewRounds().get(0).getResult()).isEqualTo(InterviewRoundResult.FAILED);
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("Nộp đơn vào vị trí đã hết hạn nộp hồ sơ bị từ chối với BadRequestException")
    void applyJob_whenJobExpired_throwsBadRequestException() {
        job.setApplicationDeadline(OffsetDateTime.now().minusDays(1)); // Hết hạn hôm qua

        when(userRepository.findById(studentUser.getId())).thenReturn(Optional.of(studentUser));
        when(jobPositionRepository.findById(job.getId())).thenReturn(Optional.of(job));

        com.internlink.core.presentation.recruitment.dto.request.JobApplicationRequest request =
            com.internlink.core.presentation.recruitment.dto.request.JobApplicationRequest.builder()
                .jobId(job.getId())
                .submittedCvDocumentId(cvDocument.getId())
                .build();

        assertThatThrownBy(() -> service.applyJob(studentUser.getId(), request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("đã hết hạn nộp hồ sơ");
    }

    @Test
    @DisplayName("Nộp đơn vào vị trí đã đóng nhận hồ sơ sớm bị từ chối với BadRequestException")
    void applyJob_whenJobClosedEarly_throwsBadRequestException() {
        job.setClosedEarlyAt(OffsetDateTime.now().minusHours(2));

        when(userRepository.findById(studentUser.getId())).thenReturn(Optional.of(studentUser));
        when(jobPositionRepository.findById(job.getId())).thenReturn(Optional.of(job));

        com.internlink.core.presentation.recruitment.dto.request.JobApplicationRequest request =
            com.internlink.core.presentation.recruitment.dto.request.JobApplicationRequest.builder()
                .jobId(job.getId())
                .submittedCvDocumentId(cvDocument.getId())
                .build();

        assertThatThrownBy(() -> service.applyJob(studentUser.getId(), request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("đã đóng nhận hồ sơ");
    }
}
