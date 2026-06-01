package me.serenityline.api.finance.reminder.notification;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FinanceReminderNotificationInsertRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanceReminderNotificationInsertRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public Optional<UUID> insertTransactionNotificationIfAbsent(
            UUID notificationId,
            UUID userId,
            UUID userGroupId,
            UUID transactionId,
            LocalDate chargeDate,
            String notifiedDescription,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate,
            OffsetDateTime createdAt
    ) {
        String sql = """
                INSERT INTO finance_reminder_notifications (
                    finance_reminder_notification_id,
                    user_id,
                    user_group_id,
                    transaction_id,
                    recurring_transaction_id,
                    recurring_transaction_logical_date,
                    charge_date,
                    notified_description,
                    notified_amount,
                    notified_currency,
                    reminder_date,
                    reminder_notification_created_at
                )
                VALUES (
                    :notificationId,
                    :userId,
                    :userGroupId,
                    :transactionId,
                    NULL,
                    NULL,
                    :chargeDate,
                    :notifiedDescription,
                    :notifiedAmount,
                    :notifiedCurrency,
                    :reminderDate,
                    :createdAt
                )
                ON CONFLICT (
                    user_group_id,
                    user_id,
                    transaction_id
                )
                WHERE transaction_id IS NOT NULL
                DO NOTHING
                RETURNING finance_reminder_notification_id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("notificationId", notificationId)
                .addValue("userId", userId)
                .addValue("userGroupId", userGroupId)
                .addValue("transactionId", transactionId)
                .addValue("chargeDate", chargeDate)
                .addValue("notifiedDescription", notifiedDescription)
                .addValue("notifiedAmount", notifiedAmount)
                .addValue("notifiedCurrency", notifiedCurrency)
                .addValue("reminderDate", reminderDate)
                .addValue("createdAt", createdAt);

        return queryOptionalUuid(sql, params);
    }

    public Optional<UUID> insertRecurringNotificationIfAbsent(
            UUID notificationId,
            UUID userId,
            UUID userGroupId,
            UUID recurringTransactionId,
            LocalDate recurringTransactionLogicalDate,
            LocalDate chargeDate,
            String notifiedDescription,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate,
            OffsetDateTime createdAt
    ) {
        String sql = """
                INSERT INTO finance_reminder_notifications (
                    finance_reminder_notification_id,
                    user_id,
                    user_group_id,
                    transaction_id,
                    recurring_transaction_id,
                    recurring_transaction_logical_date,
                    charge_date,
                    notified_description,
                    notified_amount,
                    notified_currency,
                    reminder_date,
                    reminder_notification_created_at
                )
                VALUES (
                    :notificationId,
                    :userId,
                    :userGroupId,
                    NULL,
                    :recurringTransactionId,
                    :recurringTransactionLogicalDate,
                    :chargeDate,
                    :notifiedDescription,
                    :notifiedAmount,
                    :notifiedCurrency,
                    :reminderDate,
                    :createdAt
                )
                ON CONFLICT (
                    user_group_id,
                    user_id,
                    recurring_transaction_id,
                    recurring_transaction_logical_date
                )
                WHERE recurring_transaction_id IS NOT NULL
                DO NOTHING
                RETURNING finance_reminder_notification_id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("notificationId", notificationId)
                .addValue("userId", userId)
                .addValue("userGroupId", userGroupId)
                .addValue("recurringTransactionId", recurringTransactionId)
                .addValue("recurringTransactionLogicalDate", recurringTransactionLogicalDate)
                .addValue("chargeDate", chargeDate)
                .addValue("notifiedDescription", notifiedDescription)
                .addValue("notifiedAmount", notifiedAmount)
                .addValue("notifiedCurrency", notifiedCurrency)
                .addValue("reminderDate", reminderDate)
                .addValue("createdAt", createdAt);

        return queryOptionalUuid(sql, params);
    }

    private Optional<UUID> queryOptionalUuid(
            String sql,
            MapSqlParameterSource params
    ) {
        UUID id = jdbcTemplate.query(
                sql,
                params,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    return rs.getObject("finance_reminder_notification_id", UUID.class);
                }
        );

        return Optional.ofNullable(id);
    }
}