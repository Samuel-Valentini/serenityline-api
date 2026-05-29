package me.serenityline.api.finance.calendar;


import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
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
class TransactionRepositoryCalendarQueryIntegrationTest {

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
    private TransactionRepository transactionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Group query with accountIds should return only base transactions in requested accounts and range")
    void groupBaseQueryForAccountsShouldReturnOnlyBaseTransactionsInRequestedAccountsAndRange() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(accountBId, groupId, "Conto B");
        givenAccount(accountCId, groupId, "Conto C");

        UUID expectedA = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 10),
                false,
                null,
                "Expected A"
        );

        UUID expectedB = givenTransaction(
                accountBId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 12),
                false,
                null,
                "Expected B"
        );

        givenTransaction(
                accountCId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 12),
                false,
                null,
                "Wrong account"
        );

        givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 7, 1),
                false,
                null,
                "Out of range"
        );

        givenSimulationGroup(simulationGroupAId, groupId, "Simulation A");

        givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 15),
                true,
                simulationGroupAId,
                "Simulated excluded"
        );

        List<Transaction> result = transactionRepository.findBaseGroupTransactionsInRangeForAccounts(
                groupId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                List.of(accountAId, accountBId)
        );

        assertThat(idsOf(result))
                .containsExactlyInAnyOrder(expectedA, expectedB);
    }

    @Test
    @DisplayName("Group query with simulation groups should return base plus selected simulated transactions")
    void groupBaseAndSimulatedQueryForAccountsShouldReturnBaseAndSelectedSimulatedTransactions() {
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
                LocalDate.of(2026, 6, 10),
                false,
                null,
                "Base"
        );

        UUID selectedSimulation = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 11),
                true,
                simulationGroupAId,
                "Selected simulation"
        );

        givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 12),
                true,
                simulationGroupBId,
                "Other simulation"
        );

        List<Transaction> result = transactionRepository.findBaseAndSimulatedGroupTransactionsInRangeForAccounts(
                groupId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                List.of(accountAId),
                List.of(simulationGroupAId)
        );

        assertThat(idsOf(result))
                .containsExactlyInAnyOrder(base, selectedSimulation);
    }

    @Test
    @DisplayName("Linked-user query with accountIds should return only transactions in accounts linked to collaborator")
    void linkedUserBaseQueryForAccountsShouldReturnOnlyLinkedAccounts() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");
        givenUser(collaboratorId, groupId, "COLLABORATOR", "collaborator@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(accountBId, groupId, "Conto B");
        givenAccount(inaccessibleAccountId, groupId, "Conto non accessibile");

        givenAccountUser(accountAId, collaboratorId, groupId);
        givenAccountUser(accountBId, collaboratorId, groupId);

        UUID expectedA = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 10),
                false,
                null,
                "Expected A"
        );

        UUID expectedB = givenTransaction(
                accountBId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 11),
                false,
                null,
                "Expected B"
        );

        givenTransaction(
                inaccessibleAccountId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 12),
                false,
                null,
                "Not linked"
        );

        List<Transaction> result = transactionRepository.findBaseLinkedUserTransactionsInRangeForAccounts(
                groupId,
                collaboratorId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                List.of(accountAId, accountBId, inaccessibleAccountId)
        );

        assertThat(idsOf(result))
                .containsExactlyInAnyOrder(expectedA, expectedB);
    }

    @Test
    @DisplayName("Linked-user query with simulation groups should return base plus selected simulated transactions only for linked accounts")
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

        UUID base = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 10),
                false,
                null,
                "Base"
        );

        UUID selectedSimulation = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 11),
                true,
                simulationGroupAId,
                "Selected simulation"
        );

        givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 12),
                true,
                simulationGroupBId,
                "Other simulation"
        );

        givenTransaction(
                inaccessibleAccountId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 13),
                false,
                null,
                "Base inaccessible"
        );

        givenTransaction(
                inaccessibleAccountId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 14),
                true,
                simulationGroupAId,
                "Simulation inaccessible"
        );

        List<Transaction> result = transactionRepository.findBaseAndSimulatedLinkedUserTransactionsInRangeForAccounts(
                groupId,
                collaboratorId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                List.of(accountAId, inaccessibleAccountId),
                List.of(simulationGroupAId)
        );

        assertThat(idsOf(result))
                .containsExactlyInAnyOrder(base, selectedSimulation);
    }

    @Test
    @DisplayName("Group account query should not leak transactions from another user group")
    void groupQueryForAccountsShouldNotLeakOtherGroupTransactions() {
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

        UUID expected = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 10),
                false,
                null,
                "Expected"
        );

        givenTransaction(
                otherAccountId,
                otherGroupId,
                otherCategoryId,
                LocalDate.of(2026, 6, 10),
                false,
                null,
                "Other group"
        );

        List<Transaction> result = transactionRepository.findBaseGroupTransactionsInRangeForAccounts(
                groupId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                List.of(accountAId, otherAccountId)
        );

        assertThat(idsOf(result))
                .containsExactly(expected);
    }

    private List<UUID> idsOf(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getTransactionId)
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
}