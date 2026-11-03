package com.internlink.core.application.system.notification;

import static org.assertj.core.api.Assertions.*;

import com.internlink.core.application.system.impl.NotificationServiceImpl;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.UserRole;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

import java.util.UUID;

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
    com.internlink.core.infrastructure.persistence.jdbc.JdbcEmailOutboxStore.class,
    com.internlink.core.infrastructure.persistence.jdbc.JdbcReminderSource.class,
    com.internlink.core.infrastructure.persistence.jdbc.NotificationEventRepository.class,
    com.internlink.core.infrastructure.persistence.jdbc.NotificationCampaignRepository.class,
    com.internlink.core.infrastructure.persistence.jdbc.UploadTicketRepository.class
})
@EnabledIfEnvironmentVariable(named = "INTERNLINK_TEST_PG_URL", matches = ".+")
@DirtiesContext
class NotificationJpaPostgresTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("INTERNLINK_TEST_PG_URL");
        String schema = "notification_jpa_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(url, "internlink_test", ""))
                .execute("CREATE SCHEMA " + schema);
        properties.add("spring.datasource.url", () -> url + "?currentSchema=" + schema);
        properties.add("spring.datasource.username", () -> "internlink_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.flyway.schemas", () -> schema);
    }

    @Autowired JpaUserRepository users;
    @Autowired NotificationServiceImpl service;
    @Autowired EmailProperties settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.internlink.core.infrastructure.persistence.jdbc.JdbcReminderSource reminders;

    @Autowired
    com.internlink.core.infrastructure.persistence.jdbc.NotificationCampaignAudienceRepository campaigns;

    @Autowired com.internlink.core.infrastructure.persistence.jdbc.UploadTicketRepository tickets;

    @Test
    void queriesUseTheMigratedSchema() {
        var now = java.time.OffsetDateTime.now();
        var rows = reminders.pending(now);
        for (var row : rows) assertThat(reminders.stillPending(row, now)).isTrue();
        assertThat(reminders.reserve("test-dedup")).isTrue();
        assertThat(reminders.reserve("test-dedup")).isFalse();
        UUID term = jdbc.queryForObject("SELECT id FROM internship_terms LIMIT 1", UUID.class);
        for (String audience :
                java.util.List.of(
                        "ALL_STUDENTS",
                        "MENTORS",
                        "LECTURERS",
                        "COMPANY_REPS",
                        "FACULTY_ADMINS",
                        "ADMINS")) assertThat(campaigns.audience(term, audience)).isNotNull();
        assertThat(tickets.referenced("internlink/missing.pdf")).isFalse();
        assertThat(tickets.failures()).isEmpty();
    }

    @Test
    void actualJpaNotificationAndNativeOutboxShareOneTransaction() {
        var user =
                users.saveAndFlush(
                        User.builder()
                                .role(UserRole.ADMIN)
                                .email("notification-test@example.edu")
                                .fullName("Notification Test")
                                .passwordHash("test-only")
                                .isActive(true)
                                .build());
        settings.setEnabled(true);
        settings.setDeliveryMode("REDIRECT");
        service.publishNotification(
                user.getId(),
                "SYSTEM_TEST",
                "Thông báo thử",
                "Kiểm tra giao dịch",
                "/admin/dashboard",
                true);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM email_outbox WHERE recipient_user_id=?",
                                Integer.class,
                                user.getId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notifications WHERE recipient_user_id=?",
                                Integer.class,
                                user.getId()))
                .isEqualTo(1);
    }
}
