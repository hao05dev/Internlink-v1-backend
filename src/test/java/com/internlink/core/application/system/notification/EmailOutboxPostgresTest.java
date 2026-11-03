package com.internlink.core.application.system.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.system.*;
import com.internlink.core.infrastructure.integration.email.*;
import com.internlink.core.infrastructure.integration.google.GmailSender;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.UserRole;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;

@EnabledIfEnvironmentVariable(named = "INTERNLINK_TEST_PG_URL", matches = ".+")
class EmailOutboxPostgresTest {
    JdbcTemplate jdbc;
    JdbcTemplate admin;
    DataSourceTransactionManager transactions;
    EmailOutboxService queue;
    com.internlink.core.infrastructure.persistence.jdbc.JdbcEmailOutboxStore store;
    EmailOutboxWorker worker;
    GmailSender sender;
    JpaUserRepository users;
    JpaNotificationPreferenceRepository preferences;
    User user;
    String schema;

    @Test
    void metricsMeasureBacklogAgeAndAcceptanceLatencyWithoutTreatingSkippedAsSent() {
        assertThat(store.metrics().averageAcceptanceSeconds24Hours()).isNull();
        queue.enqueueNotice(user.getEmail(), plain("Pending", "Body"));
        jdbc.update("UPDATE email_outbox SET created_at=CURRENT_TIMESTAMP-INTERVAL '10 minutes'");
        queue.enqueueNotice(user.getEmail(), plain("Sent", "Body"));
        jdbc.update(
                "UPDATE email_outbox SET status='ACCEPTED',created_at=CURRENT_TIMESTAMP-INTERVAL '90"
                    + " seconds',sent_at=CURRENT_TIMESTAMP WHERE subject='Sent'");
        queue.enqueueNotice(user.getEmail(), plain("Skipped", "Body"));
        jdbc.update(
                "UPDATE email_outbox SET status='SKIPPED',created_at=CURRENT_TIMESTAMP-INTERVAL '1"
                    + " day' WHERE subject='Skipped'");
        assertThat(store.metrics().oldestPendingSeconds()).isBetween(600L, 605L);
        assertThat(store.metrics().averageAcceptanceSeconds24Hours()).isBetween(90.0, 91.0);
    }

