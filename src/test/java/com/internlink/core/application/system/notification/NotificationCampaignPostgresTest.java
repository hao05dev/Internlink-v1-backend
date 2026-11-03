package com.internlink.core.application.system.notification;

import static org.assertj.core.api.Assertions.*;

import com.internlink.core.application.system.impl.*;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.*;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import com.internlink.core.infrastructure.persistence.jdbc.*;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.system.dto.request.NotificationCampaignRequest;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@DataJpaTest(
        showSql = false,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "internlink.email.enabled=false"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    NotificationServiceImpl.class,
    EmailOutboxService.class,
    EmailProperties.class,
    EmailRecipientPolicy.class,
    NotificationEmailComposer.class,
    JdbcEmailOutboxStore.class,
    JdbcReminderSource.class,
    com.internlink.core.infrastructure.persistence.jdbc.NotificationEventRepository.class,
    NotificationCampaignRepository.class,
    NotificationCampaignService.class,
    NotificationCampaignDeliveryService.class,
    SecurityGuard.class,
    AuditLogServiceImpl.class,
    NotificationReminderService.class
})
@EnabledIfEnvironmentVariable(named = "INTERNLINK_TEST_PG_URL", matches = ".+")
@DirtiesContext
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationCampaignPostgresTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("INTERNLINK_TEST_PG_URL"),
                schema = "campaign_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(url, "internlink_test", ""))
                .execute("CREATE SCHEMA " + schema);
        properties.add("spring.datasource.url", () -> url + "?currentSchema=" + schema);
        properties.add("spring.datasource.username", () -> "internlink_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.flyway.schemas", () -> schema);
    }

    @Autowired JpaUserRepository users;
    @Autowired JpaDepartmentRepository departments;
    @Autowired JpaAcademicProgramRepository programs;
    @Autowired JpaInternshipTermRepository terms;
    @Autowired JpaStudentRosterRepository rosters;
    @Autowired NotificationCampaignService service;
    @Autowired NotificationCampaignDeliveryService delivery;
    @Autowired NotificationCampaignRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailProperties email;
    @Autowired JdbcReminderSource source;
    @Autowired NotificationReminderService reminders;
    User faculty, student;
    InternshipTerm term;
    OffsetDateTime now;

    @BeforeEach
    void setup() {
        now = OffsetDateTime.now();
        var department = departments.findAll().getFirst();
        faculty =
                users.saveAndFlush(
                        User.builder()
                                .role(UserRole.FACULTY_ADMIN)
                                .department(department)
                                .email(UUID.randomUUID() + "@example.edu")
                                .fullName("Faculty")
                                .passwordHash("test-only")
                                .isActive(true)
                                .build());
        student =
                users.saveAndFlush(
                        User.builder()
                                .role(UserRole.STUDENT)
                                .email(UUID.randomUUID() + "@example.edu")
                                .fullName("Student")
                                .passwordHash("test-only")
                                .isActive(true)
                                .build());
        term =
                terms.saveAndFlush(
                        InternshipTerm.builder()
                                .department(department)
                                .code(UUID.randomUUID().toString())
                                .termName("Kỳ thử")
                                .academicYear("2026")
                                .semester("1")
                                .registrationOpenAt(now.minusDays(1))
                                .registrationCloseAt(now.plusHours(1))
                                .startDate(LocalDate.now())
                                .endDate(LocalDate.now().plusMonths(2))
                                .applicationDeadline(now.plusHours(2))
                                .evaluationDeadline(now.plusMonths(2))
                                .status(TermStatus.REGISTRATION_OPEN)
                                .build());
        var program =
                programs.findAll().stream()
                        .filter(p -> p.getDepartment().getId().equals(department.getId()))
                        .findFirst()
                        .orElseThrow();
        rosters.saveAndFlush(
                StudentRoster.builder()
                        .term(term)
                        .program(program)
                        .studentCode(UUID.randomUUID().toString())
                        .officialEmail(student.getEmail())
                        .fullName(student.getFullName())
                        .academicYear("2026")
                        .claimedUser(student)
                        .build());
        login(faculty);
        email.setEnabled(true);
    }

    @AfterEach
    void cleanup() {
        email.setEnabled(false);
        SecurityContextHolder.clearContext();
        jdbc.execute("DROP TRIGGER IF EXISTS reject_campaign_test ON email_outbox");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_campaign_test()");
    }

    void login(User user) {
        var principal = CustomUserDetail.create(user);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, "", principal.getAuthorities()));
    }

    NotificationCampaignRequest request(String subject, UUID key) {
        return new NotificationCampaignRequest(
                term.getId(),
                key,
                "INTRODUCTION_LETTER",
                "ALL_STUDENTS",
                subject,
                "Chào {{studentName}}, kỳ {{termName}}",
                "BOTH",
                null);
    }

    @Test
    void duplicateCreateReturnsSameCampaignAndDifferentContentIsRejected() {
        var request = request("Giấy giới thiệu", UUID.randomUUID());
        var first = service.create(request);
        assertThat(service.create(request).campaign().id()).isEqualTo(first.campaign().id());
        assertThatThrownBy(() -> service.create(request("Nội dung mới", request.requestKey())))
                .hasMessageContaining("Request key");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notification_campaigns WHERE id=?",
                                Integer.class,
                                first.campaign().id()))
                .isEqualTo(1);
    }

    @Test
    void bothChannelsCommitOnceForEachRecipient() {
        var campaign = service.create(request("Giấy giới thiệu", UUID.randomUUID()));
        var row = repository.claim();
        // Earlier tests may have unprocessed campaigns; claim until reaching this campaign.
        while (row != null && !row.campaignId().equals(campaign.campaign().id())) {
            delivery.process(row);
            row = repository.claim();
        }
        assertThat(row).isNotNull();
        delivery.process(row);
        delivery.process(row);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notifications WHERE recipient_user_id=?",
                                Integer.class,
                                student.getId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM email_outbox WHERE recipient_user_id=?",
                                Integer.class,
                                student.getId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notification_campaign_recipients r JOIN"
                                    + " email_outbox e ON e.id=r.outbox_id AND"
                                    + " e.notification_id=r.notification_id WHERE r.campaign_id=?",
                                Integer.class,
                                campaign.campaign().id()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT delivery_status->>'email' FROM notifications WHERE"
                                    + " recipient_user_id=?",
                                String.class,
                                student.getId()))
                .isEqualTo("QUEUED");
        assertThat(service.detail(campaign.campaign().id(), 0).campaign().status())
                .isEqualTo("COMPLETED");
    }

    @Test
    void emailInsertFailureRollsBackInAppAndCanRetryRecipient() {
        var campaign = service.create(request("Rollback fixture", UUID.randomUUID()));
        jdbc.execute(
                "CREATE FUNCTION reject_campaign_test() RETURNS trigger LANGUAGE plpgsql AS $$"
                    + " BEGIN IF NEW.subject='Rollback fixture' THEN RAISE EXCEPTION 'fixture'; END"
                    + " IF; RETURN NEW; END $$");
        jdbc.execute(
                "CREATE TRIGGER reject_campaign_test BEFORE INSERT ON email_outbox FOR EACH ROW"
                        + " EXECUTE FUNCTION reject_campaign_test()");
        var row = repository.claim();
        while (row != null && !row.campaignId().equals(campaign.campaign().id())) {
            delivery.process(row);
            row = repository.claim();
        }
        assertThat(row).isNotNull();
        delivery.process(row);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notifications WHERE recipient_user_id=?",
                                Integer.class,
                                student.getId()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM email_outbox WHERE recipient_user_id=?",
                                Integer.class,
                                student.getId()))
                .isZero();
        assertThat(service.detail(campaign.campaign().id(), 0).campaign().counts().get("FAILED"))
                .isEqualTo(1);
        jdbc.execute("DROP TRIGGER reject_campaign_test ON email_outbox");
        service.retry(campaign.campaign().id());
        delivery.process(repository.claim());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notifications WHERE recipient_user_id=?",
                                Integer.class,
                                student.getId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM email_outbox WHERE recipient_user_id=?",
                                Integer.class,
                                student.getId()))
                .isEqualTo(1);
    }

    @Test
    void closedTermAndFacultyFromAnotherDepartmentCannotCreateCampaign() {
        var other =
                departments.saveAndFlush(
                        Department.builder()
                                .code(UUID.randomUUID().toString())
                                .name("Other department")
                                .contactEmail("other@example.edu")
                                .isActive(true)
                                .build());
        faculty.setDepartment(other);
        faculty = users.saveAndFlush(faculty);
        assertThatThrownBy(() -> service.create(request("Thông báo", UUID.randomUUID())))
                .isInstanceOf(com.internlink.core.shared.exception.ForbiddenException.class);
        faculty.setDepartment(term.getDepartment());
        faculty = users.saveAndFlush(faculty);
        term.setStatus(TermStatus.CLOSED);
        term = terms.saveAndFlush(term);
        assertThatThrownBy(() -> service.create(request("Thông báo", UUID.randomUUID())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void remindersDeduplicateAndRecheckChangedDeadlineOrClosedTerm() {
        var candidate =
                source.pending(now).stream()
                        .filter(
                                c ->
                                        c.recipientId().equals(student.getId())
                                                && c.type().equals("APPLICATION_DEADLINE"))
                        .findFirst()
                        .orElseThrow();
        assertThat(source.stillPending(candidate, now)).isTrue();
        reminders.run(now);
        reminders.run(now);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notifications WHERE recipient_user_id=?",
                                Integer.class,
                                student.getId()))
                .isEqualTo(1);
        term.setRegistrationCloseAt(now.plusHours(3));
        term = terms.saveAndFlush(term);
        assertThat(source.stillPending(candidate, now)).isFalse();
        var changed =
                source.pending(now).stream()
                        .filter(c -> c.recipientId().equals(student.getId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(changed.key()).isNotEqualTo(candidate.key());
        term.setStatus(TermStatus.CLOSED);
        term = terms.saveAndFlush(term);
        assertThat(source.stillPending(changed, now)).isFalse();
    }
}
