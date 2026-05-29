package me.serenityline.api.finance.calendar;

import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceCalendarControllerEndToEndIntegrationTest extends IntegrationTestSupport {

    private static final String CALENDAR_PATH = "/api/finance/calendar";

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
                            transaction_affects_liquidity,
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
                LocalDate.of(2026, 1, 1),
                userGroupId
        );

        return recurringTransactionId;
    }
}