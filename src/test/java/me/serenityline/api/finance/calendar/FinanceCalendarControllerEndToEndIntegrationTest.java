package me.serenityline.api.finance.calendar;

import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceCalendarControllerEndToEndIntegrationTest extends IntegrationTestSupport {

    private static final String CALENDAR_PATH = "/api/finance/calendar";
    private static final String RECURRING_TRANSACTIONS_PATH = "/api/finance/recurring-transactions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private UUID financialPriorityId;

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
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
    void ownerShouldGetCalendarMovementsEndToEndWithJwtDbServiceRepositoriesAndProjection() throws Exception {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        UUID accountAId = UUID.randomUUID();
        UUID accountBId = UUID.randomUUID();
        UUID excludedAccountId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        UUID simulationGroupAId = UUID.randomUUID();
        UUID simulationGroupBId = UUID.randomUUID();

        givenUserGroup(userGroupId, unique("E2E calendar group"));
        givenUser(ownerId, userGroupId, "OWNER", uniqueEmail("calendar-owner-e2e"));

        givenCategory(categoryId, userGroupId, ownerId, "Casa");

        givenAccount(accountAId, userGroupId, "Conto A");
        givenAccount(accountBId, userGroupId, "Conto B");
        givenAccount(excludedAccountId, userGroupId, "Conto escluso");

        givenSimulationGroup(simulationGroupAId, userGroupId, "Simulation A");
        givenSimulationGroup(simulationGroupBId, userGroupId, "Simulation B");

        UUID baseTransactionAId = givenTransaction(
                accountAId,
                userGroupId,
                categoryId,
                LocalDate.of(2026, 6, 5),
                false,
                null,
                "Base A"
        );

        UUID baseTransactionBId = givenTransaction(
                accountBId,
                userGroupId,
                categoryId,
                LocalDate.of(2026, 6, 6),
                false,
                null,
                "Base B"
        );

        UUID selectedSimulatedTransactionId = givenTransaction(
                accountAId,
                userGroupId,
                categoryId,
                LocalDate.of(2026, 6, 7),
                true,
                simulationGroupAId,
                "Simulata selezionata"
        );

        UUID excludedSimulatedTransactionId = givenTransaction(
                accountAId,
                userGroupId,
                categoryId,
                LocalDate.of(2026, 6, 8),
                true,
                simulationGroupBId,
                "Simulata esclusa"
        );

        UUID excludedAccountTransactionId = givenTransaction(
                excludedAccountId,
                userGroupId,
                categoryId,
                LocalDate.of(2026, 6, 9),
                false,
                null,
                "Conto escluso"
        );

        UUID baseRecurringTransactionId = givenRecurringTransaction(
                userGroupId,
                accountAId,
                categoryId,
                false,
                null,
                "Ricorrente base"
        );

        UUID selectedSimulatedRecurringTransactionId = givenRecurringTransaction(
                userGroupId,
                accountAId,
                categoryId,
                true,
                simulationGroupAId,
                "Ricorrente simulata selezionata"
        );

        UUID excludedSimulatedRecurringTransactionId = givenRecurringTransaction(
                userGroupId,
                accountAId,
                categoryId,
                true,
                simulationGroupBId,
                "Ricorrente simulata esclusa"
        );

        UUID excludedAccountRecurringTransactionId = givenRecurringTransaction(
                userGroupId,
                excludedAccountId,
                categoryId,
                false,
                null,
                "Ricorrente conto escluso"
        );

        String accessToken = accessTokenFor(ownerId);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountIds", accountAId.toString(), accountBId.toString())
                        .param("simulationGroupIds", simulationGroupAId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasSize(5)))
                .andExpect(jsonPath("$[?(@.movementType == 'PERSISTED_TRANSACTION')]").value(hasSize(3)))
                .andExpect(jsonPath("$[?(@.movementType == 'PROJECTED_RECURRING_TRANSACTION')]").value(hasSize(2)))

                .andExpect(jsonPath("$[*].transactionId")
                        .value(hasItem(baseTransactionAId.toString())))
                .andExpect(jsonPath("$[*].transactionId")
                        .value(hasItem(baseTransactionBId.toString())))
                .andExpect(jsonPath("$[*].transactionId")
                        .value(hasItem(selectedSimulatedTransactionId.toString())))

                .andExpect(jsonPath("$[*].transactionId")
                        .value(not(hasItem(excludedSimulatedTransactionId.toString()))))
                .andExpect(jsonPath("$[*].transactionId")
                        .value(not(hasItem(excludedAccountTransactionId.toString()))))

                .andExpect(jsonPath("$[*].recurringTransactionId")
                        .value(hasItem(baseRecurringTransactionId.toString())))
                .andExpect(jsonPath("$[*].recurringTransactionId")
                        .value(hasItem(selectedSimulatedRecurringTransactionId.toString())))

                .andExpect(jsonPath("$[*].recurringTransactionId")
                        .value(not(hasItem(excludedSimulatedRecurringTransactionId.toString()))))
                .andExpect(jsonPath("$[*].recurringTransactionId")
                        .value(not(hasItem(excludedAccountRecurringTransactionId.toString()))))

                .andExpect(jsonPath("$[*].accountId")
                        .value(not(hasItem(excludedAccountId.toString()))));
    }

    @Test
    void ownerShouldGetDailyBalancesEndToEndWithJwtDbServiceRepositoriesAndBuckets() throws Exception {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID bucketId = UUID.randomUUID();

        givenUserGroup(userGroupId, unique("E2E daily balances group"));
        givenUser(ownerId, userGroupId, "OWNER", uniqueEmail("daily-balances-owner-e2e"));

        givenCategory(categoryId, userGroupId, ownerId, "Daily balances category");

        givenAccount(
                accountId,
                userGroupId,
                "Conto Daily Balance",
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 1, 1)
        );

        givenBucket(
                bucketId,
                userGroupId,
                accountId,
                "Fondo emergenza"
        );

        givenTransaction(
                accountId,
                userGroupId,
                categoryId,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.00"),
                true,
                true,
                false,
                null,
                "Entrata storica"
        );

        givenTransaction(
                accountId,
                userGroupId,
                categoryId,
                null,
                LocalDate.of(2026, 6, 10),
                new BigDecimal("-50.00"),
                true,
                true,
                false,
                null,
                "Spesa del giorno"
        );

        givenTransaction(
                accountId,
                userGroupId,
                categoryId,
                bucketId,
                LocalDate.of(2026, 6, 11),
                new BigDecimal("-300.00"),
                false,
                true,
                false,
                null,
                "Accantonamento fondo emergenza"
        );

        String accessToken = accessTokenFor(ownerId);

        mockMvc.perform(get(CALENDAR_PATH + "/daily-balances")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-10")
                        .param("to", "2026-06-11")
                        .param("accountIds", accountId.toString()))
                .andExpect(status().isOk())

                .andExpect(jsonPath("$[0].date").value("2026-06-10"))
                .andExpect(jsonPath("$[0].accounts[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].accounts[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].accounts[0].endOfDayAccountBalance").value(1050.00))
                .andExpect(jsonPath("$[0].accounts[0].endOfDaySerenityline").value(1050.00))
                .andExpect(jsonPath("$[0].accounts[0].endOfDayBucketsBalance").value(0))
                .andExpect(jsonPath("$[0].accounts[0].buckets").isEmpty())
                .andExpect(jsonPath("$[0].buckets").isEmpty())
                .andExpect(jsonPath("$[0].totalsByCurrency[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].totalsByCurrency[0].endOfDayAccountsBalance").value(1050.00))
                .andExpect(jsonPath("$[0].totalsByCurrency[0].endOfDaySerenityline").value(1050.00))
                .andExpect(jsonPath("$[0].totalsByCurrency[0].endOfDayBucketsBalance").value(0))

                .andExpect(jsonPath("$[1].date").value("2026-06-11"))
                .andExpect(jsonPath("$[1].accounts[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[1].accounts[0].currency").value("EUR"))
                .andExpect(jsonPath("$[1].accounts[0].endOfDayAccountBalance").value(1050.00))
                .andExpect(jsonPath("$[1].accounts[0].endOfDaySerenityline").value(750.00))
                .andExpect(jsonPath("$[1].accounts[0].endOfDayBucketsBalance").value(300.00))
                .andExpect(jsonPath("$[1].accounts[0].buckets[0].bucketId").value(bucketId.toString()))
                .andExpect(jsonPath("$[1].accounts[0].buckets[0].endOfDayBucketBalance").value(300.00))
                .andExpect(jsonPath("$[1].buckets[0].bucketId").value(bucketId.toString()))
                .andExpect(jsonPath("$[1].buckets[0].currency").value("EUR"))
                .andExpect(jsonPath("$[1].buckets[0].endOfDayBucketBalance").value(300.00))
                .andExpect(jsonPath("$[1].totalsByCurrency[0].currency").value("EUR"))
                .andExpect(jsonPath("$[1].totalsByCurrency[0].endOfDayAccountsBalance").value(1050.00))
                .andExpect(jsonPath("$[1].totalsByCurrency[0].endOfDaySerenityline").value(750.00))
                .andExpect(jsonPath("$[1].totalsByCurrency[0].endOfDayBucketsBalance").value(300.00));
    }

    @Test
    void ownerShouldConfirmRecurringOccurrenceAndCalendarShouldShowPersistedTransactionInsteadOfProjectedOccurrence() throws Exception {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        LocalDate logicalDate = LocalDate.of(2026, 6, 10);

        givenUserGroup(userGroupId, unique("E2E recurring confirmation group"));
        givenUser(ownerId, userGroupId, "OWNER", uniqueEmail("recurring-confirm-owner-e2e"));

        givenCategory(categoryId, userGroupId, ownerId, "Ricorrenti confermate");
        givenAccount(accountId, userGroupId, "Conto conferma ricorrente");

        UUID recurringTransactionId = givenRecurringTransaction(
                userGroupId,
                accountId,
                categoryId,
                false,
                null,
                "Affitto ricorrente da confermare"
        );

        String accessToken = accessTokenFor(ownerId);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", logicalDate.toString())
                        .param("to", logicalDate.toString())
                        .param("accountIds", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasSize(1)))
                .andExpect(jsonPath("$[0].movementType").value("PROJECTED_RECURRING_TRANSACTION"))
                .andExpect(jsonPath("$[0].transactionId").doesNotExist())
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$[0].logicalDate").value(logicalDate.toString()))
                .andExpect(jsonPath("$[0].chargeDate").value(logicalDate.toString()))
                .andExpect(jsonPath("$[0].description").value("Affitto ricorrente da confermare"))
                .andExpect(jsonPath("$[0].amount").value(-100.00))
                .andExpect(jsonPath("$[0].confirmed").value(false))
                .andExpect(jsonPath("$[0].userEntered").value(false))
                .andExpect(jsonPath("$[0].finalOccurrence").value(false));

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-06-10"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionDescription").value("Affitto ricorrente da confermare"))
                .andExpect(jsonPath("$.transactionAmount").value(-100.00))
                .andExpect(jsonPath("$.transactionChargeDate").value(logicalDate.toString()))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(true))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.transactionIsSimulated").value(false))
                .andExpect(jsonPath("$.simulationGroupId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsUserEntered").value(false))
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.recurringTransactionLogicalDate").value(logicalDate.toString()))
                .andExpect(jsonPath("$.recurringTransactionConfirmedAt").exists())
                .andExpect(jsonPath("$.transactionReminderEnabled").value(true))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(7));

        UUID confirmedTransactionId = jdbcTemplate.queryForObject("""
                        SELECT transaction_id
                        FROM transactions
                        WHERE user_group_id = ?
                          AND recurring_transaction_id = ?
                          AND recurring_transaction_logical_date = ?
                          AND transaction_is_user_entered = FALSE
                          AND transaction_is_confirmed = TRUE
                        """,
                UUID.class,
                userGroupId,
                recurringTransactionId,
                logicalDate
        );

        assertThat(confirmedTransactionId).isNotNull();

        Integer confirmedRows = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM transactions
                        WHERE user_group_id = ?
                          AND recurring_transaction_id = ?
                          AND recurring_transaction_logical_date = ?
                          AND transaction_is_user_entered = FALSE
                          AND transaction_is_confirmed = TRUE
                        """,
                Integer.class,
                userGroupId,
                recurringTransactionId,
                logicalDate
        );

        assertThat(confirmedRows).isEqualTo(1);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", logicalDate.toString())
                        .param("to", logicalDate.toString())
                        .param("accountIds", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasSize(1)))
                .andExpect(jsonPath("$[?(@.movementType == 'PROJECTED_RECURRING_TRANSACTION')]").value(hasSize(0)))
                .andExpect(jsonPath("$[?(@.movementType == 'PERSISTED_TRANSACTION')]").value(hasSize(1)))
                .andExpect(jsonPath("$[0].movementType").value("PERSISTED_TRANSACTION"))
                .andExpect(jsonPath("$[0].transactionId").value(confirmedTransactionId.toString()))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$[0].logicalDate").value(logicalDate.toString()))
                .andExpect(jsonPath("$[0].chargeDate").value(logicalDate.toString()))
                .andExpect(jsonPath("$[0].description").value("Affitto ricorrente da confermare"))
                .andExpect(jsonPath("$[0].amount").value(-100.00))
                .andExpect(jsonPath("$[0].confirmed").value(true))
                .andExpect(jsonPath("$[0].simulated").value(false))
                .andExpect(jsonPath("$[0].userEntered").value(false))
                .andExpect(jsonPath("$[0].finalOccurrence").value(false));
    }

    private String accessTokenFor(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow();

        return jwtTokenService.createAccessToken(user)
                .token();
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
                "Calendar E2E Test User",
                email,
                userGroupId,
                role,
                "test-password-hash"
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
            UUID categoryId,
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

    private void givenAccount(
            UUID accountId,
            UUID userGroupId,
            String name,
            BigDecimal openingBalance,
            LocalDate openingBalanceDate
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
                        VALUES (?, ?, ?, 'EUR', ?, ?)
                        """,
                accountId,
                userGroupId,
                name,
                openingBalance,
                openingBalanceDate
        );
    }

    private void givenBucket(
            UUID bucketId,
            UUID userGroupId,
            UUID accountId,
            String name
    ) {
        jdbcTemplate.update("""
                        INSERT INTO buckets (
                            bucket_id,
                            user_group_id,
                            bucket_name
                        )
                        VALUES (?, ?, ?)
                        """,
                bucketId,
                userGroupId,
                name
        );

        jdbcTemplate.update("""
                        INSERT INTO buckets_accounts (
                            bucket_id,
                            account_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                bucketId,
                accountId,
                userGroupId
        );
    }

    private UUID givenTransaction(
            UUID accountId,
            UUID userGroupId,
            UUID categoryId,
            UUID bucketId,
            LocalDate chargeDate,
            BigDecimal amount,
            boolean affectsAccountBalance,
            boolean affectsSerenityline,
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
                            bucket_id,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?, true, true, 7, ?)
                        """,
                transactionId,
                description,
                amount,
                affectsAccountBalance,
                affectsSerenityline,
                categoryId,
                chargeDate,
                accountId,
                bucketId,
                simulated,
                simulationGroupId,
                userGroupId
        );

        return transactionId;
    }
}