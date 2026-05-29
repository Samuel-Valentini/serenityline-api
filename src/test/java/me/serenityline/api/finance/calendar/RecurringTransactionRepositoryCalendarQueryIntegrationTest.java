package me.serenityline.api.finance.calendar;

import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecurringTransactionRepositoryCalendarQueryIntegrationTest {

    private final UUID groupId = UUID.randomUUID();
    private final UUID otherGroupId = UUID.randomUUID();
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
    private RecurringTransactionRepository recurringTransactionRepository;
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
    @DisplayName("Group calendar query with accountIds should return only base recurring transactions matching requested accounts")
    void groupBaseQueryForAccountsShouldReturnBaseRecurringTransactionsMatchingRequestedAccounts() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(accountBId, groupId, "Conto B");
        givenAccount(accountCId, groupId, "Conto C");

        UUID expectedA = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Expected A"
        );

        UUID expectedB = givenRecurringTransaction(
                groupId,
                accountBId,
                false,
                null,
                "Expected B"
        );

        givenRecurringTransaction(
                groupId,
                accountCId,
                false,
                null,
                "Wrong account"
        );

        givenSimulationGroup(simulationGroupAId, groupId, "Simulation A");

        givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupAId,
                "Simulated excluded"
        );

        givenRecurringTransactionWithoutOpenHistory(
                groupId,
                accountAId,
                false,
                null,
                "Closed recurring excluded"
        );

        List<RecurringTransaction> result = recurringTransactionRepository
                .findCalendarReadableBaseByUserGroupForAccounts(
                        groupId,
                        List.of(accountAId, accountBId)
                );

        assertThat(idsOf(result))
                .containsExactlyInAnyOrder(expectedA, expectedB);
    }

    @Test
    @DisplayName("Group calendar query with simulation groups should return base plus selected simulated recurring transactions")
    void groupBaseAndSimulatedQueryForAccountsShouldReturnBaseAndSelectedSimulatedRecurringTransactions() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");

        givenSimulationGroup(simulationGroupAId, groupId, "Simulation A");
        givenSimulationGroup(simulationGroupBId, groupId, "Simulation B");

        UUID base = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Base"
        );

        UUID selectedSimulation = givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupAId,
                "Selected simulation"
        );

        givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupBId,
                "Other simulation"
        );

        List<RecurringTransaction> result = recurringTransactionRepository
                .findCalendarReadableBaseAndSimulatedByUserGroupForAccounts(
                        groupId,
                        List.of(accountAId),
                        List.of(simulationGroupAId)
                );

        assertThat(idsOf(result))
                .containsExactlyInAnyOrder(base, selectedSimulation);
    }

    @Test
    @DisplayName("Linked-user calendar query with accountIds should return only recurring transactions on linked accounts")
    void linkedUserBaseQueryForAccountsShouldReturnOnlyRecurringTransactionsOnLinkedAccounts() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");
        givenUser(collaboratorId, groupId, "COLLABORATOR", "collaborator@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(accountBId, groupId, "Conto B");
        givenAccount(inaccessibleAccountId, groupId, "Conto non accessibile");

        givenAccountUser(accountAId, collaboratorId, groupId);
        givenAccountUser(accountBId, collaboratorId, groupId);

        UUID expectedA = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Expected A"
        );

        UUID expectedB = givenRecurringTransaction(
                groupId,
                accountBId,
                false,
                null,
                "Expected B"
        );

        givenRecurringTransaction(
                groupId,
                inaccessibleAccountId,
                false,
                null,
                "Not linked"
        );

        List<RecurringTransaction> result = recurringTransactionRepository
                .findCalendarReadableBaseByLinkedUserAccessForAccounts(
                        groupId,
                        collaboratorId,
                        List.of(accountAId, accountBId, inaccessibleAccountId)
                );

        assertThat(idsOf(result))
                .containsExactlyInAnyOrder(expectedA, expectedB);
    }

    @Test
    @DisplayName("Linked-user calendar query with simulations should return base plus selected simulated recurring transactions only for linked accounts")
    void linkedUserBaseAndSimulatedQueryForAccountsShouldReturnBaseAndSelectedSimulatedOnlyForLinkedAccounts() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");
        givenUser(collaboratorId, groupId, "COLLABORATOR", "collaborator@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(inaccessibleAccountId, groupId, "Conto non accessibile");

        givenAccountUser(accountAId, collaboratorId, groupId);

        givenSimulationGroup(simulationGroupAId, groupId, "Simulation A");
        givenSimulationGroup(simulationGroupBId, groupId, "Simulation B");

        UUID base = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Base"
        );

        UUID selectedSimulation = givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupAId,
                "Selected simulation"
        );

        givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupBId,
                "Other simulation"
        );

        givenRecurringTransaction(
                groupId,
                inaccessibleAccountId,
                false,
                null,
                "Base inaccessible"
        );

        givenRecurringTransaction(
                groupId,
                inaccessibleAccountId,
                true,
                simulationGroupAId,
                "Simulation inaccessible"
        );

        List<RecurringTransaction> result = recurringTransactionRepository
                .findCalendarReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
                        groupId,
                        collaboratorId,
                        List.of(accountAId, inaccessibleAccountId),
                        List.of(simulationGroupAId)
                );

        assertThat(idsOf(result))
                .containsExactlyInAnyOrder(base, selectedSimulation);
    }

    @Test
    @DisplayName("Group calendar account query should not leak recurring transactions from another user group")
    void groupQueryForAccountsShouldNotLeakRecurringTransactionsFromAnotherUserGroup() {
        UUID otherOwnerId = UUID.randomUUID();
        UUID otherCategoryId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();

        givenUserGroup(groupId, "Owner group");
        givenUserGroup(otherGroupId, "Other group");

        givenUser(ownerId, groupId, "OWNER", "owner@example.com");
        givenUser(otherOwnerId, otherGroupId, "OWNER", "other-owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenCategory(otherCategoryId, otherGroupId, otherOwnerId, "Altro");

        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(otherAccountId, otherGroupId, "Other account");

        UUID expected = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Expected"
        );

        givenRecurringTransaction(
                otherGroupId,
                otherAccountId,
                false,
                null,
                "Other group",
                otherCategoryId
        );

        List<RecurringTransaction> result = recurringTransactionRepository
                .findCalendarReadableBaseByUserGroupForAccounts(
                        groupId,
                        List.of(accountAId, otherAccountId)
                );

        assertThat(idsOf(result))
                .containsExactly(expected);
    }

    @Test
    @DisplayName("Calendar account filter should use recurring transaction details history, not only current details")
    void accountFilterShouldUseDetailsHistoryLinkedAccount() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Old account");
        givenAccount(accountBId, groupId, "Current account");

        UUID recurringTransactionId = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Historical account"
        );

        givenRecurringTransactionDetailsHistory(
                recurringTransactionId,
                groupId,
                accountBId,
                categoryId,
                "Current account",
                LocalDate.of(2026, 7, 1)
        );

        List<RecurringTransaction> oldAccountResult = recurringTransactionRepository
                .findCalendarReadableBaseByUserGroupForAccounts(
                        groupId,
                        List.of(accountAId)
                );

        List<RecurringTransaction> currentAccountResult = recurringTransactionRepository
                .findCalendarReadableBaseByUserGroupForAccounts(
                        groupId,
                        List.of(accountBId)
                );

        assertThat(idsOf(oldAccountResult))
                .containsExactly(recurringTransactionId);

        assertThat(idsOf(currentAccountResult))
                .containsExactly(recurringTransactionId);
    }

    private List<UUID> idsOf(List<RecurringTransaction> recurringTransactions) {
        return recurringTransactions.stream()
                .map(RecurringTransaction::getRecurringTransactionId)
                .toList();
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

    private UUID givenRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            boolean simulated,
            UUID simulationGroupId,
            String description
    ) {
        return givenRecurringTransaction(
                userGroupId,
                accountId,
                simulated,
                simulationGroupId,
                description,
                categoryId
        );
    }

    private UUID givenRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            boolean simulated,
            UUID simulationGroupId,
            String description,
            UUID categoryId
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        insertRecurringTransaction(
                recurringTransactionId,
                userGroupId,
                simulated,
                simulationGroupId
        );

        givenOpenRecurringTransactionHistory(recurringTransactionId);

        givenRecurringTransactionDetailsHistory(
                recurringTransactionId,
                userGroupId,
                accountId,
                categoryId,
                description,
                LocalDate.of(2026, 1, 1)
        );

        return recurringTransactionId;
    }

    private UUID givenRecurringTransactionWithoutOpenHistory(
            UUID userGroupId,
            UUID accountId,
            boolean simulated,
            UUID simulationGroupId,
            String description
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        insertRecurringTransaction(
                recurringTransactionId,
                userGroupId,
                simulated,
                simulationGroupId
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
                        VALUES (?, ?, ?, 10, 1, 'MONTH', 'NONE', ?)
                        """,
                recurringTransactionId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 1),
                BigDecimal.valueOf(-100)
        );

        givenRecurringTransactionDetailsHistory(
                recurringTransactionId,
                userGroupId,
                accountId,
                categoryId,
                description,
                LocalDate.of(2026, 1, 1)
        );

        return recurringTransactionId;
    }

    private void insertRecurringTransaction(
            UUID recurringTransactionId,
            UUID userGroupId,
            boolean simulated,
            UUID simulationGroupId
    ) {
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
    }

    private void givenOpenRecurringTransactionHistory(UUID recurringTransactionId) {
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
    }

    private void givenRecurringTransactionDetailsHistory(
            UUID recurringTransactionId,
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            String description,
            LocalDate effectiveFrom
    ) {
        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_liquidity,
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
                effectiveFrom,
                userGroupId
        );
    }
}