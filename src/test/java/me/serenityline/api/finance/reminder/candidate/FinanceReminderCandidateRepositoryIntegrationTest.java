package me.serenityline.api.finance.reminder.candidate;

import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FinanceReminderCandidateRepositoryIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);
    private static final LocalDate OPENING_BALANCE_DATE = LocalDate.of(2026, 1, 1);
    private static final String CURRENCY = "EUR";
    private final List<UUID> createdUserGroupIds = new ArrayList<>();
    @Autowired
    private FinanceReminderCandidateRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@example.com";
    }

    @AfterEach
    void tearDown() {
        for (UUID userGroupId : createdUserGroupIds) {
            deleteGroupData(userGroupId);
        }

        createdUserGroupIds.clear();
    }

    @Test
    void shouldFindDueTransactionCandidate() {
        TestContext context = createContext();

        UUID transactionId = UUID.randomUUID();

        insertUserEnteredTransaction(
                context,
                transactionId,
                "Affitto",
                new BigDecimal("-750.00"),
                LocalDate.of(2026, 6, 17),
                true,
                (short) 7
        );

        insertTransactionUser(
                transactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        List<FinanceReminderCandidate> candidates =
                transactionCandidatesForGroup(context.userGroupId());

        assertThat(candidates)
                .extracting(
                        FinanceReminderCandidate::userId,
                        FinanceReminderCandidate::userGroupId,
                        FinanceReminderCandidate::transactionId,
                        FinanceReminderCandidate::recurringTransactionId,
                        FinanceReminderCandidate::recurringTransactionLogicalDate,
                        FinanceReminderCandidate::chargeDate,
                        FinanceReminderCandidate::notifiedDescription,
                        FinanceReminderCandidate::notifiedAmount,
                        FinanceReminderCandidate::notifiedCurrency,
                        FinanceReminderCandidate::reminderDate
                )
                .containsExactly(tuple(
                        context.ownerUserId(),
                        context.userGroupId(),
                        transactionId,
                        null,
                        null,
                        LocalDate.of(2026, 6, 17),
                        "Affitto",
                        new BigDecimal("-750.00"),
                        CURRENCY,
                        TODAY
                ));
    }

    @Test
    void shouldFindCatchUpTransactionCandidateWhenReminderDateIsBeforeTodayAndChargeDateIsFuture() {
        TestContext context = createContext();

        UUID transactionId = UUID.randomUUID();

        insertUserEnteredTransaction(
                context,
                transactionId,
                "Assicurazione auto",
                new BigDecimal("-320.00"),
                LocalDate.of(2026, 6, 25),
                true,
                (short) 20
        );

        insertTransactionUser(
                transactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        List<FinanceReminderCandidate> candidates =
                transactionCandidatesForGroup(context.userGroupId());

        assertThat(candidates)
                .extracting(
                        FinanceReminderCandidate::transactionId,
                        FinanceReminderCandidate::chargeDate,
                        FinanceReminderCandidate::reminderDate,
                        FinanceReminderCandidate::notifiedDescription
                )
                .containsExactly(tuple(
                        transactionId,
                        LocalDate.of(2026, 6, 25),
                        LocalDate.of(2026, 6, 5),
                        "Assicurazione auto"
                ));
    }

    @Test
    void shouldCreateOneTransactionCandidateForEachLinkedReminderEnabledUser() {
        TestContext context = createContext();

        UUID collaboratorUserId = insertUser(
                context.userGroupId(),
                "COLLABORATOR",
                true,
                true
        );

        UUID transactionId = UUID.randomUUID();

        insertUserEnteredTransaction(
                context,
                transactionId,
                "Bolletta gas",
                new BigDecimal("-140.00"),
                LocalDate.of(2026, 6, 17),
                true,
                (short) 7
        );

        insertTransactionUser(
                transactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        insertTransactionUser(
                transactionId,
                collaboratorUserId,
                context.userGroupId()
        );

        List<FinanceReminderCandidate> candidates =
                transactionCandidatesForGroup(context.userGroupId());

        assertThat(candidates)
                .extracting(
                        FinanceReminderCandidate::userId,
                        FinanceReminderCandidate::transactionId,
                        FinanceReminderCandidate::notifiedDescription
                )
                .containsExactlyInAnyOrder(
                        tuple(context.ownerUserId(), transactionId, "Bolletta gas"),
                        tuple(collaboratorUserId, transactionId, "Bolletta gas")
                );
    }

    @Test
    void shouldIgnoreNonEligibleTransactionCandidates() {
        TestContext context = createContext();

        UUID simulationGroupId = insertSimulationGroup(context.userGroupId());

        UUID paymentReminderDisabledUserId = insertUser(
                context.userGroupId(),
                "COLLABORATOR",
                true,
                false
        );

        UUID disabledUserId = insertUser(
                context.userGroupId(),
                "COLLABORATOR",
                false,
                true
        );

        UUID reminderDateAfterTodayTransactionId = UUID.randomUUID();
        insertUserEnteredTransaction(
                context,
                reminderDateAfterTodayTransactionId,
                "Reminder futuro",
                new BigDecimal("-10.00"),
                LocalDate.of(2026, 6, 20),
                true,
                (short) 3
        );
        insertTransactionUser(
                reminderDateAfterTodayTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        UUID pastChargeDateTransactionId = UUID.randomUUID();
        insertUserEnteredTransaction(
                context,
                pastChargeDateTransactionId,
                "Movimento passato",
                new BigDecimal("-20.00"),
                LocalDate.of(2026, 6, 9),
                true,
                (short) 7
        );
        insertTransactionUser(
                pastChargeDateTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        UUID reminderDisabledTransactionId = UUID.randomUUID();
        insertUserEnteredTransaction(
                context,
                reminderDisabledTransactionId,
                "Reminder disattivato",
                new BigDecimal("-30.00"),
                LocalDate.of(2026, 6, 17),
                false,
                (short) 7
        );
        insertTransactionUser(
                reminderDisabledTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        UUID simulatedTransactionId = UUID.randomUUID();
        insertUserEnteredSimulatedTransaction(
                context,
                simulatedTransactionId,
                simulationGroupId,
                "Movimento simulato",
                new BigDecimal("-40.00"),
                LocalDate.of(2026, 6, 17)
        );
        insertTransactionUser(
                simulatedTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        UUID paymentReminderDisabledUserTransactionId = UUID.randomUUID();
        insertUserEnteredTransaction(
                context,
                paymentReminderDisabledUserTransactionId,
                "Utente reminder globali off",
                new BigDecimal("-50.00"),
                LocalDate.of(2026, 6, 17),
                true,
                (short) 7
        );
        insertTransactionUser(
                paymentReminderDisabledUserTransactionId,
                paymentReminderDisabledUserId,
                context.userGroupId()
        );

        UUID disabledUserTransactionId = UUID.randomUUID();
        insertUserEnteredTransaction(
                context,
                disabledUserTransactionId,
                "Utente disabilitato",
                new BigDecimal("-60.00"),
                LocalDate.of(2026, 6, 17),
                true,
                (short) 7
        );
        insertTransactionUser(
                disabledUserTransactionId,
                disabledUserId,
                context.userGroupId()
        );

        UUID recurringTransactionId = UUID.randomUUID();
        insertRecurringTransaction(
                context,
                recurringTransactionId,
                LocalDate.of(2026, 1, 10),
                false,
                null,
                true,
                (short) 7
        );
        insertRecurringTransactionUser(
                recurringTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        UUID systemGeneratedRecurringOccurrenceTransactionId = UUID.randomUUID();
        insertConfirmedRecurringOccurrenceTransaction(
                context,
                systemGeneratedRecurringOccurrenceTransactionId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 17),
                LocalDate.of(2026, 6, 17),
                "Transaction da recurring confermata",
                new BigDecimal("-70.00")
        );
        insertTransactionUser(
                systemGeneratedRecurringOccurrenceTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        List<FinanceReminderCandidate> candidates =
                transactionCandidatesForGroup(context.userGroupId());

        assertThat(candidates).isEmpty();
    }

    @Test
    void shouldIgnoreTransactionWhenNotificationAlreadyExists() {
        TestContext context = createContext();

        UUID transactionId = UUID.randomUUID();

        insertUserEnteredTransaction(
                context,
                transactionId,
                "Rata già notificata",
                new BigDecimal("-250.00"),
                LocalDate.of(2026, 6, 17),
                true,
                (short) 7
        );

        insertTransactionUser(
                transactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        insertTransactionReminderNotification(
                context.ownerUserId(),
                context.userGroupId(),
                transactionId,
                LocalDate.of(2026, 6, 17),
                "Rata già notificata",
                new BigDecimal("-250.00"),
                TODAY
        );

        List<FinanceReminderCandidate> candidates =
                transactionCandidatesForGroup(context.userGroupId());

        assertThat(candidates).isEmpty();
    }

    @Test
    void shouldFindRecurringReminderSeed() {
        TestContext context = createContext();

        UUID recurringTransactionId = UUID.randomUUID();

        insertRecurringTransaction(
                context,
                recurringTransactionId,
                LocalDate.of(2026, 1, 10),
                false,
                null,
                true,
                (short) 7
        );

        insertRecurringTransactionUser(
                recurringTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        List<RecurringFinanceReminderSeed> seeds =
                recurringSeedsForGroup(context.userGroupId());

        assertThat(seeds)
                .extracting(
                        RecurringFinanceReminderSeed::recurringTransactionId,
                        RecurringFinanceReminderSeed::userGroupId,
                        RecurringFinanceReminderSeed::firstPaymentDate,
                        RecurringFinanceReminderSeed::reminderDaysBefore
                )
                .containsExactly(tuple(
                        recurringTransactionId,
                        context.userGroupId(),
                        LocalDate.of(2026, 1, 10),
                        (short) 7
                ));
    }

    @Test
    void shouldIgnoreNonEligibleRecurringSeeds() {
        TestContext context = createContext();

        UUID simulationGroupId = insertSimulationGroup(context.userGroupId());

        UUID paymentReminderDisabledUserId = insertUser(
                context.userGroupId(),
                "COLLABORATOR",
                true,
                false
        );

        UUID reminderDisabledRecurringTransactionId = UUID.randomUUID();
        insertRecurringTransaction(
                context,
                reminderDisabledRecurringTransactionId,
                LocalDate.of(2026, 1, 10),
                false,
                null,
                false,
                (short) 7
        );
        insertRecurringTransactionUser(
                reminderDisabledRecurringTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        UUID simulatedRecurringTransactionId = UUID.randomUUID();
        insertRecurringTransaction(
                context,
                simulatedRecurringTransactionId,
                LocalDate.of(2026, 1, 10),
                true,
                simulationGroupId,
                true,
                (short) 7
        );
        insertRecurringTransactionUser(
                simulatedRecurringTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        UUID noReminderEnabledUserRecurringTransactionId = UUID.randomUUID();
        insertRecurringTransaction(
                context,
                noReminderEnabledUserRecurringTransactionId,
                LocalDate.of(2026, 1, 10),
                false,
                null,
                true,
                (short) 7
        );
        insertRecurringTransactionUser(
                noReminderEnabledUserRecurringTransactionId,
                paymentReminderDisabledUserId,
                context.userGroupId()
        );

        UUID futureFirstPaymentDateRecurringTransactionId = UUID.randomUUID();
        insertRecurringTransaction(
                context,
                futureFirstPaymentDateRecurringTransactionId,
                TODAY.plusDays(367),
                false,
                null,
                true,
                (short) 7
        );
        insertRecurringTransactionUser(
                futureFirstPaymentDateRecurringTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        List<RecurringFinanceReminderSeed> seeds =
                recurringSeedsForGroup(context.userGroupId());

        assertThat(seeds).isEmpty();
    }

    @Test
    void shouldFindReminderEnabledUserIdsByRecurringTransactionId() {
        TestContext context = createContext();

        UUID enabledCollaboratorUserId = insertUser(
                context.userGroupId(),
                "COLLABORATOR",
                true,
                true
        );

        UUID paymentReminderDisabledUserId = insertUser(
                context.userGroupId(),
                "COLLABORATOR",
                true,
                false
        );

        UUID disabledUserId = insertUser(
                context.userGroupId(),
                "COLLABORATOR",
                false,
                true
        );

        UUID recurringTransactionId = UUID.randomUUID();

        insertRecurringTransaction(
                context,
                recurringTransactionId,
                LocalDate.of(2026, 1, 10),
                false,
                null,
                true,
                (short) 7
        );

        insertRecurringTransactionUser(
                recurringTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        insertRecurringTransactionUser(
                recurringTransactionId,
                enabledCollaboratorUserId,
                context.userGroupId()
        );

        insertRecurringTransactionUser(
                recurringTransactionId,
                paymentReminderDisabledUserId,
                context.userGroupId()
        );

        insertRecurringTransactionUser(
                recurringTransactionId,
                disabledUserId,
                context.userGroupId()
        );

        Map<UUID, List<UUID>> result =
                repository.findReminderEnabledUserIdsByRecurringTransactionId(
                        context.userGroupId(),
                        List.of(recurringTransactionId)
                );

        assertThat(result)
                .containsOnlyKeys(recurringTransactionId);

        assertThat(result.get(recurringTransactionId))
                .containsExactlyInAnyOrder(
                        context.ownerUserId(),
                        enabledCollaboratorUserId
                );
    }

    @Test
    void shouldFindConfirmedRecurringOccurrenceSnapshot() {
        TestContext context = createContext();

        UUID recurringTransactionId = UUID.randomUUID();

        insertRecurringTransaction(
                context,
                recurringTransactionId,
                LocalDate.of(2026, 1, 10),
                false,
                null,
                true,
                (short) 7
        );

        insertRecurringTransactionUser(
                recurringTransactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        UUID transactionId = UUID.randomUUID();

        insertConfirmedRecurringOccurrenceTransaction(
                context,
                transactionId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 17),
                LocalDate.of(2026, 6, 15),
                "Snapshot da transaction confermata",
                new BigDecimal("-95.50")
        );

        List<FinanceReminderConfirmedRecurringOccurrenceSnapshot> snapshots =
                repository.findConfirmedRecurringOccurrenceSnapshots(
                        context.userGroupId(),
                        List.of(recurringTransactionId),
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30)
                );

        assertThat(snapshots)
                .extracting(
                        FinanceReminderConfirmedRecurringOccurrenceSnapshot::recurringTransactionId,
                        FinanceReminderConfirmedRecurringOccurrenceSnapshot::recurringTransactionLogicalDate,
                        FinanceReminderConfirmedRecurringOccurrenceSnapshot::chargeDate,
                        FinanceReminderConfirmedRecurringOccurrenceSnapshot::notifiedDescription,
                        FinanceReminderConfirmedRecurringOccurrenceSnapshot::notifiedAmount,
                        FinanceReminderConfirmedRecurringOccurrenceSnapshot::notifiedCurrency
                )
                .containsExactly(tuple(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 17),
                        LocalDate.of(2026, 6, 15),
                        "Snapshot da transaction confermata",
                        new BigDecimal("-95.50"),
                        CURRENCY
                ));
    }

    private List<FinanceReminderCandidate> transactionCandidatesForGroup(UUID userGroupId) {
        return repository.findDueTransactionCandidates(TODAY, 10_000)
                .stream()
                .filter(candidate -> candidate.userGroupId().equals(userGroupId))
                .toList();
    }

    private List<RecurringFinanceReminderSeed> recurringSeedsForGroup(UUID userGroupId) {
        return repository.findRecurringReminderSeeds(TODAY, 10_000)
                .stream()
                .filter(seed -> seed.userGroupId().equals(userGroupId))
                .toList();
    }

    private TestContext createContext() {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        createdUserGroupIds.add(userGroupId);

        jdbcTemplate.update("""
                        INSERT INTO user_groups (
                            user_group_id,
                            user_group_name
                        )
                        VALUES (?, ?)
                        """,
                userGroupId,
                "Reminder Candidate Test Group " + userGroupId
        );

        insertUser(
                ownerUserId,
                userGroupId,
                "OWNER",
                true,
                true
        );

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
                ownerUserId,
                "Reminder Candidate Category " + categoryId
        );

        jdbcTemplate.update("""
                        INSERT INTO accounts (
                            account_id,
                            account_name,
                            currency,
                            opening_balance,
                            opening_balance_date,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                accountId,
                "Reminder Candidate Account " + accountId,
                CURRENCY,
                BigDecimal.ZERO,
                OPENING_BALANCE_DATE,
                userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO accounts_users (
                            account_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                accountId,
                ownerUserId,
                userGroupId
        );

        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        return new TestContext(
                userGroupId,
                ownerUserId,
                accountId,
                categoryId,
                financialPriorityId
        );
    }

    private UUID insertUser(
            UUID userGroupId,
            String userRole,
            boolean enabled,
            boolean paymentEmailRemindersEnabled
    ) {
        UUID userId = UUID.randomUUID();

        insertUser(
                userId,
                userGroupId,
                userRole,
                enabled,
                paymentEmailRemindersEnabled
        );

        return userId;
    }

    private void insertUser(
            UUID userId,
            UUID userGroupId,
            String userRole,
            boolean enabled,
            boolean paymentEmailRemindersEnabled
    ) {
        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_platform_role,
                            preferred_locale,
                            preferred_theme,
                            wants_invoice,
                            user_password_hash,
                            user_is_enabled,
                            payment_email_reminders_enabled
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                "Reminder Candidate User " + userId,
                uniqueEmail("reminder-candidate"),
                userGroupId,
                userRole,
                "USER",
                "it-IT",
                "LIGHT",
                false,
                "{noop}password",
                enabled,
                paymentEmailRemindersEnabled
        );
    }

    private UUID insertSimulationGroup(UUID userGroupId) {
        UUID simulationGroupId = UUID.randomUUID();

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
                "Reminder Candidate Simulation " + simulationGroupId
        );

        return simulationGroupId;
    }

    private void insertUserEnteredTransaction(
            TestContext context,
            UUID transactionId,
            String description,
            BigDecimal amount,
            LocalDate chargeDate,
            boolean reminderEnabled,
            short reminderDaysBefore
    ) {
        jdbcTemplate.update("""
                        INSERT INTO transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            category_id,
                            transaction_charge_date,
                            account_id,
                            transaction_is_confirmed,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                transactionId,
                description,
                amount,
                context.categoryId(),
                chargeDate,
                context.accountId(),
                false,
                false,
                null,
                true,
                null,
                null,
                null,
                reminderEnabled,
                reminderDaysBefore,
                context.userGroupId()
        );
    }

    private void insertUserEnteredSimulatedTransaction(
            TestContext context,
            UUID transactionId,
            UUID simulationGroupId,
            String description,
            BigDecimal amount,
            LocalDate chargeDate
    ) {
        jdbcTemplate.update("""
                        INSERT INTO transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            category_id,
                            transaction_charge_date,
                            account_id,
                            transaction_is_confirmed,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                transactionId,
                description,
                amount,
                context.categoryId(),
                chargeDate,
                context.accountId(),
                false,
                true,
                simulationGroupId,
                true,
                null,
                null,
                null,
                true,
                (short) 7,
                context.userGroupId()
        );
    }

    private void insertConfirmedRecurringOccurrenceTransaction(
            TestContext context,
            UUID transactionId,
            UUID recurringTransactionId,
            LocalDate logicalDate,
            LocalDate chargeDate,
            String description,
            BigDecimal amount
    ) {
        jdbcTemplate.update("""
                        INSERT INTO transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            category_id,
                            transaction_charge_date,
                            account_id,
                            transaction_is_confirmed,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                transactionId,
                description,
                amount,
                context.categoryId(),
                chargeDate,
                context.accountId(),
                true,
                false,
                null,
                false,
                recurringTransactionId,
                logicalDate,
                OffsetDateTime.now(),
                true,
                (short) 7,
                context.userGroupId()
        );
    }

    private void insertTransactionUser(
            UUID transactionId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update("""
                        INSERT INTO transactions_users (
                            transaction_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                transactionId,
                userId,
                userGroupId
        );
    }

    private void insertRecurringTransaction(
            TestContext context,
            UUID recurringTransactionId,
            LocalDate firstPaymentDate,
            boolean simulated,
            UUID simulationGroupId,
            boolean reminderEnabled,
            short reminderDaysBefore
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
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                recurringTransactionId,
                true,
                firstPaymentDate,
                simulated,
                simulationGroupId,
                reminderEnabled,
                reminderDaysBefore,
                context.userGroupId()
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
                        VALUES (?, ?, NULL, ?, 1, 'WEEK', 'NONE', ?, NULL, NULL)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                firstPaymentDate.getDayOfWeek().getValue(),
                new BigDecimal("-100.00")
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
                "Recurring reminder candidate " + recurringTransactionId,
                context.categoryId(),
                context.financialPriorityId(),
                context.accountId(),
                firstPaymentDate,
                context.userGroupId()
        );
    }

    private void insertRecurringTransactionUser(
            UUID recurringTransactionId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions_users (
                            recurring_transaction_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                recurringTransactionId,
                userId,
                userGroupId
        );
    }

    private void insertTransactionReminderNotification(
            UUID userId,
            UUID userGroupId,
            UUID transactionId,
            LocalDate chargeDate,
            String notifiedDescription,
            BigDecimal notifiedAmount,
            LocalDate reminderDate
    ) {
        jdbcTemplate.update("""
                        INSERT INTO finance_reminder_notifications (
                            user_id,
                            user_group_id,
                            transaction_id,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            charge_date,
                            notified_description,
                            notified_amount,
                            notified_currency,
                            reminder_date
                        )
                        VALUES (?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?)
                        """,
                userId,
                userGroupId,
                transactionId,
                chargeDate,
                notifiedDescription,
                notifiedAmount,
                CURRENCY,
                reminderDate
        );
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

    private void deleteGroupData(UUID userGroupId) {
        jdbcTemplate.update("""
                DELETE FROM finance_reminder_notifications
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM transactions_users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM transactions
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transactions_users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transaction_details_history
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transaction_history history
                USING recurring_transactions recurring
                WHERE history.recurring_transaction_id = recurring.recurring_transaction_id
                  AND recurring.user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transactions
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM simulation_groups_accounts
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM simulation_groups
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM accounts_users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM accounts
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM category_details_history details
                USING categories category
                WHERE details.category_id = category.category_id
                  AND category.user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM category_status_history status
                USING categories category
                WHERE status.category_id = category.category_id
                  AND category.user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM categories
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM user_groups
                WHERE user_group_id = ?
                """, userGroupId);
    }

    private record TestContext(
            UUID userGroupId,
            UUID ownerUserId,
            UUID accountId,
            UUID categoryId,
            UUID financialPriorityId
    ) {
    }
}