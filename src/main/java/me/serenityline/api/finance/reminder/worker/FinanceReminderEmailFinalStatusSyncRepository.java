package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.finance.reminder.notification.FinanceReminderEmailFinalStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class FinanceReminderEmailFinalStatusSyncRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanceReminderEmailFinalStatusSyncRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    private static FinanceReminderEmailFinalStatus mapStatus(EmailOutboxStatus status) {
        return switch (status) {
            case SENT -> FinanceReminderEmailFinalStatus.SENT;
            case FAILED -> FinanceReminderEmailFinalStatus.FAILED;
            case CANCELLED -> FinanceReminderEmailFinalStatus.CANCELLED;
            case PENDING -> throw new IllegalArgumentException(
                    "finance.reminderFinalStatus.pendingStatus.invalid"
            );
        };
    }

    public List<FinanceReminderEmailFinalStatusSyncCandidate> findFinalStatusCandidates(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("finance.reminderFinalStatus.limit.invalid");
        }

        String sql = """
                SELECT
                    frn.finance_reminder_notification_id,
                    frn.email_outbox_id,
                    eo.email_status,
                    eo.provider,
                    eo.provider_message_id
                FROM finance_reminder_notifications frn
                JOIN email_outbox eo
                    ON eo.email_outbox_id = frn.email_outbox_id
                WHERE frn.email_final_status IS NULL
                  AND eo.email_status IN ('SENT', 'FAILED', 'CANCELLED')
                ORDER BY
                    eo.email_updated_at,
                    frn.finance_reminder_notification_id
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new FinanceReminderEmailFinalStatusSyncCandidate(
                        rs.getObject("finance_reminder_notification_id", UUID.class),
                        rs.getObject("email_outbox_id", UUID.class),
                        mapStatus(EmailOutboxStatus.valueOf(rs.getString("email_status"))),
                        rs.getString("provider"),
                        rs.getString("provider_message_id")
                )
        );
    }
}