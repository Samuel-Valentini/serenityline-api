package me.serenityline.api.finance.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class FinanceCalendarServiceIntegrationTest {

    private final UUID groupId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID collaboratorId = UUID.randomUUID();
    private final UUID accountAId = UUID.randomUUID();
    private final UUID accountBId = UUID.randomUUID();
    private final UUID accountCId = UUID.randomUUID();
    private final UUID inaccessibleAccountId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID simulationGroupAId = UUID.randomUUID();
    private final UUID simulationGroupBId = UUID.randomUUID();
    @Autowired
    private FinanceCalendarService financeCalendarService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private UUID financialPriorityId;

    @BeforeEach
    void setUp() {
        financialPriorityId = jdbcTemplate.queryForObject("""
                SELECT financial_priority_id
                FROM financial_priorities
                WHERE financial_priority_name = 'ESSENTIAL'
                """, UUID.class);
    }

    @Test
    @DisplayName("Owner with multiple accountIds should get stable and projected recurring movements only for requested accounts")
    void ownerWithMultipleAccountIdsShouldGetStableAndProjectedRecurringMovementsOnlyForRequestedAccounts() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(accountBId, groupId, "Conto B");
        givenAccount(accountCId, groupId, "Conto C");

        UUID stableA = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 5),
                false,
                null,
                "Stable A"
        );

        UUID stableB = givenTransaction(
                accountBId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 6),
                false,
                null,
                "Stable B"
        );

        givenTransaction(
                accountCId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 7),
                false,
                null,
                "Stable C excluded"
        );

        UUID recurringA = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Recurring A"
        );

        UUID recurringC = givenRecurringTransaction(
                groupId,
                accountCId,
                false,
                null,
                "Recurring C excluded"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        List.of(accountAId, accountBId),
                        null
                )
        );

        assertThat(result)
                .extracting(FinanceCalendarMovement::accountId)
                .containsOnly(accountAId, accountBId);

        List<UUID> persistedTransactionIds = result.stream()
                .map(FinanceCalendarMovement::transactionId)
                .filter(Objects::nonNull)
                .toList();

        List<UUID> projectedRecurringTransactionIds = result.stream()
                .map(FinanceCalendarMovement::recurringTransactionId)
                .filter(Objects::nonNull)
                .toList();

        assertThat(persistedTransactionIds)
                .containsExactlyInAnyOrder(stableA, stableB);

        assertThat(projectedRecurringTransactionIds)
                .contains(recurringA)
                .doesNotContain(recurringC);
    }

    @Test
    @DisplayName("Collaborator with multiple accountIds should get only movements for linked accounts")
    void collaboratorWithMultipleAccountIdsShouldGetOnlyLinkedAccountMovements() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");
        givenUser(collaboratorId, groupId, "COLLABORATOR", "collaborator@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(accountBId, groupId, "Conto B");
        givenAccount(inaccessibleAccountId, groupId, "Conto non accessibile");

        givenAccountUser(accountAId, collaboratorId, groupId);
        givenAccountUser(accountBId, collaboratorId, groupId);

        UUID stableA = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 5),
                false,
                null,
                "Stable A"
        );

        UUID stableB = givenTransaction(
                accountBId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 6),
                false,
                null,
                "Stable B"
        );

        givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Recurring A"
        );

        givenTransaction(
                inaccessibleAccountId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 7),
                false,
                null,
                "Inaccessible excluded"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                collaboratorId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        List.of(accountAId, accountBId),
                        null
                )
        );

        assertThat(result)
                .extracting(FinanceCalendarMovement::accountId)
                .containsOnly(accountAId, accountBId);

        assertThat(result)
                .extracting(FinanceCalendarMovement::transactionId)
                .contains(stableA, stableB);
    }

    @Test
    @DisplayName("Selected simulation groups should include base and selected simulated movements")
    void selectedSimulationGroupsShouldIncludeBaseAndSelectedSimulatedMovements() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Conto A");

        givenSimulationGroup(simulationGroupAId, groupId, "Simulation A");
        givenSimulationGroup(simulationGroupBId, groupId, "Simulation B");

        UUID base = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 5),
                false,
                null,
                "Base"
        );

        UUID selectedSimulation = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 6),
                true,
                simulationGroupAId,
                "Selected simulation"
        );

        UUID excludedSimulation = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 7),
                true,
                simulationGroupBId,
                "Excluded simulation"
        );

        UUID selectedRecurringSimulation = givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupAId,
                "Selected recurring simulation"
        );

        UUID excludedRecurringSimulation = givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupBId,
                "Excluded recurring simulation"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        List.of(accountAId),
                        List.of(simulationGroupAId)
                )
        );

        assertThat(result)
                .extracting(FinanceCalendarMovement::transactionId)
                .contains(base, selectedSimulation)
                .doesNotContain(excludedSimulation);

        assertThat(result)
                .extracting(FinanceCalendarMovement::recurringTransactionId)
                .contains(selectedRecurringSimulation)
                .doesNotContain(excludedRecurringSimulation);
    }

    private void givenUserGroup(UUID userGroupId, String name) {
        jdbcTemplate.update("""
                INSERT INTO user_groups (
                    user_group_id,
                    user_group_name
                )
                VALUES (?, ?)
                """, userGroupId, name);
    }

    private void givenUser(UUID userId, UUID userGroupId, String role, String email) {
        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_group_id,
                            user_name,
                            email,
                            user_role,
                            user_platform_role,
                            preferred_locale,
                            preferred_theme,
                            wants_invoice,
                            user_password_hash,
                            user_is_enabled,
                            email_2fa_enabled,
                            payment_email_reminders_enabled
                        )
                        VALUES (?, ?, ?, ?, ?, 'USER', 'it-IT', 'DEFAULT', false, ?, true, false, true)
                        """,
                userId,
                userGroupId,
                "Test User",
                email,
                role,
                "{bcrypt}hash"
        );
    }

    private void givenCategory(UUID categoryId, UUID userGroupId, UUID createdByUserId, String name) {
        jdbcTemplate.update("""
                INSERT INTO categories (
                    category_id,
                    user_group_id,
                    category_created_by_user_id,
                    category_current_name
                )
                VALUES (?, ?, ?, ?)
                """, categoryId, userGroupId, createdByUserId, name);

        jdbcTemplate.update("""
                INSERT INTO category_status_history (
                    category_id,
                    category_is_active
                )
                VALUES (?, true)
                """, categoryId);

        jdbcTemplate.update("""
                INSERT INTO category_details_history (
                    category_id,
                    category_name,
                    category_description
                )
                VALUES (?, ?, ?)
                """, categoryId, name, "Test category");
    }

    private void givenAccount(UUID accountId, UUID userGroupId, String name) {
        jdbcTemplate.update("""
                INSERT INTO accounts (
                    account_id,
                    user_group_id,
                    account_name,
                    currency,
                    opening_balance,
                    opening_balance_date
                )
                VALUES (?, ?, ?, 'EUR', 0, ?)
                """, accountId, userGroupId, name, LocalDate.of(2026, 1, 1));
    }

    private void givenAccountUser(UUID accountId, UUID userId, UUID userGroupId) {
        jdbcTemplate.update("""
                INSERT INTO accounts_users (
                    account_id,
                    user_id,
                    user_group_id
                )
                VALUES (?, ?, ?)
                """, accountId, userId, userGroupId);
    }

    private void givenSimulationGroup(UUID simulationGroupId, UUID userGroupId, String name) {
        jdbcTemplate.update("""
                INSERT INTO simulation_groups (
                    simulation_group_id,
                    user_group_id,
                    simulation_group_name
                )
                VALUES (?, ?, ?)
                """, simulationGroupId, userGroupId, name);
    }

    private UUID givenTransaction(
            UUID accountId,
            UUID userGroupId,
            UUID categoryId,
            LocalDate chargeDate,
            boolean simulated,
            UUID simulationGroupId,
            String description
    ) {
        UUID transactionId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            transaction_affects_account_balance,
                            transaction_affects_serenityline,
                            category_id,
                            transaction_charge_date,
                            transaction_is_confirmed,
                            account_id,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, true, true, ?, ?, true, ?, ?, ?, true, true, 7, ?)
                        """,
                transactionId,
                description,
                BigDecimal.valueOf(-10),
                categoryId,
                chargeDate,
                accountId,
                simulated,
                simulationGroupId,
                userGroupId
        );

        return transactionId;
    }

    private UUID givenRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            boolean simulated,
            UUID simulationGroupId,
            String description
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            simulation_group_id,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, true, ?, ?, ?, true, 7, ?)
                        """,
                recurringTransactionId,
                LocalDate.of(2026, 1, 10),
                simulated,
                simulationGroupId,
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
                            payment_amount
                        )
                        VALUES (?, ?, NULL, 10, 1, 'MONTH', 'NONE', ?)
                        """,
                recurringTransactionId,
                LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(-100)
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, true, true, ?, ?)
                        """,
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId,
                accountId,
                LocalDate.of(2026, 1, 1),
                userGroupId
        );

        return recurringTransactionId;
    }
}