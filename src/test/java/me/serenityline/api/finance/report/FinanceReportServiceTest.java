package me.serenityline.api.finance.report;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.calendar.*;
import me.serenityline.api.finance.common.FinanceProperties;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import me.serenityline.api.finance.transaction.service.RecurringTransactionAccessService;
import me.serenityline.api.finance.transaction.service.TransactionAccessService;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceReportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private static final UUID CURRENT_USER_ID = UUID.randomUUID();
    private static final UUID USER_GROUP_ID = UUID.randomUUID();

    @Mock
    private FinanceCalendarService financeCalendarService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RecurringTransactionRepository recurringTransactionRepository;

    @Mock
    private RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;

    @Mock
    private RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;

    @Mock
    private SimulationGroupRepository simulationGroupRepository;

    @Mock
    private TransactionAccessService transactionAccessService;

    @Mock
    private RecurringTransactionAccessService recurringTransactionAccessService;

    @Mock
    private FinanceCalendarProperties financeCalendarProperties;

    @Mock
    private FinanceProperties financeProperties;

    @Mock
    private FinanceReportProperties financeReportProperties;

    private FinanceReportService service;

    private User currentUser;

    private static FinanceReportSummaryRequest request(
            List<UUID> accountIds,
            List<UUID> simulationGroupIds
    ) {
        return new FinanceReportSummaryRequest(
                accountIds,
                simulationGroupIds
        );
    }

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

    private static RecurringTransaction recurringTransaction(
            UUID recurringTransactionId,
            LocalDate firstPaymentDate
    ) {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);

        lenient().when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(recurringTransactionId);
        lenient().when(recurringTransaction.getRecurringTransactionFirstPaymentDate())
                .thenReturn(firstPaymentDate);

        return recurringTransaction;
    }

    private static RecurringTransactionHistory rule(
            RecurringTransaction recurringTransaction,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            RecurrenceUnit recurrenceUnit,
            short recurrenceInterval,
            String paymentAmount,
            LocalDate recurringTransactionEndDate
    ) {
        RecurringTransactionHistory rule =
                mock(RecurringTransactionHistory.class);

        lenient().when(rule.getRecurringTransaction())
                .thenReturn(recurringTransaction);
        lenient().when(rule.getEffectiveFrom())
                .thenReturn(effectiveFrom);
        lenient().when(rule.getEffectiveTo())
                .thenReturn(effectiveTo);
        lenient().when(rule.getRecurrenceUnit())
                .thenReturn(recurrenceUnit);
        lenient().when(rule.getRecurrenceInterval())
                .thenReturn(recurrenceInterval);
        lenient().when(rule.getPaymentAmount())
                .thenReturn(new BigDecimal(paymentAmount));
        lenient().when(rule.getRecurringTransactionEndDate())
                .thenReturn(recurringTransactionEndDate);
        lenient().when(rule.getRecurringTransactionHistoryCreatedAt())
                .thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        lenient().when(rule.getRecurringTransactionHistoryId())
                .thenReturn(UUID.randomUUID());

        return rule;
    }

    private static RecurringTransactionDetailsHistory details(
            RecurringTransaction recurringTransaction,
            LocalDate effectiveFrom,
            boolean affectsSerenityline,
            String currency
    ) {
        Account account = mock(Account.class);
        lenient().when(account.getCurrency())
                .thenReturn(currency);

        RecurringTransactionDetailsHistory details =
                mock(RecurringTransactionDetailsHistory.class);

        lenient().when(details.getRecurringTransaction())
                .thenReturn(recurringTransaction);
        lenient().when(details.getRecurringTransactionDetailsEffectiveFrom())
                .thenReturn(effectiveFrom);
        lenient().when(details.isRecurringTransactionAffectsSerenityline())
                .thenReturn(affectsSerenityline);
        lenient().when(details.getLinkedAccount())
                .thenReturn(account);
        lenient().when(details.getRecurringTransactionDetailsHistoryCreatedAt())
                .thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        lenient().when(details.getRecurringTransactionDetailsHistoryId())
                .thenReturn(UUID.randomUUID());

        return details;
    }

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                TODAY.atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );

        lenient().when(financeCalendarProperties.getMaxAccountIds())
                .thenReturn(50);

        lenient().when(financeProperties.getMaxSimulationGroupIds())
                .thenReturn(50);

        lenient().when(financeReportProperties.getExtremesPastMonths())
                .thenReturn(36);

        lenient().when(financeReportProperties.getExtremesFutureMonths())
                .thenReturn(36);

        lenient().when(financeReportProperties.getYearEndForecastYears())
                .thenReturn(10);

        lenient().when(financeReportProperties.getTrendMinDays())
                .thenReturn(90);

        lenient().when(financeCalendarService.getDailyBalancesForReport(
                any(UUID.class),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of());

        stubEmptyRecurringRepositoryResults();

        service = new FinanceReportService(
                financeCalendarService,
                userRepository,
                recurringTransactionRepository,
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                simulationGroupRepository,
                transactionAccessService,
                recurringTransactionAccessService,
                financeCalendarProperties,
                financeProperties,
                financeReportProperties,
                fixedClock
        );
    }

    @Test
    void getReportSummaryShouldThrowWhenCurrentUserDoesNotExist() {
        when(userRepository.findById(CURRENT_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("user.notFound");

        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void getReportSummaryShouldRejectNullAccountId() {
        assertThatThrownBy(() -> service.getReportSummary(
                CURRENT_USER_ID,
                request(Arrays.asList(UUID.randomUUID(), null), null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.account.idRequired");

        verifyNoInteractions(userRepository);
        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void getReportSummaryShouldRejectTooManyAccountIds() {
        when(financeCalendarProperties.getMaxAccountIds())
                .thenReturn(2);

        assertThatThrownBy(() -> service.getReportSummary(
                CURRENT_USER_ID,
                request(
                        List.of(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID()
                        ),
                        null
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.accountIdsTooMany");

        verifyNoInteractions(userRepository);
        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void getReportSummaryShouldRejectNullSimulationGroupId() {
        assertThatThrownBy(() -> service.getReportSummary(
                CURRENT_USER_ID,
                request(null, Arrays.asList(UUID.randomUUID(), null))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.simulationGroup.idRequired");

        verifyNoInteractions(userRepository);
        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void getReportSummaryShouldRejectUnreadableSimulationGroup() {
        givenCurrentUser(true, true);

        UUID simulationGroupA = UUID.randomUUID();
        UUID simulationGroupB = UUID.randomUUID();

        when(simulationGroupRepository.findActiveIdsByUserGroupId(
                List.of(simulationGroupA, simulationGroupB),
                USER_GROUP_ID
        )).thenReturn(List.of(simulationGroupA));

        assertThatThrownBy(() -> service.getReportSummary(
                CURRENT_USER_ID,
                request(null, List.of(simulationGroupA, simulationGroupB))
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("finance.simulationGroup.notFound");

        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void getReportSummaryShouldCallCalendarOnceFromExtremesFromToLastForecastYearEnd() {
        givenCurrentUser(true, true);

        service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        ArgumentCaptor<FinanceCalendarSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceCalendarSearchRequest.class);

        verify(financeCalendarService, times(1))
                .getDailyBalancesForReport(
                        eq(CURRENT_USER_ID),
                        requestCaptor.capture()
                );

        FinanceCalendarSearchRequest captured = requestCaptor.getValue();

        assertThat(captured.from()).isEqualTo(LocalDate.of(2023, 6, 15));
        assertThat(captured.to()).isEqualTo(LocalDate.of(2036, 12, 31));
        assertThat(captured.accountIds()).isEmpty();
        assertThat(captured.simulationGroupIds()).isEmpty();
    }

    @Test
    void getReportSummaryShouldUseExtremesToWhenAfterLastForecastYearEnd() {
        givenCurrentUser(true, true);

        when(financeReportProperties.getExtremesFutureMonths())
                .thenReturn(180);

        service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        ArgumentCaptor<FinanceCalendarSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceCalendarSearchRequest.class);

        verify(financeCalendarService)
                .getDailyBalancesForReport(
                        eq(CURRENT_USER_ID),
                        requestCaptor.capture()
                );

        assertThat(requestCaptor.getValue().to())
                .isEqualTo(TODAY.plusMonths(180));
    }

    @Test
    void getReportSummaryShouldUseWithoutAccountFilterRepositoryWhenAccountIdsAreMissing() {
        givenCurrentUser(true, true);

        service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        verify(recurringTransactionRepository)
                .findReportReadableBaseByUserGroup(
                        eq(USER_GROUP_ID),
                        isNull(),
                        eq(TODAY)
                );

        verify(recurringTransactionRepository, never())
                .findReportReadableBaseByUserGroupForAccounts(
                        any(UUID.class),
                        anyCollection(),
                        any(LocalDate.class)
                );
    }

    @Test
    void getReportSummaryShouldUseForAccountsRepositoryWhenAccountIdsAreProvided() {
        givenCurrentUser(true, true);

        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        service.getReportSummary(
                CURRENT_USER_ID,
                request(List.of(accountA, accountA, accountB), null)
        );

        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(currentUser, USER_GROUP_ID, accountA);

        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(currentUser, USER_GROUP_ID, accountB);

        verify(recurringTransactionRepository)
                .findReportReadableBaseByUserGroupForAccounts(
                        eq(USER_GROUP_ID),
                        eq(List.of(accountA, accountB)),
                        eq(TODAY)
                );

        verify(recurringTransactionRepository, never())
                .findReportReadableBaseByUserGroup(
                        any(UUID.class),
                        nullable(UUID.class),
                        any(LocalDate.class)
                );
    }

    @Test
    void getReportSummaryShouldUseLinkedBaseAndSimulatedForAccountsRepository() {
        givenCurrentUser(false, false);

        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        when(simulationGroupRepository.findActiveIdsReadableByLinkedUser(
                List.of(simulationGroupId),
                USER_GROUP_ID,
                CURRENT_USER_ID
        )).thenReturn(List.of(simulationGroupId));

        service.getReportSummary(
                CURRENT_USER_ID,
                request(
                        List.of(accountA, accountB),
                        List.of(simulationGroupId)
                )
        );

        verify(recurringTransactionRepository)
                .findReportReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
                        eq(USER_GROUP_ID),
                        eq(CURRENT_USER_ID),
                        eq(List.of(accountA, accountB)),
                        eq(List.of(simulationGroupId)),
                        eq(TODAY)
                );
    }

    @Test
    void getReportSummaryShouldCalculateYearEndForecastsFromDailyBalanceRange() {
        givenCurrentUser(true, true);

        when(financeReportProperties.getYearEndForecastYears())
                .thenReturn(1);

        when(financeCalendarService.getDailyBalancesForReport(
                eq(CURRENT_USER_ID),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of(
                dailyBalance(
                        LocalDate.of(2026, 12, 31),
                        "EUR",
                        "1000.00",
                        "900.00"
                ),
                dailyBalance(
                        LocalDate.of(2027, 12, 31),
                        "EUR",
                        "2000.00",
                        "1800.00"
                )
        ));

        FinanceReportSummary summary = service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        assertThat(summary.yearEndForecasts()).hasSize(2);

        assertThat(summary.yearEndForecasts().get(0).year()).isEqualTo(2026);
        assertThat(summary.yearEndForecasts().get(0).date())
                .isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(summary.yearEndForecasts().get(0).balancesByCurrency())
                .hasSize(1);
        assertThat(summary.yearEndForecasts().get(0).balancesByCurrency().get(0).currency())
                .isEqualTo("EUR");
        assertThat(summary.yearEndForecasts().get(0).balancesByCurrency().get(0).endOfYearAccountBalance())
                .isEqualByComparingTo("1000.00");
        assertThat(summary.yearEndForecasts().get(0).balancesByCurrency().get(0).endOfYearSerenityline())
                .isEqualByComparingTo("900.00");

        assertThat(summary.yearEndForecasts().get(1).year()).isEqualTo(2027);
        assertThat(summary.yearEndForecasts().get(1).balancesByCurrency().get(0).endOfYearAccountBalance())
                .isEqualByComparingTo("2000.00");
        assertThat(summary.yearEndForecasts().get(1).balancesByCurrency().get(0).endOfYearSerenityline())
                .isEqualByComparingTo("1800.00");
    }

    @Test
    void getReportSummaryShouldCalculateExtremesOnlyInsideConfiguredExtremesRange() {
        givenCurrentUser(true, true);

        LocalDate insideRange = TODAY.plusDays(1);
        LocalDate outsideExtremesRange = LocalDate.of(2030, 12, 31);

        when(financeCalendarService.getDailyBalancesForReport(
                eq(CURRENT_USER_ID),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of(
                dailyBalance(
                        insideRange,
                        "EUR",
                        "100.00",
                        "100.00"
                ),
                dailyBalance(
                        outsideExtremesRange,
                        "EUR",
                        "-999999.00",
                        "-999999.00"
                )
        ));

        FinanceReportSummary summary = service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        assertThat(summary.extremesByCurrency()).hasSize(1);

        FinanceReportExtremesByCurrency eur = summary.extremesByCurrency().get(0);

        assertThat(eur.currency()).isEqualTo("EUR");
        assertThat(eur.minAccountBalance().date()).isEqualTo(insideRange);
        assertThat(eur.minAccountBalance().value()).isEqualByComparingTo("100.00");
        assertThat(eur.minSerenityline().date()).isEqualTo(insideRange);
        assertThat(eur.minSerenityline().value()).isEqualByComparingTo("100.00");
    }

    @Test
    void getReportSummaryShouldClassifyMonotonicDownTrendAtRangeEndForMinimum() {
        givenCurrentUser(true, true);

        when(financeReportProperties.getTrendMinDays())
                .thenReturn(3);

        LocalDate rangeTo = TODAY.plusMonths(36);

        when(financeCalendarService.getDailyBalancesForReport(
                eq(CURRENT_USER_ID),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of(
                dailyBalance(
                        rangeTo.minusDays(2),
                        "EUR",
                        "100.00",
                        "100.00"
                ),
                dailyBalance(
                        rangeTo.minusDays(1),
                        "EUR",
                        "90.00",
                        "90.00"
                ),
                dailyBalance(
                        rangeTo,
                        "EUR",
                        "80.00",
                        "80.00"
                )
        ));

        FinanceReportSummary summary = service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        FinanceReportPoint minSerenityline =
                summary.extremesByCurrency().get(0).minSerenityline();

        assertThat(minSerenityline.date()).isEqualTo(rangeTo);
        assertThat(minSerenityline.value()).isEqualByComparingTo("80.00");
        assertThat(minSerenityline.classification())
                .isEqualTo(FinanceReportExtremeClassification.MONOTONIC_TREND_WITHIN_HORIZON);
        assertThat(minSerenityline.trend()).isNotNull();
        assertThat(minSerenityline.trend().direction())
                .isEqualTo(FinanceReportTrendDirection.DOWN);
        assertThat(minSerenityline.trend().monotonicUntilRangeEnd())
                .isTrue();
    }

    @Test
    void getReportSummaryShouldCalculateAnnualAndMonthlyRecurringMetrics() {
        givenCurrentUser(true, true);

        UUID monthlyIncomeId = UUID.randomUUID();
        UUID yearlyExpenseId = UUID.randomUUID();
        UUID quarterlyExpenseId = UUID.randomUUID();
        UUID weeklyExpenseId = UUID.randomUUID();

        RecurringTransaction monthlyIncome = recurringTransaction(
                monthlyIncomeId,
                LocalDate.of(2026, 1, 1)
        );
        RecurringTransaction yearlyExpense = recurringTransaction(
                yearlyExpenseId,
                LocalDate.of(2026, 1, 1)
        );
        RecurringTransaction quarterlyExpense = recurringTransaction(
                quarterlyExpenseId,
                LocalDate.of(2026, 1, 1)
        );
        RecurringTransaction weeklyExpense = recurringTransaction(
                weeklyExpenseId,
                LocalDate.of(2026, 1, 1)
        );

        when(recurringTransactionRepository.findReportReadableBaseByUserGroup(
                USER_GROUP_ID,
                null,
                TODAY
        )).thenReturn(List.of(
                monthlyIncome,
                yearlyExpense,
                quarterlyExpense,
                weeklyExpense
        ));

        RecurringTransactionHistory monthlyIncomeRule = rule(
                monthlyIncome,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                "100.00",
                null
        );

        RecurringTransactionHistory yearlyExpenseRule = rule(
                yearlyExpense,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.YEAR,
                (short) 1,
                "-600.00",
                null
        );

        RecurringTransactionHistory quarterlyExpenseRule = rule(
                quarterlyExpense,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 3,
                "-90.00",
                null
        );

        RecurringTransactionHistory weeklyExpenseRule = rule(
                weeklyExpense,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.WEEK,
                (short) 1,
                "-20.00",
                null
        );

        RecurringTransactionDetailsHistory monthlyIncomeDetails = details(
                monthlyIncome,
                LocalDate.of(2026, 1, 1),
                true,
                "EUR"
        );

        RecurringTransactionDetailsHistory yearlyExpenseDetails = details(
                yearlyExpense,
                LocalDate.of(2026, 1, 1),
                true,
                "EUR"
        );

        RecurringTransactionDetailsHistory quarterlyExpenseDetails = details(
                quarterlyExpense,
                LocalDate.of(2026, 1, 1),
                true,
                "EUR"
        );

        RecurringTransactionDetailsHistory weeklyExpenseDetails = details(
                weeklyExpense,
                LocalDate.of(2026, 1, 1),
                true,
                "EUR"
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(
                monthlyIncomeRule,
                yearlyExpenseRule,
                quarterlyExpenseRule,
                weeklyExpenseRule
        ));

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(
                monthlyIncomeDetails,
                yearlyExpenseDetails,
                quarterlyExpenseDetails,
                weeklyExpenseDetails
        ));

        FinanceReportSummary summary = service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        assertThat(summary.recurringByCurrency()).hasSize(1);

        FinanceRecurringReportSummary eur = summary.recurringByCurrency().get(0);

        assertThat(eur.currency()).isEqualTo("EUR");
        assertThat(eur.annualIncome()).isEqualByComparingTo("1200.00");
        assertThat(eur.annualExpenses()).isEqualByComparingTo("2000.00");
        assertThat(eur.annualNetBalance()).isEqualByComparingTo("-800.00");
        assertThat(eur.averageMonthlyIncome()).isEqualByComparingTo("100.00");
        assertThat(eur.averageMonthlyExpenses()).isEqualByComparingTo("166.67");
        assertThat(eur.averageMonthlyNetBalance()).isEqualByComparingTo("-66.67");
    }

    @Test
    void getReportSummaryShouldExcludeRecurringThatDoesNotAffectSerenityline() {
        givenCurrentUser(true, true);

        UUID recurringTransactionId = UUID.randomUUID();

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 1, 1)
        );

        when(recurringTransactionRepository.findReportReadableBaseByUserGroup(
                USER_GROUP_ID,
                null,
                TODAY
        )).thenReturn(List.of(recurringTransaction));

        RecurringTransactionHistory recurringRule = rule(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                "100.00",
                null
        );

        RecurringTransactionDetailsHistory recurringDetails = details(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                false,
                "EUR"
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringRule));

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringDetails));

        FinanceReportSummary summary = service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        assertThat(summary.recurringByCurrency()).isEmpty();
    }

    @Test
    void getReportSummaryShouldIgnoreFutureRuleChangeForAlreadyActiveRecurring() {
        givenCurrentUser(true, true);

        UUID recurringTransactionId = UUID.randomUUID();

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 1, 1)
        );

        when(recurringTransactionRepository.findReportReadableBaseByUserGroup(
                USER_GROUP_ID,
                null,
                TODAY
        )).thenReturn(List.of(recurringTransaction));

        RecurringTransactionHistory currentRule = rule(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 9, 1),
                RecurrenceUnit.MONTH,
                (short) 1,
                "100.00",
                null
        );

        RecurringTransactionHistory futureRule = rule(
                recurringTransaction,
                LocalDate.of(2026, 9, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                "999.00",
                null
        );

        RecurringTransactionDetailsHistory currentDetails = details(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                true,
                "EUR"
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(
                currentRule,
                futureRule
        ));

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(currentDetails));

        FinanceReportSummary summary = service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        assertThat(summary.recurringByCurrency()).hasSize(1);
        assertThat(summary.recurringByCurrency().get(0).annualIncome())
                .isEqualByComparingTo("1200.00");
    }

    @Test
    void getReportSummaryShouldUseFirstPaymentDateAsTargetDateForFutureRecurring() {
        givenCurrentUser(true, true);

        UUID recurringTransactionId = UUID.randomUUID();

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 9, 1)
        );

        when(recurringTransactionRepository.findReportReadableBaseByUserGroup(
                USER_GROUP_ID,
                null,
                TODAY
        )).thenReturn(List.of(recurringTransaction));

        RecurringTransactionHistory recurringRule = rule(
                recurringTransaction,
                LocalDate.of(2026, 9, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                "100.00",
                null
        );

        RecurringTransactionDetailsHistory recurringDetails = details(
                recurringTransaction,
                LocalDate.of(2026, 9, 1),
                true,
                "EUR"
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringRule));

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringDetails));

        FinanceReportSummary summary = service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        assertThat(summary.recurringByCurrency()).hasSize(1);
        assertThat(summary.recurringByCurrency().get(0).annualIncome())
                .isEqualByComparingTo("1200.00");
    }

    @Test
    void getReportSummaryShouldSkipFutureRecurringEndedBeforeTargetDate() {
        givenCurrentUser(true, true);

        UUID recurringTransactionId = UUID.randomUUID();

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 9, 1)
        );

        when(recurringTransactionRepository.findReportReadableBaseByUserGroup(
                USER_GROUP_ID,
                null,
                TODAY
        )).thenReturn(List.of(recurringTransaction));

        RecurringTransactionHistory recurringRule = rule(
                recurringTransaction,
                LocalDate.of(2026, 6, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 1,
                "100.00",
                LocalDate.of(2026, 8, 31)
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringRule));

        RecurringTransactionDetailsHistory recurringDetails = details(
                recurringTransaction,
                LocalDate.of(2026, 6, 1),
                true,
                "EUR"
        );

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringDetails));

        FinanceReportSummary summary = service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        );

        assertThat(summary.recurringByCurrency()).isEmpty();
    }

    @Test
    void getReportSummaryShouldThrowWhenEligibleRecurringHasNoRule() {
        givenCurrentUser(true, true);

        RecurringTransaction recurringTransaction = recurringTransaction(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1)
        );

        when(recurringTransactionRepository.findReportReadableBaseByUserGroup(
                USER_GROUP_ID,
                null,
                TODAY
        )).thenReturn(List.of(recurringTransaction));

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of());

        RecurringTransactionDetailsHistory recurringDetails = details(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                true,
                "EUR"
        );

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringDetails));

        assertThatThrownBy(() -> service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.report.recurringRuleMissing");
    }

    @Test
    void getReportSummaryShouldThrowWhenRecurrenceIntervalIsZero() {
        givenCurrentUser(true, true);

        RecurringTransaction recurringTransaction = recurringTransaction(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1)
        );

        when(recurringTransactionRepository.findReportReadableBaseByUserGroup(
                USER_GROUP_ID,
                null,
                TODAY
        )).thenReturn(List.of(recurringTransaction));

        RecurringTransactionHistory recurringRule = rule(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                null,
                RecurrenceUnit.MONTH,
                (short) 0,
                "100.00",
                null
        );

        RecurringTransactionDetailsHistory recurringDetails = details(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                true,
                "EUR"
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringRule));

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                eq(USER_GROUP_ID)
        )).thenReturn(List.of(recurringDetails));
        assertThatThrownBy(() -> service.getReportSummary(
                CURRENT_USER_ID,
                request(null, null)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.report.recurrenceIntervalInvalid");
    }

    private void givenCurrentUser(
            boolean canReadAllGroupTransactions,
            boolean canReadAllGroupRecurringTransactions
    ) {
        UserGroup userGroup = mock(UserGroup.class);

        lenient().when(userGroup.getUserGroupId())
                .thenReturn(USER_GROUP_ID);

        currentUser = mock(User.class);

        lenient().when(currentUser.getUserId())
                .thenReturn(CURRENT_USER_ID);

        lenient().when(currentUser.getUserGroup())
                .thenReturn(userGroup);

        when(userRepository.findById(CURRENT_USER_ID))
                .thenReturn(Optional.of(currentUser));

        when(transactionAccessService.canReadAllGroupTransactions(currentUser))
                .thenReturn(canReadAllGroupTransactions);

        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(currentUser))
                .thenReturn(canReadAllGroupRecurringTransactions);
    }

    private void stubEmptyRecurringRepositoryResults() {
        lenient().when(recurringTransactionRepository.findReportReadableBaseByUserGroup(
                any(UUID.class),
                nullable(UUID.class),
                any(LocalDate.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionRepository.findReportReadableBaseAndSimulatedByUserGroup(
                any(UUID.class),
                nullable(UUID.class),
                anyCollection(),
                any(LocalDate.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionRepository.findReportReadableBaseByLinkedUserAccess(
                any(UUID.class),
                any(UUID.class),
                nullable(UUID.class),
                any(LocalDate.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionRepository.findReportReadableBaseAndSimulatedByLinkedUserAccess(
                any(UUID.class),
                any(UUID.class),
                nullable(UUID.class),
                anyCollection(),
                any(LocalDate.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionRepository.findReportReadableBaseByUserGroupForAccounts(
                any(UUID.class),
                anyCollection(),
                any(LocalDate.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionRepository.findReportReadableBaseAndSimulatedByUserGroupForAccounts(
                any(UUID.class),
                anyCollection(),
                anyCollection(),
                any(LocalDate.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionRepository.findReportReadableBaseByLinkedUserAccessForAccounts(
                any(UUID.class),
                any(UUID.class),
                anyCollection(),
                any(LocalDate.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionRepository.findReportReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
                any(UUID.class),
                any(UUID.class),
                anyCollection(),
                anyCollection(),
                any(LocalDate.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                any(UUID.class)
        )).thenReturn(List.of());

        lenient().when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                anyCollection(),
                any(UUID.class)
        )).thenReturn(List.of());
    }
}