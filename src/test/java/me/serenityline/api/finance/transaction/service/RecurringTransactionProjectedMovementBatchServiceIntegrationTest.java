package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RecurringTransactionProjectedMovementBatchServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService;

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    private static String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void shouldGenerateProjectedMovementsForMultiplePersistedRecurringTransactions() {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID accountId = createAccount(
                owner.userGroupId(),
                "Conto batch projected movement integration",
                "EUR"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria batch projected movement integration"
        );

        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID firstRecurringTransactionId = createWeeklyRecurringTransaction(
                owner.userGroupId(),
                accountId,
                categoryId,
                financialPriorityId,
                LocalDate.of(2026, 1, 5),
                new BigDecimal("-100.00"),
                "Prima ricorrente batch"
        );

        UUID secondRecurringTransactionId = createWeeklyRecurringTransaction(
                owner.userGroupId(),
                accountId,
                categoryId,
                financialPriorityId,
                LocalDate.of(2026, 1, 7),
                new BigDecimal("-50.00"),
                "Seconda ricorrente batch"
        );

        List<RecurringTransactionProjectedMovementSeed> seeds = List.of(
                new RecurringTransactionProjectedMovementSeed(
                        firstRecurringTransactionId,
                        owner.userGroupId(),
                        LocalDate.of(2026, 1, 5)
                ),
                new RecurringTransactionProjectedMovementSeed(
                        secondRecurringTransactionId,
                        owner.userGroupId(),
                        LocalDate.of(2026, 1, 7)
                )
        );

        List<RecurringTransactionProjectedMovement> movements =
                recurringTransactionProjectedMovementBatchService.generateProjectedMovements(
                        seeds,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 20)
                );

        assertThat(movements)
                .extracting(
                        RecurringTransactionProjectedMovement::recurringTransactionId,
                        RecurringTransactionProjectedMovement::logicalDate,
                        RecurringTransactionProjectedMovement::chargeDate,
                        RecurringTransactionProjectedMovement::amount,
                        RecurringTransactionProjectedMovement::description,
                        RecurringTransactionProjectedMovement::finalOccurrence,
                        RecurringTransactionProjectedMovement::affectsAccountBalance,
                        RecurringTransactionProjectedMovement::affectsSerenityline
                )
                .containsExactly(
                        tuple(
                                firstRecurringTransactionId,
                                LocalDate.of(2026, 1, 5),
                                LocalDate.of(2026, 1, 5),
                                new BigDecimal("-100.00"),
                                "Prima ricorrente batch",
                                false,
                                true,
                                true
                        ),
                        tuple(
                                secondRecurringTransactionId,
                                LocalDate.of(2026, 1, 7),
                                LocalDate.of(2026, 1, 7),
                                new BigDecimal("-50.00"),
                                "Seconda ricorrente batch",
                                false,
                                true,
                                true
                        ),
                        tuple(
                                firstRecurringTransactionId,
                                LocalDate.of(2026, 1, 12),
                                LocalDate.of(2026, 1, 12),
                                new BigDecimal("-100.00"),
                                "Prima ricorrente batch",
                                false,
                                true,
                                true
                        ),
                        tuple(
                                secondRecurringTransactionId,
                                LocalDate.of(2026, 1, 14),
                                LocalDate.of(2026, 1, 14),
                                new BigDecimal("-50.00"),
                                "Seconda ricorrente batch",
                                false,
                                true,
                                true
                        ),
                        tuple(
                                firstRecurringTransactionId,
                                LocalDate.of(2026, 1, 19),
                                LocalDate.of(2026, 1, 19),
                                new BigDecimal("-100.00"),
                                "Prima ricorrente batch",
                                false,
                                true,
                                true
                        )
                );

        assertThat(movements)
                .allSatisfy(movement -> {
                    assertThat(movement.linkedAccount().getAccountId()).isEqualTo(accountId);
                    assertThat(movement.category()).isNotNull();
                    assertThat(movement.financialPriority()).isNotNull();
                    assertThat(movement.linkedCreditCard()).isNull();
                    assertThat(movement.linkedBucket()).isNull();
                });
    }

    private UserRef createUserWithNewGroup(String role) {
        UUID userGroupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO user_groups (
                            user_group_id,
                            user_group_name
                        )
                        VALUES (?, ?)
                        """,
                userGroupId,
                unique("Batch Occurrence Test Group")
        );

        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_password_hash,
                            user_is_enabled
                        )
                        VALUES (?, ?, ?, ?, ?, ?, TRUE)
                        """,
                userId,
                "Batch Occurrence Test User",
                uniqueEmail("batch-occurrence-owner"),
                userGroupId,
                role,
                "test-password-hash"
        );

        return new UserRef(userId, userGroupId, role);
    }

    private UUID createAccount(
            UUID userGroupId,
            String name,
            String currency
    ) {
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO accounts (
                            account_id,
                            account_name,
                            account_description,
                            currency,
                            issuing_institution,
                            opening_balance,
                            opening_balance_date,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                accountId,
                unique(name),
                "Conto test batch occurrence",
                currency,
                "Banca test",
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 1, 1),
                userGroupId
        );

        return accountId;
    }

    private UUID createActiveCategory(
            UUID userGroupId,
            UUID createdByUserId,
            String name
    ) {
        UUID categoryId = UUID.randomUUID();
        String uniqueName = unique(name);

        jdbcTemplate.update("""
                        INSERT INTO categories (
                            category_id,
                            user_group_id,
                            category_created_by_user_id,
                            category_current_name
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                categoryId,
                userGroupId,
                createdByUserId,
                uniqueName
        );

        jdbcTemplate.update("""
                        INSERT INTO category_details_history (
                            category_id,
                            category_name,
                            category_description
                        )
                        VALUES (?, ?, ?)
                        """,
                categoryId,
                uniqueName,
                "Categoria test batch occurrence"
        );

        jdbcTemplate.update("""
                        INSERT INTO category_status_history (
                            category_id,
                            category_is_active
                        )
                        VALUES (?, TRUE)
                        """,
                categoryId
        );

        return categoryId;
    }

    private UUID financialPriorityId(String financialPriorityName) {
        return jdbcTemplate.queryForObject("""
                        SELECT financial_priority_id
                        FROM financial_priorities
                        WHERE financial_priority_name = ?
                        """,
                UUID.class,
                financialPriorityName
        );
    }

    private UUID createWeeklyRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            UUID financialPriorityId,
            LocalDate firstPaymentDate,
            BigDecimal paymentAmount,
            String description
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, TRUE, ?, FALSE, TRUE, 7, ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_history (
                            recurring_transaction_id,
                            effective_from,
                            effective_to,
                            day_of_unit,
                            recurrence_interval,
                            recurrence_unit,
                            payment_date_adjustment_policy,
                            payment_amount,
                            recurring_transaction_end_date,
                            final_payment_amount
                        )
                        VALUES (?, ?, NULL, ?, 1, 'WEEK', 'PREVIOUS_BUSINESS_DAY', ?, NULL, NULL)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                firstPaymentDate.getDayOfWeek().getValue(),
                paymentAmount
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_credit_card_id,
                            linked_bucket_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, NULL, NULL, TRUE, TRUE, ?, ?)
                        """,
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId,
                accountId,
                firstPaymentDate,
                userGroupId
        );

        return recurringTransactionId;
    }

    private record UserRef(
            UUID userId,
            UUID userGroupId,
            String role
    ) {
    }
}