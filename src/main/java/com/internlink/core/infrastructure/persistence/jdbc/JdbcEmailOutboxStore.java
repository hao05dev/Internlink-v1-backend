package com.internlink.core.infrastructure.persistence.jdbc;

import com.internlink.core.application.system.notification.*;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import com.internlink.core.presentation.system.dto.response.EmailDeliveryResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class JdbcEmailOutboxStore implements EmailOutboxStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final EmailProperties settings;

    public JdbcEmailOutboxStore(
            JdbcTemplate jdbc, PlatformTransactionManager transactions, EmailProperties settings) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.settings = settings;
    }

    public boolean insert(
            String email,
            RenderedEmail content,
            String key,
            UUID notificationId,
            UUID userId,
            String type,
            EmailDispatchContext context) {
        return jdbc.update(
                        """
INSERT INTO email_outbox(id,notification_id,recipient_user_id,recipient_email,subject,body,html_body,event_type,idempotency_key,status,
    template_key,initiated_by,source_type,source_id,scope_type,scope_id)
VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?,?,?,?,?,?) ON CONFLICT(idempotency_key) DO NOTHING
""",
                        UUID.randomUUID(),
                        notificationId,
                        userId,
                        email,
                        content.subject(),
                        content.text(),
                        content.html(),
                        type,
                        key,
                        content.template(),
                        context.initiatedBy(),
                        context.sourceType(),
                        context.sourceId(),
                        context.scopeType(),
                        context.scopeId())
                > 0;
    }

    public EmailDelivery claim() {
        return transaction.execute(
                tx -> {
                    var control =
                            jdbc.queryForMap(
                                    "SELECT paused,daily_limit FROM email_delivery_control WHERE"
                                            + " id=1 FOR UPDATE");
                    if (Boolean.TRUE.equals(control.get("paused"))) return null;
                    UUID lease = UUID.randomUUID();
                    var rows =
                            jdbc.query(
                                    """
UPDATE email_outbox SET status='SENDING',attempts=attempts+1,lease_token=?,
    lease_until=CURRENT_TIMESTAMP+INTERVAL '10 minutes'
WHERE id IN (SELECT id FROM email_outbox WHERE status IN ('PENDING','RETRY')
    AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED)
RETURNING *
""",
                                    (rs, index) -> delivery(rs),
                                    lease);
                    if (rows.isEmpty()) return null;
                    long used = used();
                    if (used >= ((Number) control.get("daily_limit")).intValue()) {
                        tx.setRollbackOnly();
                        return null;
                    }
                    jdbc.update(
                            "INSERT INTO email_attempt_reservations(id,sender_key) VALUES(?,?)",
                            UUID.randomUUID(),
                            senderKey());
                    return rows.getFirst();
                });
    }

    public void expireLeases() {
        transaction.executeWithoutResult(
                tx -> {
                    var ids =
                            jdbc.query(
                                    """
UPDATE email_outbox SET status='UNKNOWN',last_error_code='worker_result_unknown',lease_token=NULL,lease_until=NULL
WHERE status='SENDING' AND lease_until<CURRENT_TIMESTAMP RETURNING notification_id
""",
                                    (rs, index) -> uuid(rs, "notification_id"));
                    for (var id : ids) updateNotification(id, "UNKNOWN");
                    jdbc.update(
                            "DELETE FROM email_attempt_reservations WHERE"
                                    + " reserved_at<CURRENT_TIMESTAMP-INTERVAL '30 days'");
                });
    }

    public boolean finish(
            EmailDelivery row,
            String result,
            String error,
            String providerId,
            OffsetDateTime next) {
        return Boolean.TRUE.equals(
                transaction.execute(
                        tx -> {
                            int changed =
                                    jdbc.update(
                                            """
UPDATE email_outbox SET status=?,last_error_code=?,provider_message_id=?,next_attempt_at=?,
    sent_at=CASE WHEN ?='ACCEPTED' THEN CURRENT_TIMESTAMP ELSE sent_at END,lease_token=NULL,lease_until=NULL
WHERE id=? AND status='SENDING' AND lease_token=?
""",
                                            result,
                                            error,
                                            providerId,
                                            next,
                                            result,
                                            row.id(),
                                            row.lease());
                            if (changed > 0) updateNotification(row.notificationId(), result);
                            return changed > 0;
                        }));
    }

    public Map<String, Long> summary() {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(
                "SELECT status,count(*) AS amount FROM email_outbox GROUP BY status",
                (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> result.put(rs.getString("status"), rs.getLong("amount")));
        return result;
    }

    public EmailDeliveryResponse.Metrics metrics() {
        return jdbc.queryForObject(
                """
SELECT greatest(0,extract(epoch FROM CURRENT_TIMESTAMP-min(created_at) FILTER (WHERE status IN ('PENDING','RETRY','SENDING')))) AS oldest_seconds,
    avg(greatest(0,extract(epoch FROM sent_at-created_at))) FILTER (WHERE status IN ('ACCEPTED','DELIVERED','BOUNCED') AND sent_at>=CURRENT_TIMESTAMP-INTERVAL '24 hours') AS average_seconds
FROM email_outbox
""",
                (rs, index) -> {
                    Number oldest = (Number) rs.getObject("oldest_seconds");
                    Number average = (Number) rs.getObject("average_seconds");
                    return new EmailDeliveryResponse.Metrics(
                            oldest == null ? null : oldest.longValue(),
                            average == null ? null : average.doubleValue());
                });
    }

    public EmailDeliveryResponse.Controls controls() {
        var control =
                jdbc.queryForMap(
                        "SELECT paused,daily_limit FROM email_delivery_control WHERE id=1");
        return new EmailDeliveryResponse.Controls(
                (Boolean) control.get("paused"),
                ((Number) control.get("daily_limit")).intValue(),
                used());
    }

    public void updateControls(boolean paused, int limit) {
        jdbc.update(
                "UPDATE email_delivery_control SET paused=?,daily_limit=? WHERE id=1",
                paused,
                limit);
    }

    public EmailDeliveryResponse.Page page(EmailDeliveryFilter filter, int page, int size) {
        var clauses = new ArrayList<String>();
        var args = new ArrayList<Object>();
        if (filter.status() != null && !filter.status().isBlank()) {
            clauses.add("status=?");
            args.add(filter.status());
        }
        if (filter.scopeType() != null && !filter.scopeType().isBlank()) {
            clauses.add("scope_type=?");
            args.add(filter.scopeType());
        }
        if (filter.sourceType() != null && !filter.sourceType().isBlank()) {
            clauses.add("source_type=?");
            args.add(filter.sourceType());
        }
        if (filter.templateKey() != null && !filter.templateKey().isBlank()) {
            clauses.add("template_key=?");
            args.add(filter.templateKey());
        }
        if (filter.recipient() != null && !filter.recipient().isBlank()) {
            clauses.add("lower(recipient_email) LIKE ?");
            args.add("%" + filter.recipient().strip().toLowerCase(Locale.ROOT) + "%");
        }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        long count =
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM email_outbox" + where, Long.class, args.toArray()));
        var params = new ArrayList<Object>(args);
        params.add(size);
        params.add((long) page * size);
        var rows =
                jdbc.query(
                        "SELECT * FROM email_outbox"
                                + where
                                + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                        (rs, index) ->
                                new EmailDeliveryResponse.Item(
                                        uuid(rs, "id"),
                                        uuid(rs, "notification_id"),
                                        rs.getString("recipient_email"),
                                        rs.getString("subject"),
                                        rs.getString("status"),
                                        rs.getInt("attempts"),
                                        rs.getString("last_error_code"),
                                        rs.getString("provider_message_id"),
                                        rs.getString("template_key"),
                                        uuid(rs, "initiated_by"),
                                        rs.getString("source_type"),
                                        uuid(rs, "source_id"),
                                        rs.getString("scope_type"),
                                        uuid(rs, "scope_id"),
                                        rs.getObject("created_at", OffsetDateTime.class),
                                        rs.getObject("next_attempt_at", OffsetDateTime.class),
                                        rs.getObject("sent_at", OffsetDateTime.class),
                                        rs.getObject("delivered_at", OffsetDateTime.class),
                                        rs.getObject("bounced_at", OffsetDateTime.class)),
                        params.toArray());
        return new EmailDeliveryResponse.Page(rows, count, (int) ((count + size - 1) / size));
    }

    public boolean retry(UUID id, boolean confirmedNotAccepted) {
        return transition(
                id,
                "RETRY",
                confirmedNotAccepted
                        ? List.of("FAILED", "UNKNOWN", "BOUNCED")
                        : List.of("FAILED", "BOUNCED"));
    }

    public boolean cancel(UUID id) {
        return transition(
                id, "SKIPPED", List.of("PENDING", "RETRY", "FAILED", "UNKNOWN", "BOUNCED"));
    }

    public void attachContext(UUID notificationId, EmailDispatchContext context) {
        jdbc.update("""
UPDATE email_outbox SET initiated_by=?,source_type=?,source_id=?,scope_type=?,scope_id=?
WHERE notification_id=?
""", context.initiatedBy(), context.sourceType(), context.sourceId(),
                context.scopeType(), context.scopeId(), notificationId);
    }

    private boolean transition(UUID id, String target, List<String> allowed) {
        return Boolean.TRUE.equals(
                transaction.execute(
                        tx -> {
                            var rows =
                                    jdbc.query(
                                            "SELECT status,notification_id FROM email_outbox WHERE"
                                                    + " id=? FOR UPDATE",
                                            (rs, index) ->
                                                    Map.entry(
                                                            rs.getString("status"),
                                                            Objects.toString(
                                                                    rs.getString("notification_id"),
                                                                    "")),
                                            id);
                            if (rows.isEmpty() || !allowed.contains(rows.getFirst().getKey()))
                                return false;
                            jdbc.update(
                                    """
UPDATE email_outbox SET status=?,attempts=CASE WHEN ?='RETRY' THEN 0 ELSE attempts END,
    next_attempt_at=CURRENT_TIMESTAMP,last_error_code=NULL,lease_token=NULL,lease_until=NULL WHERE id=?
""",
                                    target,
                                    target,
                                    id);
                            String notificationId = rows.getFirst().getValue();
                            if (!notificationId.isBlank())
                                updateNotification(UUID.fromString(notificationId), target);
                            return true;
                        }));
    }

    private String senderKey() {
        return settings.getFrom().strip().toLowerCase(Locale.ROOT);
    }

    private long used() {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT count(*) FROM email_attempt_reservations WHERE sender_key=? AND"
                                + " reserved_at>CURRENT_TIMESTAMP-INTERVAL '24 hours'",
                        Long.class,
                        senderKey()));
    }

    private void updateNotification(UUID id, String state) {
        if (id != null)
            jdbc.update(
                    "UPDATE notifications SET delivery_status=delivery_status ||"
                            + " jsonb_build_object('email',CAST(? AS text)) WHERE id=?",
                    state,
                    id);
    }

    private static UUID uuid(ResultSet rs, String name) throws SQLException {
        String value = rs.getString(name);
        return value == null ? null : UUID.fromString(value);
    }

    private static EmailDelivery delivery(ResultSet rs) throws SQLException {
        return new EmailDelivery(
                uuid(rs, "id"),
                uuid(rs, "notification_id"),
                uuid(rs, "recipient_user_id"),
                rs.getString("recipient_email"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getString("html_body"),
                rs.getString("event_type"),
                rs.getInt("attempts"),
                uuid(rs, "lease_token"));
    }
}