    @BeforeEach
    void setup() {
        String url = System.getenv("INTERNLINK_TEST_PG_URL");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, "internlink_test", ""));
        schema = "outbox_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        var ds =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                        "internlink_test",
                        "");
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE users(id UUID PRIMARY KEY)");
        jdbc.execute(
                "CREATE TABLE email_delivery_control(id INTEGER PRIMARY KEY,paused BOOLEAN DEFAULT"
                        + " false,daily_limit INTEGER DEFAULT 200)");
        jdbc.execute("INSERT INTO email_delivery_control(id) VALUES(1)");
        jdbc.execute(
                "CREATE TABLE email_attempt_reservations(id UUID PRIMARY KEY,sender_key"
                        + " TEXT,reserved_at TIMESTAMPTZ DEFAULT NOW())");
        jdbc.execute(
                "CREATE TABLE notifications(id UUID PRIMARY KEY, delivery_status JSONB NOT NULL"
                        + " DEFAULT '{}')");
        new ResourceDatabasePopulator(
                        new FileSystemResource(
                                "src/main/resources/db/migration/V17__notification_email_outbox_and_r2.sql"))
                .execute(ds);
        jdbc.execute("ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_status_check");
        jdbc.execute(
                "ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_status_check CHECK"
                        + " (status IN ('PENDING','SENDING','RETRY','ACCEPTED','DELIVERED',"
                        + "'BOUNCED','FAILED','UNKNOWN','SKIPPED'))");
        jdbc.execute(
                "ALTER TABLE email_outbox ADD COLUMN delivered_at TIMESTAMPTZ,"
                        + " ADD COLUMN bounced_at TIMESTAMPTZ, ADD COLUMN html_body TEXT,"
                        + " ADD COLUMN template_key VARCHAR(60), ADD COLUMN initiated_by UUID,"
                        + " ADD COLUMN source_type VARCHAR(40), ADD COLUMN source_id UUID,"
                        + " ADD COLUMN scope_type VARCHAR(20), ADD COLUMN scope_id UUID");
        users = mock(JpaUserRepository.class);
        preferences = mock(JpaNotificationPreferenceRepository.class);
        sender = mock(GmailSender.class);
        user =
                User.builder()
                        .email("student@example.edu")
                        .role(UserRole.STUDENT)
                        .isActive(true)
                        .build();
        user.setId(UUID.randomUUID());
        jdbc.update("INSERT INTO users VALUES (?)", user.getId());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var settings = new EmailProperties();
        settings.setEnabled(true);
        settings.setDeliveryMode("ALLOWLIST");
        settings.setAllowedRecipients(Set.of(user.getEmail()));
        settings.setPublicBaseUrl("https://internlink.example");
        store =
                new com.internlink.core.infrastructure.persistence.jdbc.JdbcEmailOutboxStore(
                        jdbc, transactions, settings);
        var policy = new EmailRecipientPolicy(users, preferences);
        queue =
                new EmailOutboxService(
                        store, settings, policy, users, new NotificationEmailComposer(new EmailRenderer(settings)));
        worker = new EmailOutboxWorker(store, sender, settings, policy);
    }

    private static RenderedEmail plain(String subject, String body) {
        return new RenderedEmail("TEST", subject, body, null);
    }

    @AfterEach
    void cleanup() {
        if (admin != null && schema != null) admin.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    Notification notification() {
        var n =
                Notification.builder()
                        .id(UUID.randomUUID())
                        .recipient(user)
                        .notificationType("APPLICATION_STATUS_CHANGED")
                        .title("Hồ sơ thực tập")
                        .message("Hồ sơ đã cập nhật")
                        .actionUrl("/student/applications")
                        .build();
        jdbc.update("INSERT INTO notifications(id) VALUES (?)", n.getId());
        return n;
    }

    String state(UUID id) {
        return jdbc.queryForObject("SELECT status FROM email_outbox WHERE id=?", String.class, id);
    }

    @Test
    void rollbackRemovesBusinessNotificationAndEmailTogether() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        tx -> {
                            var n = notification();
                            assertThat(queue.enqueue(n)).isEqualTo("QUEUED");
                            tx.setRollbackOnly();
                        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_outbox", Integer.class))
                .isZero();
        verifyNoInteractions(sender);
    }

    @Test
    void duplicateNotificationCreatesOnlyOneDelivery() {
        var n = notification();
        assertThat(queue.enqueue(n)).isEqualTo("QUEUED");
        assertThat(queue.enqueue(n)).isEqualTo("DUPLICATE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_outbox", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void parallelWorkersClaimDistinctRows() throws Exception {
        queue.enqueue(notification());
        queue.enqueue(notification());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(worker::claim);
            var second = executor.submit(worker::claim);
            var a = first.get(5, TimeUnit.SECONDS);
            var b = second.get(5, TimeUnit.SECONDS);
            assertThat(a.id()).isNotEqualTo(b.id());
            assertThat(a.attempts()).isEqualTo(1);
            assertThat(a.lease()).isNotNull();
            assertThat(state(a.id())).isEqualTo("SENDING");
            assertThat(worker.claim()).isNull();
        }
    }

    @Test
    void acceptedEmailUpdatesBothStatuses() {
        var n = notification();
        queue.enqueue(n);
        var row = worker.claim();
        when(sender.deliver(any(), anyString(), anyString(), anyString(), any())).thenReturn("gmail-id");
        worker.deliver(row);
        assertThat(state(row.id())).isEqualTo("ACCEPTED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT delivery_status->>'email' FROM notifications WHERE id=?",
                                String.class,
                                n.getId()))
                .isEqualTo("ACCEPTED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT provider_message_id FROM email_outbox WHERE id=?",
                                String.class,
                                row.id()))
                .isEqualTo("gmail-id");
    }

    @Test
    void explicitTemporaryRejectionRetriesAfterDelay() {
        queue.enqueue(notification());
        var row = worker.claim();
        when(sender.deliver(any(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new EmailDeliveryException("gmail_http_429", true, false));
        worker.deliver(row);
        assertThat(state(row.id())).isEqualTo("RETRY");
        assertThat(worker.claim()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT next_attempt_at>CURRENT_TIMESTAMP FROM email_outbox WHERE"
                                        + " id=?",
                                Boolean.class,
                                row.id()))
                .isTrue();
    }

    @Test
    void uncertainAcceptanceIsNeverAutomaticallyResent() {
        queue.enqueue(notification());
        var row = worker.claim();
        when(sender.deliver(any(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new EmailDeliveryException("smtp_result_unknown", false, true));
        worker.deliver(row);
        worker.tick();
        assertThat(state(row.id())).isEqualTo("UNKNOWN");
        verify(sender, times(1)).deliver(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void crashedWorkerLeaseBecomesUnknown() {
        queue.enqueue(notification());
        var row = worker.claim();
        jdbc.update(
                "UPDATE email_outbox SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE"
                        + " id=?",
                row.id());
        worker.tick();
        assertThat(state(row.id())).isEqualTo("UNKNOWN");
        verifyNoInteractions(sender);
    }

    @Test
    void preferenceRevokedAfterEnqueuePreventsDelivery() {
        queue.enqueue(notification());
        var row = worker.claim();
        when(preferences.findById(user.getId()))
                .thenReturn(
                        Optional.of(NotificationPreference.builder().emailEnabled(false).build()));
        worker.deliver(row);
        assertThat(state(row.id())).isEqualTo("SKIPPED");
        verifyNoInteractions(sender);
    }

    @Test
    void changedEmailAddressPreventsDeliveryToOldAddress() {
        queue.enqueue(notification());
        var row = worker.claim();
        user.setEmail("new@example.edu");
        worker.deliver(row);
        assertThat(state(row.id())).isEqualTo("SKIPPED");
        verifyNoInteractions(sender);
    }

    @Test
    void inactiveRegisteredUserCannotReceiveRosterNotice() {
        user.setIsActive(false);
        assertThat(queue.enqueueNotice(user.getEmail(), plain("Thông báo", "Nội dung"))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_outbox", Integer.class))
                .isZero();
    }

    @Test
    void staleLeaseCannotOverwriteCurrentResult() {
        queue.enqueue(notification());
        var row = worker.claim();
        jdbc.update(
                "UPDATE email_outbox SET lease_token=? WHERE id=?", UUID.randomUUID(), row.id());
        when(sender.deliver(any(), anyString(), anyString(), anyString(), any())).thenReturn("gmail-id");
        worker.deliver(row);
        assertThat(state(row.id())).isEqualTo("SENDING");
    }

    @Test
    void pausedDeliveryDoesNotClaimOrConsumeBudget() {
        queue.enqueue(notification());
        store.updateControls(true, 1);
        assertThat(worker.claim()).isNull();
        assertThat(store.controls().usedLast24Hours()).isZero();
        assertThat(jdbc.queryForObject("SELECT attempts FROM email_outbox LIMIT 1", Integer.class))
                .isZero();
    }

    @Test
    void quotaFenceRollsBackAnExcessClaim() {
        queue.enqueue(notification());
        queue.enqueue(notification());
        store.updateControls(false, 1);
        assertThat(worker.claim()).isNotNull();
        assertThat(worker.claim()).isNull();
        assertThat(store.controls().usedLast24Hours()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT attempts FROM email_outbox WHERE status='PENDING'",
                                Integer.class))
                .isZero();
    }

    @Test
    void unknownRetryRequiresConfirmationAndSentCannotRetry() {
        queue.enqueue(notification());
        var row = worker.claim();
        jdbc.update("UPDATE email_outbox SET status='UNKNOWN' WHERE id=?", row.id());
        assertThat(store.retry(row.id(), false)).isFalse();
        assertThat(store.retry(row.id(), true)).isTrue();
        assertThat(state(row.id())).isEqualTo("RETRY");
        jdbc.update("UPDATE email_outbox SET status='ACCEPTED' WHERE id=?", row.id());
        assertThat(store.retry(row.id(), true)).isFalse();
        assertThat(store.cancel(row.id())).isFalse();
    }
}
