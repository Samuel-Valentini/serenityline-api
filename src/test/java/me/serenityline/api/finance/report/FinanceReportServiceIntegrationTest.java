package me.serenityline.api.finance.report;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.calendar.*;
import me.serenityline.api.finance.common.FinanceProperties;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;
import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FinanceReportServiceIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final LocalDate EXTREMES_FROM = LocalDate.of(2026, 5, 15);
    private static final LocalDate EXTREMES_TO = LocalDate.of(2026, 7, 15);
    private static final LocalDate BALANCES_TO = LocalDate.of(2027, 12, 31);

    @Autowired
    private FinanceReportService financeReportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FinanceCalendarService financeCalendarService;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private FinanceCalendarProperties financeCalendarProperties;

    @MockitoBean
    private FinanceProperties financeProperties;

    @MockitoBean
    private FinanceReportProperties financeReportProperties;

    private UUID financialPriorityId;

    private static FinanceCalendarDailyBalance dailyBalance(
            LocalDate date,
            String currency,
            String accountBalance,
            String serenityline
    ) {
        return new FinanceCalendarDailyBalance(
                date,
                List.of(),
                List.of(),
                List.of(new FinanceCalendarCurrencyDailyBalance(
                        currency,
                        new BigDecimal(accountBalance),
                        new BigDecimal(serenityline),
                        BigDecimal.ZERO
                ))
        );
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

        when(clock.getZone())
                .thenReturn(ZoneOffset.UTC);

        when(clock.instant())
                .thenReturn(Instant.parse("2026-06-15T00:00:00Z"));

        when(financeCalendarProperties.getMaxAccountIds())
                .thenReturn(50);

        when(financeProperties.getMaxSimulationGroupIds())
                .thenReturn(50);

        when(financeReportProperties.getExtremesPastMonths())
                .thenReturn(1);

        when(financeReportProperties.getExtremesFutureMonths())
                .thenReturn(1);

        when(financeReportProperties.getYearEndForecastYears())
                .thenReturn(1);

        when(financeReportProperties.getTrendMinDays())
                .thenReturn(2);
    }

    @Test
    void ownerShouldBuildReportSummaryUsingRealRepositoriesAndOneCalendarCall() {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        UUID accountAId = UUID.randomUUID();
        UUID accountBId = UUID.randomUUID();
        UUID excludedAccountId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        UUID simulationGroupAId = UUID.randomUUID();
        UUID simulationGroupBId = UUID.randomUUID();

        givenUserGroup(userGroupId, unique("report service group"));
        givenUser(ownerId, userGroupId, "OWNER", uniqueEmail("report-owner"));

        givenCategory(categoryId, userGroupId, ownerId, unique("report category"));

        givenAccount(accountAId, userGroupId, unique("report account A"));
        givenAccount(accountBId, userGroupId, unique("report account B"));
        givenAccount(excludedAccountId, userGroupId, unique("report excluded account"));

        givenSimulationGroup(simulationGroupAId, userGroupId, unique("Report Simulation A"));
        givenSimulationGroup(simulationGroupBId, userGroupId, unique("Report Simulation B"));

        givenRecurringTransaction(
                userGroupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                new BigDecimal("100.00"),
                true,
                "Base monthly income"
        );

        givenRecurringTransaction(
                userGroupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                true,
                simulationGroupAId,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.YEAR,
                (short) 1,
                new BigDecimal("-600.00"),
                true,
                "Selected simulated yearly expense"
        );

        givenRecurringTransaction(
                userGroupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                true,
                simulationGroupBId,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.YEAR,
                (short) 1,
                new BigDecimal("-999.00"),
                true,
                "Excluded simulated expense"
        );

        givenRecurringTransaction(
                userGroupId,
                excludedAccountId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                new BigDecimal("-50.00"),
                true,
                "Excluded account recurring"
        );

        givenRecurringTransaction(
                userGroupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                new BigDecimal("-25.00"),
                false,
                "Does not affect serenityline"
        );

        when(financeCalendarService.getDailyBalancesForReport(
                eq(ownerId),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of(
                dailyBalance(EXTREMES_FROM, "EUR", "500.00", "600.00"),
                dailyBalance(TODAY, "EUR", "700.00", "300.00"),
                dailyBalance(EXTREMES_TO, "EUR", "1000.00", "900.00"),
                dailyBalance(LocalDate.of(2026, 12, 31), "EUR", "1100.00", "950.00"),
                dailyBalance(LocalDate.of(2027, 12, 31), "EUR", "1200.00", "850.00")
        ));

        FinanceReportSummary summary = financeReportService.getReportSummary(
                ownerId,
                new FinanceReportSummaryRequest(
                        List.of(accountAId, accountBId, accountAId),
                        List.of(simulationGroupAId, simulationGroupAId)
                )
        );

        assertThat(summary.asOfDate()).isEqualTo(TODAY);
        assertThat(summary.projectionMode()).isEqualTo(FinanceReportProjectionMode.PROJECTED_PLANNING);
        assertThat(summary.extremesRange()).isEqualTo(new FinanceReportRange(EXTREMES_FROM, EXTREMES_TO));
        assertThat(summary.yearEndForecastYears()).isEqualTo(1);

        assertThat(summary.recurringByCurrency())
                .singleElement()
                .satisfies(recurring -> {
                    assertThat(recurring.currency()).isEqualTo("EUR");
                    assertThat(recurring.annualIncome()).isEqualByComparingTo("1200.00");
                    assertThat(recurring.annualExpenses()).isEqualByComparingTo("600.00");
                    assertThat(recurring.annualNetBalance()).isEqualByComparingTo("600.00");
                    assertThat(recurring.averageMonthlyIncome()).isEqualByComparingTo("100.00");
                    assertThat(recurring.averageMonthlyExpenses()).isEqualByComparingTo("50.00");
                    assertThat(recurring.averageMonthlyNetBalance()).isEqualByComparingTo("50.00");
                });

        assertThat(summary.extremesByCurrency())
                .singleElement()
                .satisfies(extremes -> {
                    assertThat(extremes.currency()).isEqualTo("EUR");

                    assertThat(extremes.minSerenityline().date()).isEqualTo(TODAY);
                    assertThat(extremes.minSerenityline().value()).isEqualByComparingTo("300.00");

                    assertThat(extremes.maxSerenityline().date()).isEqualTo(EXTREMES_TO);
                    assertThat(extremes.maxSerenityline().value()).isEqualByComparingTo("900.00");

                    assertThat(extremes.minAccountBalance().date()).isEqualTo(EXTREMES_FROM);
                    assertThat(extremes.minAccountBalance().value()).isEqualByComparingTo("500.00");

                    assertThat(extremes.maxAccountBalance().date()).isEqualTo(EXTREMES_TO);
                    assertThat(extremes.maxAccountBalance().value()).isEqualByComparingTo("1000.00");
                });

        assertThat(summary.yearEndForecasts())
                .hasSize(2);

        assertThat(summary.yearEndForecasts().get(0).year()).isEqualTo(2026);
        assertThat(summary.yearEndForecasts().get(0).date()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(summary.yearEndForecasts().get(0).balancesByCurrency())
                .singleElement()
                .satisfies(balance -> {
                    assertThat(balance.currency()).isEqualTo("EUR");
                    assertThat(balance.endOfYearAccountBalance()).isEqualByComparingTo("1100.00");
                    assertThat(balance.endOfYearSerenityline()).isEqualByComparingTo("950.00");
                });

        assertThat(summary.yearEndForecasts().get(1).year()).isEqualTo(2027);
        assertThat(summary.yearEndForecasts().get(1).date()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(summary.yearEndForecasts().get(1).balancesByCurrency())
                .singleElement()
                .satisfies(balance -> {
                    assertThat(balance.currency()).isEqualTo("EUR");
                    assertThat(balance.endOfYearAccountBalance()).isEqualByComparingTo("1200.00");
                    assertThat(balance.endOfYearSerenityline()).isEqualByComparingTo("850.00");
                });

        ArgumentCaptor<FinanceCalendarSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceCalendarSearchRequest.class);

        verify(financeCalendarService).getDailyBalancesForReport(
                eq(ownerId),
                requestCaptor.capture()
        );

        FinanceCalendarSearchRequest calendarRequest = requestCaptor.getValue();

        assertThat(calendarRequest.from()).isEqualTo(EXTREMES_FROM);
        assertThat(calendarRequest.to()).isEqualTo(BALANCES_TO);
        assertThat(calendarRequest.accountIds()).containsExactly(accountAId, accountBId);
        assertThat(calendarRequest.simulationGroupIds()).containsExactly(simulationGroupAId);

        verifyNoMoreInteractions(financeCalendarService);
    }

    @Test
    void collaboratorShouldBuildReportOnlyForLinkedRequestedAccount() {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID collaboratorId = UUID.randomUUID();

        UUID linkedAccountId = UUID.randomUUID();
        UUID unlinkedAccountId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        givenUserGroup(userGroupId, unique("collaborator report group"));
        givenUser(ownerId, userGroupId, "OWNER", uniqueEmail("collaborator-report-owner"));
        givenUser(collaboratorId, userGroupId, "COLLABORATOR", uniqueEmail("collaborator-report-user"));

        givenCategory(categoryId, userGroupId, ownerId, unique("collaborator report category"));

        givenAccount(linkedAccountId, userGroupId, unique("linked report account"));
        givenAccount(unlinkedAccountId, userGroupId, unique("unlinked report account"));

        givenAccountUser(linkedAccountId, collaboratorId, userGroupId);

        givenRecurringTransaction(
                userGroupId,
                linkedAccountId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                new BigDecimal("-20.00"),
                true,
                "Linked monthly expense"
        );

        givenRecurringTransaction(
                userGroupId,
                unlinkedAccountId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                new BigDecimal("-999.00"),
                true,
                "Unlinked monthly expense"
        );

        when(financeCalendarService.getDailyBalancesForReport(
                eq(collaboratorId),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of(
                dailyBalance(EXTREMES_FROM, "EUR", "100.00", "100.00"),
                dailyBalance(EXTREMES_TO, "EUR", "80.00", "80.00"),
                dailyBalance(LocalDate.of(2026, 12, 31), "EUR", "70.00", "70.00"),
                dailyBalance(LocalDate.of(2027, 12, 31), "EUR", "60.00", "60.00")
        ));

        FinanceReportSummary summary = financeReportService.getReportSummary(
                collaboratorId,
                new FinanceReportSummaryRequest(
                        List.of(linkedAccountId),
                        null
                )
        );

        assertThat(summary.recurringByCurrency())
                .singleElement()
                .satisfies(recurring -> {
                    assertThat(recurring.currency()).isEqualTo("EUR");
                    assertThat(recurring.annualIncome()).isEqualByComparingTo("0.00");
                    assertThat(recurring.annualExpenses()).isEqualByComparingTo("240.00");
                    assertThat(recurring.annualNetBalance()).isEqualByComparingTo("-240.00");
                    assertThat(recurring.averageMonthlyIncome()).isEqualByComparingTo("0.00");
                    assertThat(recurring.averageMonthlyExpenses()).isEqualByComparingTo("20.00");
                    assertThat(recurring.averageMonthlyNetBalance()).isEqualByComparingTo("-20.00");
                });

        ArgumentCaptor<FinanceCalendarSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceCalendarSearchRequest.class);

        verify(financeCalendarService).getDailyBalancesForReport(
                eq(collaboratorId),
                requestCaptor.capture()
        );

        FinanceCalendarSearchRequest calendarRequest = requestCaptor.getValue();

        assertThat(calendarRequest.from()).isEqualTo(EXTREMES_FROM);
        assertThat(calendarRequest.to()).isEqualTo(BALANCES_TO);
        assertThat(calendarRequest.accountIds()).containsExactly(linkedAccountId);
        assertThat(calendarRequest.simulationGroupIds()).isEmpty();

        verifyNoMoreInteractions(financeCalendarService);
    }

    @Test
    void collaboratorShouldNotBuildReportForUnlinkedRequestedAccount() {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID collaboratorId = UUID.randomUUID();

        UUID linkedAccountId = UUID.randomUUID();
        UUID unlinkedAccountId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        givenUserGroup(userGroupId, unique("collaborator denied report group"));
        givenUser(ownerId, userGroupId, "OWNER", uniqueEmail("collaborator-denied-owner"));
        givenUser(collaboratorId, userGroupId, "COLLABORATOR", uniqueEmail("collaborator-denied-user"));

        givenCategory(categoryId, userGroupId, ownerId, unique("collaborator denied category"));

        givenAccount(linkedAccountId, userGroupId, unique("collaborator linked account"));
        givenAccount(unlinkedAccountId, userGroupId, unique("collaborator unlinked account"));

        givenAccountUser(linkedAccountId, collaboratorId, userGroupId);

        assertThatThrownBy(() -> financeReportService.getReportSummary(
                collaboratorId,
                new FinanceReportSummaryRequest(
                        List.of(unlinkedAccountId),
                        null
                )
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("finance.account.notFound");

        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void viewerCollaboratorShouldBuildReportForAllGroupAccountsWithoutAccountFilter() {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        UUID accountAId = UUID.randomUUID();
        UUID accountBId = UUID.randomUUID();

        UUID categoryId = UUID.randomUUID();

        givenUserGroup(userGroupId, unique("viewer report group"));
        givenUser(ownerId, userGroupId, "OWNER", uniqueEmail("viewer-report-owner"));
        givenUser(viewerId, userGroupId, "VIEWER_COLLABORATOR", uniqueEmail("viewer-report-user"));

        givenCategory(categoryId, userGroupId, ownerId, unique("viewer report category"));

        givenAccount(accountAId, userGroupId, unique("viewer report account A"));
        givenAccount(accountBId, userGroupId, unique("viewer report account B"));

        givenRecurringTransaction(
                userGroupId,
                accountAId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                new BigDecimal("-100.00"),
                true,
                "Account A monthly expense"
        );

        givenRecurringTransaction(
                userGroupId,
                accountBId,
                categoryId,
                LocalDate.of(2026, 1, 1),
                false,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                new BigDecimal("250.00"),
                true,
                "Account B monthly income"
        );

        when(financeCalendarService.getDailyBalancesForReport(
                eq(viewerId),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of(
                dailyBalance(EXTREMES_FROM, "EUR", "100.00", "100.00"),
                dailyBalance(TODAY, "EUR", "200.00", "200.00"),
                dailyBalance(EXTREMES_TO, "EUR", "300.00", "300.00"),
                dailyBalance(LocalDate.of(2026, 12, 31), "EUR", "400.00", "400.00"),
                dailyBalance(LocalDate.of(2027, 12, 31), "EUR", "500.00", "500.00")
        ));

        FinanceReportSummary summary = financeReportService.getReportSummary(
                viewerId,
                new FinanceReportSummaryRequest(
                        null,
                        null
                )
        );

        assertThat(summary.recurringByCurrency())
                .singleElement()
                .satisfies(recurring -> {
                    assertThat(recurring.currency()).isEqualTo("EUR");
                    assertThat(recurring.annualIncome()).isEqualByComparingTo("3000.00");
                    assertThat(recurring.annualExpenses()).isEqualByComparingTo("1200.00");
                    assertThat(recurring.annualNetBalance()).isEqualByComparingTo("1800.00");
                    assertThat(recurring.averageMonthlyIncome()).isEqualByComparingTo("250.00");
                    assertThat(recurring.averageMonthlyExpenses()).isEqualByComparingTo("100.00");
                    assertThat(recurring.averageMonthlyNetBalance()).isEqualByComparingTo("150.00");
                });

        ArgumentCaptor<FinanceCalendarSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceCalendarSearchRequest.class);

        verify(financeCalendarService).getDailyBalancesForReport(
                eq(viewerId),
                requestCaptor.capture()
        );

        FinanceCalendarSearchRequest calendarRequest = requestCaptor.getValue();

        assertThat(calendarRequest.from()).isEqualTo(EXTREMES_FROM);
        assertThat(calendarRequest.to()).isEqualTo(BALANCES_TO);
        assertThat(calendarRequest.accountIds()).isEmpty();
        assertThat(calendarRequest.simulationGroupIds()).isEmpty();

        verifyNoMoreInteractions(financeCalendarService);
    }

    private UUID givenRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            LocalDate firstPaymentDate,
            boolean simulated,
            UUID simulationGroupId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            RecurrenceUnit recurrenceUnit,
            short recurrenceInterval,
            BigDecimal paymentAmount,
            boolean affectsSerenityline,
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
                effectiveFrom,
                effectiveTo,
                recurrenceUnit,
                recurrenceInterval,
                paymentAmount
        );

        givenRecurringDetails(
                recurringTransactionId,
                userGroupId,
                accountId,
                categoryId,
                description,
                effectiveFrom,
                true,
                affectsSerenityline
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
            RecurrenceUnit recurrenceUnit,
            short recurrenceInterval,
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
                        VALUES (?, ?, ?, 1, ?, ?, 'NONE', ?)
                        """,
                recurringTransactionId,
                effectiveFrom,
                effectiveTo,
                recurrenceInterval,
                recurrenceUnit.name(),
                paymentAmount
        );
    }

    private void givenRecurringDetails(
            UUID recurringTransactionId,
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            String description,
            LocalDate effectiveFrom,
            boolean affectsAccountBalance,
            boolean affectsSerenityline
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
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId,
                accountId,
                affectsAccountBalance,
                affectsSerenityline,
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
                "Finance Report Service Integration Test User",
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
                """, categoryId, name, "Finance report service integration test category");
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
                            account_user_id,
                            account_id,
                            user_id,
                            user_group_id,
                            account_access_granted_at
                        )
                        VALUES (?, ?, ?, ?, now())
                        """,
                UUID.randomUUID(),
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