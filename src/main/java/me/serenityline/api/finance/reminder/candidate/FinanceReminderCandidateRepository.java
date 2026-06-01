package me.serenityline.api.finance.reminder.candidate;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

@Repository
public class FinanceReminderCandidateRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanceReminderCandidateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    private static void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("finance.reminderCandidate.limit.invalid");
        }
    }

    public List<FinanceReminderCandidate> findDueTransactionCandidates(
            LocalDate today,
            int limit
    ) {
        Objects.requireNonNull(today, "today");
        validateLimit(limit);

        String sql = """
                SELECT
                    tu.user_id,
                    t.user_group_id,
                    t.transaction_id,
                    t.transaction_charge_date,
                    t.transaction_description,
                    t.transaction_amount,
                    a.currency,
                    (t.transaction_charge_date - t.transaction_reminder_days_before::integer) AS reminder_date
                FROM transactions t
                JOIN transactions_users tu
                    ON tu.transaction_id = t.transaction_id
                   AND tu.user_group_id = t.user_group_id
                JOIN users u
                    ON u.user_id = tu.user_id
                   AND u.user_group_id = tu.user_group_id
                JOIN accounts a
                    ON a.account_id = t.account_id
                   AND a.user_group_id = t.user_group_id
                WHERE t.transaction_is_simulated = FALSE
                  AND t.transaction_is_user_entered = TRUE
                  AND t.transaction_reminder_enabled = TRUE
                  AND u.user_is_enabled = TRUE
                  AND u.payment_email_reminders_enabled = TRUE
                  AND (t.transaction_charge_date - t.transaction_reminder_days_before::integer) <= :today
                  AND t.transaction_charge_date >= :today
                  AND NOT EXISTS (
                        SELECT 1
                        FROM finance_reminder_notifications frn
                        WHERE frn.user_group_id = t.user_group_id
                          AND frn.user_id = tu.user_id
                          AND frn.transaction_id = t.transaction_id
                  )
                ORDER BY
                    reminder_date,
                    t.transaction_charge_date,
                    t.transaction_id,
                    tu.user_id
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("limit", limit);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> FinanceReminderCandidate.forTransaction(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("user_group_id", UUID.class),
                        rs.getObject("transaction_id", UUID.class),
                        rs.getDate("transaction_charge_date").toLocalDate(),
                        rs.getString("transaction_description"),
                        rs.getBigDecimal("transaction_amount"),
                        rs.getString("currency"),
                        rs.getDate("reminder_date").toLocalDate()
                )
        );
    }

    public List<RecurringFinanceReminderSeed> findRecurringReminderSeeds(
            LocalDate today,
            int limit
    ) {
        return findRecurringReminderSeedsPage(today, limit, null);
    }

    public Map<UUID, List<UUID>> findReminderEnabledUserIdsByRecurringTransactionId(
            UUID userGroupId,
            Collection<UUID> recurringTransactionIds
    ) {
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(recurringTransactionIds, "recurringTransactionIds");

        if (recurringTransactionIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                SELECT
                    rtu.recurring_transaction_id,
                    rtu.user_id
                FROM recurring_transactions_users rtu
                JOIN users u
                    ON u.user_id = rtu.user_id
                   AND u.user_group_id = rtu.user_group_id
                WHERE rtu.user_group_id = :userGroupId
                  AND rtu.recurring_transaction_id IN (:recurringTransactionIds)
                  AND u.user_is_enabled = TRUE
                  AND u.payment_email_reminders_enabled = TRUE
                ORDER BY
                    rtu.recurring_transaction_id,
                    rtu.user_id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userGroupId", userGroupId)
                .addValue("recurringTransactionIds", recurringTransactionIds);

        Map<UUID, List<UUID>> result = new LinkedHashMap<>();

        jdbcTemplate.query(
                sql,
                params,
                rs -> {
                    UUID recurringTransactionId = rs.getObject("recurring_transaction_id", UUID.class);
                    UUID userId = rs.getObject("user_id", UUID.class);

                    result.computeIfAbsent(
                            recurringTransactionId,
                            ignored -> new ArrayList<>()
                    ).add(userId);
                }
        );

        return result.entrySet()
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), List.copyOf(entry.getValue())),
                        LinkedHashMap::putAll
                );
    }

    public List<FinanceReminderConfirmedRecurringOccurrenceSnapshot> findConfirmedRecurringOccurrenceSnapshots(
            UUID userGroupId,
            Collection<UUID> recurringTransactionIds,
            LocalDate minLogicalDate,
            LocalDate maxLogicalDate
    ) {
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(recurringTransactionIds, "recurringTransactionIds");
        Objects.requireNonNull(minLogicalDate, "minLogicalDate");
        Objects.requireNonNull(maxLogicalDate, "maxLogicalDate");

        if (recurringTransactionIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT
                    t.recurring_transaction_id,
                    t.recurring_transaction_logical_date,
                    t.transaction_charge_date,
                    t.transaction_description,
                    t.transaction_amount,
                    a.currency
                FROM transactions t
                JOIN accounts a
                    ON a.account_id = t.account_id
                   AND a.user_group_id = t.user_group_id
                WHERE t.user_group_id = :userGroupId
                  AND t.recurring_transaction_id IN (:recurringTransactionIds)
                  AND t.recurring_transaction_logical_date BETWEEN :minLogicalDate AND :maxLogicalDate
                  AND t.transaction_is_user_entered = FALSE
                  AND t.transaction_is_confirmed = TRUE
                  AND t.transaction_is_simulated = FALSE
                ORDER BY
                    t.recurring_transaction_id,
                    t.recurring_transaction_logical_date,
                    t.transaction_id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userGroupId", userGroupId)
                .addValue("recurringTransactionIds", recurringTransactionIds)
                .addValue("minLogicalDate", minLogicalDate)
                .addValue("maxLogicalDate", maxLogicalDate);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new FinanceReminderConfirmedRecurringOccurrenceSnapshot(
                        rs.getObject("recurring_transaction_id", UUID.class),
                        rs.getDate("recurring_transaction_logical_date").toLocalDate(),
                        rs.getDate("transaction_charge_date").toLocalDate(),
                        rs.getString("transaction_description"),
                        rs.getBigDecimal("transaction_amount"),
                        rs.getString("currency")
                )
        );
    }

    public List<RecurringFinanceReminderSeed> findRecurringReminderSeedsPage(
            LocalDate today,
            int pageSize,
            UUID afterRecurringTransactionId
    ) {
        Objects.requireNonNull(today, "today");
        validateLimit(pageSize);

        LocalDate latestRelevantChargeDate = today.plusDays(366);

        String afterFilter = afterRecurringTransactionId == null
                ? ""
                : " AND rt.recurring_transaction_id > :afterRecurringTransactionId\n";

        String sql = """
                SELECT
                    rt.recurring_transaction_id,
                    rt.user_group_id,
                    rt.recurring_transaction_first_payment_date,
                    rt.recurring_transaction_reminder_days_before
                FROM recurring_transactions rt
                WHERE rt.recurring_transaction_is_simulated = FALSE
                  AND rt.recurring_transaction_reminder_enabled = TRUE
                  AND rt.recurring_transaction_first_payment_date <= :latestRelevantChargeDate
                  AND EXISTS (
                        SELECT 1
                        FROM recurring_transaction_history rth
                        WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                          AND rth.effective_to IS NULL
                  )
                  AND EXISTS (
                        SELECT 1
                        FROM recurring_transaction_details_history rdth
                        WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                          AND rdth.user_group_id = rt.user_group_id
                          AND rdth.recurring_transaction_details_effective_from <= :latestRelevantChargeDate
                  )
                  AND EXISTS (
                        SELECT 1
                        FROM recurring_transactions_users rtu
                        JOIN users u
                            ON u.user_id = rtu.user_id
                           AND u.user_group_id = rtu.user_group_id
                        WHERE rtu.recurring_transaction_id = rt.recurring_transaction_id
                          AND rtu.user_group_id = rt.user_group_id
                          AND u.user_is_enabled = TRUE
                          AND u.payment_email_reminders_enabled = TRUE
                  )
                """
                + afterFilter
                + """
                ORDER BY
                    rt.recurring_transaction_id
                LIMIT :pageSize
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("latestRelevantChargeDate", latestRelevantChargeDate)
                .addValue("pageSize", pageSize);

        if (afterRecurringTransactionId != null) {
            params.addValue("afterRecurringTransactionId", afterRecurringTransactionId);
        }

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new RecurringFinanceReminderSeed(
                        rs.getObject("recurring_transaction_id", UUID.class),
                        rs.getObject("user_group_id", UUID.class),
                        rs.getDate("recurring_transaction_first_payment_date").toLocalDate(),
                        rs.getShort("recurring_transaction_reminder_days_before")
                )
        );
    }
}