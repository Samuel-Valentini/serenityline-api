package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringTransactionRepositoryReportIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 6, 15);

    @Autowired
    private RecurringTransactionRepository recurringTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID financialPriorityId;

    private static List<UUID> ids(List<RecurringTransaction> recurringTransactions) {
        return recurringTransactions.stream()
                .map(RecurringTransaction::getRecurringTransactionId)
                .toList();
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    private static String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@example.com";
    }

    @BeforeEach
    void setUp() {
        financialPriorityId = jdbcTemplate.queryForObject("""
                SELECT financial_priority_id
                FROM financial_priorities
                WHERE financial_priority_name = 'ESSENTIAL'
                """, UUID.class);
    }

    @Test
    void findReportReadableBaseByUserGroupShouldReturnOnlyBaseRecurringInSameGroup() {
        UUID groupId = UUID.randomUUID();
        UUID otherGroupId = UUID.randomUUID();

        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();

        UUID accountId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();
        UUID otherCategoryId = UUID.randomUUID();

        UUID simulationGroupId = UUID.randomUUID();

        givenUserGroup(groupId, unique("report group"));
        givenUserGroup(otherGroupId, unique("other report group"));

        givenUser(ownerId, groupId, "OWNER", uniqueEmail("report-owner"));
        givenUser(otherOwnerId, otherGroupId, "OWNER", uniqueEmail("report-other-owner"));

        givenCategory(categoryId, groupId, ownerId, unique("report category"));
        givenCategory(otherCategoryId, otherGroupId, otherOwnerId, unique("other category"));

        givenAccount(accountId, groupId, unique("report account"));
        givenAccount(otherAccountId, otherGroupId, unique("other account"));

        givenSimulationGroup(simulationGroupId, groupId, unique("report simulation"));

        UUID baseId = givenRecurringTransaction(
                groupId,
                accountId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Base recurring"
        );

        UUID simulatedId = givenRecurringTransaction(
                groupId,
                accountId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                true,
                simulationGroupId,
                "Simulated recurring"
        );

        UUID otherGroupRecurringId = givenRecurringTransaction(
                otherGroupId,
                otherAccountId,
                otherCategoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Other group recurring"
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        groupId,
                        null,
                        AS_OF_DATE
                );

        assertThat(ids(result))
                .containsExactly(baseId)
                .doesNotContain(simulatedId, otherGroupRecurringId);
    }

    @Test
    void findReportReadableBaseByUserGroupShouldFilterByCurrentDetailsAccount() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        UUID accountAId = UUID.randomUUID();
        UUID accountBId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("details group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("details-owner"));

        givenCategory(categoryId, groupId, ownerId, unique("details category"));

        givenAccount(accountAId, groupId, unique("account A"));
        givenAccount(accountBId, groupId, unique("account B"));

        UUID recurringId = givenRecurringTransactionHeader(
                groupId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        givenRecurringRule(
                recurringId,
                LocalDate.of(2026, 1, 1),
                null,
                BigDecimal.valueOf(-100)
        );

        givenRecurringDetails(
                recurringId,
                groupId,
                accountAId,
                categoryId,
                "Old account",
                LocalDate.of(2026, 1, 1)
        );

        givenRecurringDetails(
                recurringId,
                groupId,
                accountBId,
                categoryId,
                "Current account",
                LocalDate.of(2026, 5, 1)
        );

        List<RecurringTransaction> accountAResult =
                recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        groupId,
                        accountAId,
                        AS_OF_DATE
                );

        List<RecurringTransaction> accountBResult =
                recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        groupId,
                        accountBId,
                        AS_OF_DATE
                );

        assertThat(ids(accountAResult)).isEmpty();
        assertThat(ids(accountBResult)).containsExactly(recurringId);
    }

    @Test
    void findReportReadableBaseByUserGroupShouldUseFirstPaymentDateForFutureRecurring() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("future group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("future-owner"));
        givenCategory(categoryId, groupId, ownerId, unique("future category"));
        givenAccount(accountId, groupId, unique("future account"));

        UUID recurringId = givenRecurringTransactionHeader(
                groupId,
                LocalDate.of(2026, 9, 1),
                false,
                null
        );

        givenRecurringRule(
                recurringId,
                LocalDate.of(2026, 9, 1),
                null,
                BigDecimal.valueOf(-100)
        );

        givenRecurringDetails(
                recurringId,
                groupId,
                accountId,
                categoryId,
                "Future recurring",
                LocalDate.of(2026, 9, 1)
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        groupId,
                        null,
                        AS_OF_DATE
                );

        assertThat(ids(result)).containsExactly(recurringId);
    }

    @Test
    void findReportReadableBaseByUserGroupShouldExcludeFutureRecurringWithoutRuleAtFirstPaymentDate() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("missing rule group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("missing-rule-owner"));
        givenCategory(categoryId, groupId, ownerId, unique("missing rule category"));
        givenAccount(accountId, groupId, unique("missing rule account"));

        UUID recurringId = givenRecurringTransactionHeader(
                groupId,
                LocalDate.of(2026, 9, 1),
                false,
                null
        );

        givenRecurringRule(
                recurringId,
                LocalDate.of(2026, 10, 1),
                null,
                BigDecimal.valueOf(-100)
        );

        givenRecurringDetails(
                recurringId,
                groupId,
                accountId,
                categoryId,
                "Future recurring without rule at first payment",
                LocalDate.of(2026, 9, 1)
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        groupId,
                        null,
                        AS_OF_DATE
                );

        assertThat(ids(result)).doesNotContain(recurringId);
    }

    @Test
    void findReportReadableBaseByUserGroupShouldExcludeFutureRecurringWithoutDetailsAtFirstPaymentDate() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("missing details group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("missing-details-owner"));
        givenCategory(categoryId, groupId, ownerId, unique("missing details category"));
        givenAccount(accountId, groupId, unique("missing details account"));

        UUID recurringId = givenRecurringTransactionHeader(
                groupId,
                LocalDate.of(2026, 9, 1),
                false,
                null
        );

        givenRecurringRule(
                recurringId,
                LocalDate.of(2026, 9, 1),
                null,
                BigDecimal.valueOf(-100)
        );

        givenRecurringDetails(
                recurringId,
                groupId,
                accountId,
                categoryId,
                "Future recurring without details at first payment",
                LocalDate.of(2026, 10, 1)
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        groupId,
                        null,
                        AS_OF_DATE
                );

        assertThat(ids(result)).doesNotContain(recurringId);
    }

    @Test
    void findReportReadableBaseByUserGroupShouldExcludeActiveRecurringWithOnlyFutureRule() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("future rule only group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("future-rule-only-owner"));
        givenCategory(categoryId, groupId, ownerId, unique("future rule only category"));
        givenAccount(accountId, groupId, unique("future rule only account"));

        UUID recurringId = givenRecurringTransactionHeader(
                groupId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        givenRecurringRule(
                recurringId,
                LocalDate.of(2026, 9, 1),
                null,
                BigDecimal.valueOf(-100)
        );

        givenRecurringDetails(
                recurringId,
                groupId,
                accountId,
                categoryId,
                "Active recurring with only future rule",
                LocalDate.of(2026, 1, 1)
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        groupId,
                        null,
                        AS_OF_DATE
                );

        assertThat(ids(result)).doesNotContain(recurringId);
    }

    @Test
    void findReportReadableBaseByUserGroupShouldUseStrictEffectiveTo() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("strict effective group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("strict-effective-owner"));
        givenCategory(categoryId, groupId, ownerId, unique("strict effective category"));
        givenAccount(accountId, groupId, unique("strict effective account"));

        UUID closedAtTargetId = givenRecurringTransactionHeader(
                groupId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        UUID validThroughTargetId = givenRecurringTransactionHeader(
                groupId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        givenRecurringRule(
                closedAtTargetId,
                LocalDate.of(2026, 1, 1),
                AS_OF_DATE,
                BigDecimal.valueOf(-100)
        );

        givenRecurringRule(
                validThroughTargetId,
                LocalDate.of(2026, 1, 1),
                AS_OF_DATE.plusDays(1),
                BigDecimal.valueOf(-100)
        );

        givenRecurringDetails(
                closedAtTargetId,
                groupId,
                accountId,
                categoryId,
                "Closed at target",
                LocalDate.of(2026, 1, 1)
        );

        givenRecurringDetails(
                validThroughTargetId,
                groupId,
                accountId,
                categoryId,
                "Valid through target",
                LocalDate.of(2026, 1, 1)
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        groupId,
                        null,
                        AS_OF_DATE
                );

        assertThat(ids(result))
                .contains(validThroughTargetId)
                .doesNotContain(closedAtTargetId);
    }

    @Test
    void findReportReadableBaseAndSimulatedByUserGroupShouldReturnBaseAndSelectedSimulations() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        UUID simulationAId = UUID.randomUUID();
        UUID simulationBId = UUID.randomUUID();

        givenUserGroup(groupId, unique("simulation group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("simulation-owner"));
        givenCategory(categoryId, groupId, ownerId, unique("simulation category"));
        givenAccount(accountId, groupId, unique("simulation account"));

        givenSimulationGroup(simulationAId, groupId, unique("Simulation A"));
        givenSimulationGroup(simulationBId, groupId, unique("Simulation B"));

        UUID baseId = givenRecurringTransaction(
                groupId,
                accountId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Base"
        );

        UUID selectedSimulationId = givenRecurringTransaction(
                groupId,
                accountId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                true,
                simulationAId,
                "Selected simulation"
        );

        UUID excludedSimulationId = givenRecurringTransaction(
                groupId,
                accountId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                true,
                simulationBId,
                "Excluded simulation"
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseAndSimulatedByUserGroup(
                        groupId,
                        null,
                        List.of(simulationAId),
                        AS_OF_DATE
                );

        assertThat(ids(result))
                .containsExactly(baseId, selectedSimulationId)
                .doesNotContain(excludedSimulationId);
    }

    @Test
    void findReportReadableBaseByLinkedUserAccessShouldReturnOnlyLinkedAccounts() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID collaboratorId = UUID.randomUUID();

        UUID accountAId = UUID.randomUUID();
        UUID accountBId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("linked group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("linked-owner"));
        givenUser(collaboratorId, groupId, "COLLABORATOR", uniqueEmail("linked-collaborator"));

        givenCategory(categoryId, groupId, ownerId, unique("linked category"));

        givenAccount(accountAId, groupId, unique("linked account A"));
        givenAccount(accountBId, groupId, unique("linked account B"));

        givenAccountUser(accountAId, collaboratorId, groupId);

        UUID accountARecurringId = givenRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Linked account recurring"
        );

        UUID accountBRecurringId = givenRecurringTransaction(
                groupId,
                accountBId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Unlinked account recurring"
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByLinkedUserAccess(
                        groupId,
                        collaboratorId,
                        null,
                        AS_OF_DATE
                );

        assertThat(ids(result))
                .containsExactly(accountARecurringId)
                .doesNotContain(accountBRecurringId);
    }

    @Test
    void findReportReadableBaseByUserGroupForAccountsShouldReturnRecurringForRequestedAccounts() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        UUID accountAId = UUID.randomUUID();
        UUID accountBId = UUID.randomUUID();
        UUID accountCId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("for accounts group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("for-accounts-owner"));

        givenCategory(categoryId, groupId, ownerId, unique("for accounts category"));

        givenAccount(accountAId, groupId, unique("for accounts A"));
        givenAccount(accountBId, groupId, unique("for accounts B"));
        givenAccount(accountCId, groupId, unique("for accounts C"));

        UUID recurringAId = givenRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Recurring A"
        );

        UUID recurringBId = givenRecurringTransaction(
                groupId,
                accountBId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Recurring B"
        );

        UUID recurringCId = givenRecurringTransaction(
                groupId,
                accountCId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Recurring C"
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByUserGroupForAccounts(
                        groupId,
                        List.of(accountAId, accountBId),
                        AS_OF_DATE
                );

        assertThat(ids(result))
                .containsExactly(recurringAId, recurringBId)
                .doesNotContain(recurringCId);
    }

    @Test
    void findReportReadableBaseByLinkedUserAccessForAccountsShouldReturnIntersectionOfRequestedAndLinkedAccounts() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID collaboratorId = UUID.randomUUID();

        UUID accountAId = UUID.randomUUID();
        UUID accountBId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        givenUserGroup(groupId, unique("linked for accounts group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("linked-for-accounts-owner"));
        givenUser(collaboratorId, groupId, "COLLABORATOR", uniqueEmail("linked-for-accounts-collaborator"));

        givenCategory(categoryId, groupId, ownerId, unique("linked for accounts category"));

        givenAccount(accountAId, groupId, unique("linked requested A"));
        givenAccount(accountBId, groupId, unique("linked requested B"));

        givenAccountUser(accountAId, collaboratorId, groupId);

        UUID recurringAId = givenRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Linked requested recurring A"
        );

        UUID recurringBId = givenRecurringTransaction(
                groupId,
                accountBId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Requested but not linked recurring B"
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseByLinkedUserAccessForAccounts(
                        groupId,
                        collaboratorId,
                        List.of(accountAId, accountBId),
                        AS_OF_DATE
                );

        assertThat(ids(result))
                .containsExactly(recurringAId)
                .doesNotContain(recurringBId);
    }

    @Test
    void findReportReadableBaseAndSimulatedByLinkedUserAccessForAccountsShouldRespectRequestedLinkedAccountsAndSimulationGroups() {
        UUID groupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID collaboratorId = UUID.randomUUID();

        UUID accountAId = UUID.randomUUID();
        UUID accountBId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        UUID simulationAId = UUID.randomUUID();
        UUID simulationBId = UUID.randomUUID();

        givenUserGroup(groupId, unique("linked sim accounts group"));
        givenUser(ownerId, groupId, "OWNER", uniqueEmail("linked-sim-owner"));
        givenUser(collaboratorId, groupId, "COLLABORATOR", uniqueEmail("linked-sim-collaborator"));

        givenCategory(categoryId, groupId, ownerId, unique("linked sim category"));

        givenAccount(accountAId, groupId, unique("linked sim account A"));
        givenAccount(accountBId, groupId, unique("linked sim account B"));

        givenAccountUser(accountAId, collaboratorId, groupId);

        givenSimulationGroup(simulationAId, groupId, unique("Linked Simulation A"));
        givenSimulationGroup(simulationBId, groupId, unique("Linked Simulation B"));

        UUID baseLinkedId = givenRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                "Base linked"
        );

        UUID selectedSimLinkedId = givenRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                true,
                simulationAId,
                "Selected simulation linked"
        );

        UUID selectedSimUnlinkedId = givenRecurringTransaction(
                groupId,
                accountBId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                true,
                simulationAId,
                "Selected simulation unlinked"
        );

        UUID excludedSimLinkedId = givenRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                true,
                simulationBId,
                "Excluded simulation linked"
        );

        List<RecurringTransaction> result =
                recurringTransactionRepository.findReportReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
                        groupId,
                        collaboratorId,
                        List.of(accountAId, accountBId),
                        List.of(simulationAId),
                        AS_OF_DATE
                );

        assertThat(ids(result))
                .containsExactly(baseLinkedId, selectedSimLinkedId)
                .doesNotContain(selectedSimUnlinkedId, excludedSimLinkedId);
    }

    private UUID givenRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            LocalDate firstPaymentDate,
            boolean simulated,
            UUID simulationGroupId,
            String description
    ) {
        UUID recurringTransactionId = givenRecurringTransactionHeader(
                userGroupId,
                firstPaymentDate,
                simulated,
                simulationGroupId
        );

        givenRecurringRule(
                recurringTransactionId,
                LocalDate.of(2026, 1, 1),
                null,
                BigDecimal.valueOf(-100)
        );

        givenRecurringDetails(
                recurringTransactionId,
                userGroupId,
                accountId,
                categoryId,
                description,
                LocalDate.of(2026, 1, 1)
        );

        return recurringTransactionId;
    }

    private UUID givenRecurringTransactionHeader(
            UUID userGroupId,
            LocalDate firstPaymentDate,
            boolean simulated,
            UUID simulationGroupId
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
                firstPaymentDate,
                simulated,
                simulationGroupId,
                userGroupId
        );

        return recurringTransactionId;
    }

    private void givenRecurringRule(
            UUID recurringTransactionId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal paymentAmount
    ) {
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
                        VALUES (?, ?, ?, 1, 1, 'MONTH', 'NONE', ?)
                        """,
                recurringTransactionId,
                effectiveFrom,
                effectiveTo,
                paymentAmount
        );
    }

    private void givenRecurringDetails(
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
                effectiveFrom,
                userGroupId
        );
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

    private void givenUser(
            UUID userId,
            UUID userGroupId,
            String role,
            String email
    ) {
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
                "Report Repository Test User",
                email,
                userGroupId,
                role,
                "test-password-hash"
        );
    }

    private void givenCategory(
            UUID categoryId,
            UUID userGroupId,
            UUID createdByUserId,
            String name
    ) {
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

    private void givenAccount(
            UUID accountId,
            UUID userGroupId,
            String name
    ) {
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
                        """,
                accountId,
                userGroupId,
                name,
                LocalDate.of(2026, 1, 1)
        );
    }

    private void givenAccountUser(
            UUID accountId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update("""
                        INSERT INTO accounts_users (
                            account_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                accountId,
                userId,
                userGroupId
        );
    }

    private void givenSimulationGroup(
            UUID simulationGroupId,
            UUID userGroupId,
            String name
    ) {
        jdbcTemplate.update("""
                        INSERT INTO simulation_groups (
                            simulation_group_id,
                            user_group_id,
                            simulation_group_name
                        )
                        VALUES (?, ?, ?)
                        """,
                simulationGroupId,
                userGroupId,
                name
        );
    }
}