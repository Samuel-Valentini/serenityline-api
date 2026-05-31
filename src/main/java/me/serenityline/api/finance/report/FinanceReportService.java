package me.serenityline.api.finance.report;

import me.serenityline.api.common.error.ResourceNotFoundException;
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
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinanceReportService {

    // Standard annualization factors for recurring report metrics.
    // These values intentionally represent a stable yearly rhythm,
    // not the exact number of occurrences in a specific calendar year.

    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal WEEKS_IN_YEAR = BigDecimal.valueOf(52);

    private final FinanceCalendarService financeCalendarService;
    private final UserRepository userRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final TransactionAccessService transactionAccessService;
    private final RecurringTransactionAccessService recurringTransactionAccessService;
    private final FinanceCalendarProperties financeCalendarProperties;
    private final FinanceProperties financeProperties;
    private final FinanceReportProperties financeReportProperties;
    private final Clock clock;

    public FinanceReportService(
            FinanceCalendarService financeCalendarService,
            UserRepository userRepository,
            RecurringTransactionRepository recurringTransactionRepository,
            RecurringTransactionHistoryRepository recurringTransactionHistoryRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository,
            SimulationGroupRepository simulationGroupRepository,
            TransactionAccessService transactionAccessService,
            RecurringTransactionAccessService recurringTransactionAccessService,
            FinanceCalendarProperties financeCalendarProperties,
            FinanceProperties financeProperties,
            FinanceReportProperties financeReportProperties,
            Clock clock
    ) {
        this.financeCalendarService = Objects.requireNonNull(financeCalendarService, "financeCalendarService");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.recurringTransactionRepository = Objects.requireNonNull(recurringTransactionRepository, "recurringTransactionRepository");
        this.recurringTransactionHistoryRepository = Objects.requireNonNull(recurringTransactionHistoryRepository, "recurringTransactionHistoryRepository");
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(recurringTransactionDetailsHistoryRepository, "recurringTransactionDetailsHistoryRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.transactionAccessService = Objects.requireNonNull(transactionAccessService, "transactionAccessService");
        this.recurringTransactionAccessService = Objects.requireNonNull(recurringTransactionAccessService, "recurringTransactionAccessService");
        this.financeCalendarProperties = Objects.requireNonNull(financeCalendarProperties, "financeCalendarProperties");
        this.financeProperties = Objects.requireNonNull(financeProperties, "financeProperties");
        this.financeReportProperties = Objects.requireNonNull(financeReportProperties, "financeReportProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public FinanceReportSummary getReportSummary(
            UUID currentUserId,
            FinanceReportSummaryRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(request, "request");

        LocalDate asOfDate = LocalDate.now(clock);

        ReportRequestContext context = buildReportRequestContext(
                currentUserId,
                request.accountIds(),
                request.simulationGroupIds()
        );

        LocalDate extremesFrom = asOfDate.minusMonths(
                financeReportProperties.getExtremesPastMonths()
        );

        LocalDate extremesTo = asOfDate.plusMonths(
                financeReportProperties.getExtremesFutureMonths()
        );

        int yearEndForecastYears = financeReportProperties.getYearEndForecastYears();

        LocalDate lastYearEndDate = LocalDate.of(
                asOfDate.getYear() + yearEndForecastYears,
                12,
                31
        );

        LocalDate balancesTo = lastYearEndDate.isAfter(extremesTo)
                ? lastYearEndDate
                : extremesTo;

        List<FinanceCalendarDailyBalance> dailyBalances =
                financeCalendarService.getDailyBalancesForReport(
                        currentUserId,
                        new FinanceCalendarSearchRequest(
                                extremesFrom,
                                balancesTo,
                                context.accountIds(),
                                context.simulationGroupIds()
                        )
                );

        List<FinanceCalendarDailyBalance> extremesDailyBalances =
                dailyBalances.stream()
                        .filter(dailyBalance -> !dailyBalance.date().isBefore(extremesFrom))
                        .filter(dailyBalance -> !dailyBalance.date().isAfter(extremesTo))
                        .toList();

        return new FinanceReportSummary(
                asOfDate,
                FinanceReportProjectionMode.PROJECTED_PLANNING,
                new FinanceReportRange(extremesFrom, extremesTo),
                yearEndForecastYears,
                calculateRecurringByCurrency(context, asOfDate),
                calculateExtremesByCurrency(
                        extremesDailyBalances,
                        asOfDate,
                        extremesFrom,
                        extremesTo
                ),
                calculateYearEndForecasts(
                        dailyBalances,
                        asOfDate.getYear(),
                        yearEndForecastYears
                )
        );
    }

    private ReportRequestContext buildReportRequestContext(
            UUID currentUserId,
            List<UUID> rawAccountIds,
            List<UUID> rawSimulationGroupIds
    ) {
        List<UUID> accountIds = normalizeAccountIds(rawAccountIds);
        List<UUID> simulationGroupIds = normalizeSimulationGroupIds(rawSimulationGroupIds);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        boolean canReadAllGroupTransactions =
                transactionAccessService.canReadAllGroupTransactions(currentUser);

        boolean canReadAllGroupRecurringTransactions =
                recurringTransactionAccessService.canReadAllGroupRecurringTransactions(currentUser);

        assertReadableAccountFilters(
                currentUser,
                userGroupId,
                accountIds
        );

        assertReadableSimulationGroups(
                currentUser,
                userGroupId,
                simulationGroupIds,
                canReadAllGroupTransactions
        );

        return new ReportRequestContext(
                currentUser,
                userGroupId,
                accountIds,
                simulationGroupIds,
                canReadAllGroupRecurringTransactions
        );
    }

    private List<UUID> normalizeAccountIds(List<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();

        for (UUID accountId : accountIds) {
            if (accountId == null) {
                throw new IllegalArgumentException("finance.account.idRequired");
            }

            normalized.add(accountId);
        }

        if (normalized.size() > financeCalendarProperties.getMaxAccountIds()) {
            throw new IllegalArgumentException("finance.calendar.accountIdsTooMany");
        }

        return List.copyOf(normalized);
    }

    private List<UUID> normalizeSimulationGroupIds(List<UUID> simulationGroupIds) {
        if (simulationGroupIds == null || simulationGroupIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();

        for (UUID simulationGroupId : simulationGroupIds) {
            if (simulationGroupId == null) {
                throw new IllegalArgumentException("finance.simulationGroup.idRequired");
            }

            normalized.add(simulationGroupId);
        }

        if (normalized.size() > financeProperties.getMaxSimulationGroupIds()) {
            throw new IllegalArgumentException("finance.simulationGroup.idsTooMany");
        }

        return List.copyOf(normalized);
    }

    private void assertReadableAccountFilters(
            User currentUser,
            UUID userGroupId,
            List<UUID> accountIds
    ) {
        if (accountIds.isEmpty()) {
            recurringTransactionAccessService.assertReadableAccountFilter(
                    currentUser,
                    userGroupId,
                    null
            );
            return;
        }

        for (UUID accountId : accountIds) {
            recurringTransactionAccessService.assertReadableAccountFilter(
                    currentUser,
                    userGroupId,
                    accountId
            );
        }
    }

    private void assertReadableSimulationGroups(
            User currentUser,
            UUID userGroupId,
            List<UUID> simulationGroupIds,
            boolean canReadAllGroupSimulationGroups
    ) {
        if (simulationGroupIds.isEmpty()) {
            return;
        }

        List<UUID> foundIds;

        if (canReadAllGroupSimulationGroups) {
            foundIds = simulationGroupRepository.findActiveIdsByUserGroupId(
                    simulationGroupIds,
                    userGroupId
            );
        } else {
            foundIds = simulationGroupRepository.findActiveIdsReadableByLinkedUser(
                    simulationGroupIds,
                    userGroupId,
                    currentUser.getUserId()
            );
        }

        if (!new HashSet<>(foundIds).containsAll(simulationGroupIds)) {
            throw new ResourceNotFoundException("finance.simulationGroup.notFound");
        }
    }

    private List<FinanceRecurringReportSummary> calculateRecurringByCurrency(
            ReportRequestContext context,
            LocalDate asOfDate
    ) {
        List<RecurringTransaction> recurringTransactions =
                findReadableRecurringTransactions(
                        context,
                        asOfDate
                );

        if (recurringTransactions.isEmpty()) {
            return List.of();
        }

        Set<UUID> recurringTransactionIds =
                recurringTransactions.stream()
                        .map(RecurringTransaction::getRecurringTransactionId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, LocalDate> targetDateByRecurringTransactionId =
                targetDateByRecurringTransactionId(
                        recurringTransactions,
                        asOfDate
                );

        Map<UUID, RecurringTransactionHistory> currentRuleByRecurringTransactionId =
                currentRuleByRecurringTransactionId(
                        recurringTransactionIds,
                        context.userGroupId(),
                        targetDateByRecurringTransactionId
                );

        Map<UUID, RecurringTransactionDetailsHistory> currentDetailsByRecurringTransactionId =
                currentDetailsByRecurringTransactionId(
                        recurringTransactionIds,
                        context.userGroupId(),
                        targetDateByRecurringTransactionId
                );

        Map<String, RecurringCurrencyAccumulator> totalsByCurrency = new TreeMap<>();

        for (RecurringTransaction recurringTransaction : recurringTransactions) {
            UUID recurringTransactionId = recurringTransaction.getRecurringTransactionId();

            LocalDate targetDate =
                    targetDateByRecurringTransactionId.get(recurringTransactionId);

            if (targetDate == null) {
                throw new IllegalStateException("finance.report.recurringTargetDateMissing");
            }

            RecurringTransactionHistory rule =
                    currentRuleByRecurringTransactionId.get(recurringTransactionId);

            RecurringTransactionDetailsHistory details =
                    currentDetailsByRecurringTransactionId.get(recurringTransactionId);

            if (rule == null) {
                throw new IllegalStateException("finance.report.recurringRuleMissing");
            }

            if (details == null) {
                throw new IllegalStateException("finance.report.recurringDetailsMissing");
            }

            if (rule.getRecurringTransactionEndDate() != null
                    && rule.getRecurringTransactionEndDate().isBefore(targetDate)) {
                continue;
            }

            if (!details.isRecurringTransactionAffectsSerenityline()) {
                continue;
            }

            String currency = details.getLinkedAccount().getCurrency();
            BigDecimal annualAmount = annualize(rule);

            RecurringCurrencyAccumulator accumulator =
                    totalsByCurrency.computeIfAbsent(
                            currency,
                            ignored -> new RecurringCurrencyAccumulator()
                    );

            if (annualAmount.compareTo(BigDecimal.ZERO) >= 0) {
                accumulator.annualIncome = accumulator.annualIncome.add(annualAmount);
            } else {
                accumulator.annualExpenses = accumulator.annualExpenses.add(annualAmount.abs());
            }
        }

        return totalsByCurrency.entrySet()
                .stream()
                .map(entry -> toRecurringReportSummary(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    private Map<UUID, LocalDate> targetDateByRecurringTransactionId(
            List<RecurringTransaction> recurringTransactions,
            LocalDate asOfDate
    ) {
        Map<UUID, LocalDate> result = new LinkedHashMap<>();

        for (RecurringTransaction recurringTransaction : recurringTransactions) {
            LocalDate firstPaymentDate =
                    recurringTransaction.getRecurringTransactionFirstPaymentDate();

            LocalDate targetDate = firstPaymentDate.isAfter(asOfDate)
                    ? firstPaymentDate
                    : asOfDate;

            result.put(
                    recurringTransaction.getRecurringTransactionId(),
                    targetDate
            );
        }

        return result;
    }

    private List<RecurringTransaction> findReadableRecurringTransactions(
            ReportRequestContext context,
            LocalDate asOfDate
    ) {
        if (context.accountIds().isEmpty()) {
            return findReportReadableRecurringTransactionsWithoutAccountFilter(
                    context,
                    asOfDate
            );
        }

        return findReportReadableRecurringTransactionsForAccounts(
                context,
                asOfDate
        );
    }

    private List<RecurringTransaction> findReportReadableRecurringTransactionsWithoutAccountFilter(
            ReportRequestContext context,
            LocalDate asOfDate
    ) {
        if (context.canReadAllGroupRecurringTransactions()) {
            if (context.simulationGroupIds().isEmpty()) {
                return recurringTransactionRepository.findReportReadableBaseByUserGroup(
                        context.userGroupId(),
                        null,
                        asOfDate
                );
            }

            return recurringTransactionRepository.findReportReadableBaseAndSimulatedByUserGroup(
                    context.userGroupId(),
                    null,
                    context.simulationGroupIds(),
                    asOfDate
            );
        }

        if (context.simulationGroupIds().isEmpty()) {
            return recurringTransactionRepository.findReportReadableBaseByLinkedUserAccess(
                    context.userGroupId(),
                    context.currentUser().getUserId(),
                    null,
                    asOfDate
            );
        }

        return recurringTransactionRepository.findReportReadableBaseAndSimulatedByLinkedUserAccess(
                context.userGroupId(),
                context.currentUser().getUserId(),
                null,
                context.simulationGroupIds(),
                asOfDate
        );
    }

    private List<RecurringTransaction> findReportReadableRecurringTransactionsForAccounts(
            ReportRequestContext context,
            LocalDate asOfDate
    ) {
        if (context.canReadAllGroupRecurringTransactions()) {
            if (context.simulationGroupIds().isEmpty()) {
                return recurringTransactionRepository.findReportReadableBaseByUserGroupForAccounts(
                        context.userGroupId(),
                        context.accountIds(),
                        asOfDate
                );
            }

            return recurringTransactionRepository.findReportReadableBaseAndSimulatedByUserGroupForAccounts(
                    context.userGroupId(),
                    context.accountIds(),
                    context.simulationGroupIds(),
                    asOfDate
            );
        }

        if (context.simulationGroupIds().isEmpty()) {
            return recurringTransactionRepository.findReportReadableBaseByLinkedUserAccessForAccounts(
                    context.userGroupId(),
                    context.currentUser().getUserId(),
                    context.accountIds(),
                    asOfDate
            );
        }

        return recurringTransactionRepository.findReportReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
                context.userGroupId(),
                context.currentUser().getUserId(),
                context.accountIds(),
                context.simulationGroupIds(),
                asOfDate
        );
    }

    private Map<UUID, RecurringTransactionHistory> currentRuleByRecurringTransactionId(
            Set<UUID> recurringTransactionIds,
            UUID userGroupId,
            Map<UUID, LocalDate> targetDateByRecurringTransactionId
    ) {
        return recurringTransactionHistoryRepository
                .findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                        recurringTransactionIds,
                        userGroupId
                )
                .stream()
                .filter(history -> {
                    UUID recurringTransactionId =
                            history.getRecurringTransaction()
                                    .getRecurringTransactionId();

                    LocalDate targetDate =
                            targetDateByRecurringTransactionId.get(recurringTransactionId);

                    return targetDate != null
                            && !history.getEffectiveFrom().isAfter(targetDate)
                            && (
                            history.getEffectiveTo() == null
                                    || history.getEffectiveTo().isAfter(targetDate)
                    );
                })
                .collect(Collectors.toMap(
                        history -> history.getRecurringTransaction().getRecurringTransactionId(),
                        Function.identity(),
                        this::newerRecurringRule,
                        LinkedHashMap::new
                ));
    }

    private RecurringTransactionHistory newerRecurringRule(
            RecurringTransactionHistory first,
            RecurringTransactionHistory second
    ) {
        Comparator<RecurringTransactionHistory> comparator = Comparator
                .comparing(RecurringTransactionHistory::getEffectiveFrom)
                .thenComparing(RecurringTransactionHistory::getRecurringTransactionHistoryCreatedAt)
                .thenComparing(RecurringTransactionHistory::getRecurringTransactionHistoryId);

        return comparator.compare(first, second) >= 0 ? first : second;
    }

    private Map<UUID, RecurringTransactionDetailsHistory> currentDetailsByRecurringTransactionId(
            Set<UUID> recurringTransactionIds,
            UUID userGroupId,
            Map<UUID, LocalDate> targetDateByRecurringTransactionId
    ) {
        return recurringTransactionDetailsHistoryRepository
                .findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                        recurringTransactionIds,
                        userGroupId
                )
                .stream()
                .filter(details -> {
                    UUID recurringTransactionId =
                            details.getRecurringTransaction()
                                    .getRecurringTransactionId();

                    LocalDate targetDate =
                            targetDateByRecurringTransactionId.get(recurringTransactionId);

                    return targetDate != null
                            && !details.getRecurringTransactionDetailsEffectiveFrom()
                            .isAfter(targetDate);
                })
                .collect(Collectors.toMap(
                        details -> details.getRecurringTransaction().getRecurringTransactionId(),
                        Function.identity(),
                        this::newerRecurringDetails,
                        LinkedHashMap::new
                ));
    }

    private RecurringTransactionDetailsHistory newerRecurringDetails(
            RecurringTransactionDetailsHistory first,
            RecurringTransactionDetailsHistory second
    ) {
        Comparator<RecurringTransactionDetailsHistory> comparator = Comparator
                .comparing(RecurringTransactionDetailsHistory::getRecurringTransactionDetailsEffectiveFrom)
                .thenComparing(RecurringTransactionDetailsHistory::getRecurringTransactionDetailsHistoryCreatedAt)
                .thenComparing(RecurringTransactionDetailsHistory::getRecurringTransactionDetailsHistoryId);

        return comparator.compare(first, second) >= 0 ? first : second;
    }

    private BigDecimal annualize(RecurringTransactionHistory rule) {
        BigDecimal multiplier = yearlyMultiplier(
                rule.getRecurrenceUnit(),
                rule.getRecurrenceInterval()
        );

        return rule.getPaymentAmount()
                .multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal yearlyMultiplier(
            RecurrenceUnit recurrenceUnit,
            short recurrenceInterval
    ) {
        if (recurrenceUnit == null) {
            throw new IllegalStateException("finance.report.recurrenceUnitMissing");
        }

        if (recurrenceInterval <= 0) {
            throw new IllegalStateException("finance.report.recurrenceIntervalInvalid");
        }

        BigDecimal interval = BigDecimal.valueOf(recurrenceInterval);

        return switch (recurrenceUnit) {
            case DAY -> DAYS_IN_YEAR.divide(interval, 10, RoundingMode.HALF_UP);
            case WEEK -> WEEKS_IN_YEAR.divide(interval, 10, RoundingMode.HALF_UP);
            case MONTH -> MONTHS_IN_YEAR.divide(interval, 10, RoundingMode.HALF_UP);
            case YEAR -> BigDecimal.ONE.divide(interval, 10, RoundingMode.HALF_UP);
        };
    }

    private FinanceRecurringReportSummary toRecurringReportSummary(
            String currency,
            RecurringCurrencyAccumulator accumulator
    ) {
        BigDecimal annualIncome = accumulator.annualIncome.setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualExpenses = accumulator.annualExpenses.setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualNetBalance = annualIncome.subtract(annualExpenses)
                .setScale(2, RoundingMode.HALF_UP);

        return new FinanceRecurringReportSummary(
                currency,
                annualIncome,
                annualExpenses,
                annualNetBalance,
                monthlyAverage(annualIncome),
                monthlyAverage(annualExpenses),
                monthlyAverage(annualNetBalance)
        );
    }

    private BigDecimal monthlyAverage(BigDecimal annualAmount) {
        return annualAmount.divide(MONTHS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

    private List<FinanceReportExtremesByCurrency> calculateExtremesByCurrency(
            List<FinanceCalendarDailyBalance> dailyBalances,
            LocalDate asOfDate,
            LocalDate rangeFrom,
            LocalDate rangeTo
    ) {
        Map<String, List<CurrencyDailyPoint>> pointsByCurrency = new TreeMap<>();

        for (FinanceCalendarDailyBalance dailyBalance : dailyBalances) {
            for (FinanceCalendarCurrencyDailyBalance total : dailyBalance.totalsByCurrency()) {
                pointsByCurrency
                        .computeIfAbsent(total.currency(), ignored -> new ArrayList<>())
                        .add(new CurrencyDailyPoint(
                                dailyBalance.date(),
                                total.currency(),
                                total.endOfDayAccountsBalance(),
                                total.endOfDaySerenityline()
                        ));
            }
        }

        return pointsByCurrency.entrySet()
                .stream()
                .map(entry -> toExtremesByCurrency(
                        entry.getKey(),
                        entry.getValue(),
                        asOfDate,
                        rangeFrom,
                        rangeTo
                ))
                .toList();
    }

    private FinanceReportExtremesByCurrency toExtremesByCurrency(
            String currency,
            List<CurrencyDailyPoint> points,
            LocalDate asOfDate,
            LocalDate rangeFrom,
            LocalDate rangeTo
    ) {
        List<CurrencyDailyPoint> orderedPoints = points.stream()
                .sorted(Comparator.comparing(CurrencyDailyPoint::date))
                .toList();

        CurrencyDailyPoint minSerenityline =
                minBy(orderedPoints, CurrencyDailyPoint::serenityline);

        CurrencyDailyPoint maxSerenityline =
                maxBy(orderedPoints, CurrencyDailyPoint::serenityline);

        CurrencyDailyPoint minAccountBalance =
                minBy(orderedPoints, CurrencyDailyPoint::accountBalance);

        CurrencyDailyPoint maxAccountBalance =
                maxBy(orderedPoints, CurrencyDailyPoint::accountBalance);

        return new FinanceReportExtremesByCurrency(
                currency,
                asOfDate,
                rangeFrom,
                rangeTo,
                toReportPoint(
                        minSerenityline,
                        CurrencyDailyPoint::serenityline,
                        asOfDate,
                        rangeFrom,
                        rangeTo,
                        orderedPoints,
                        FinanceReportTrendDirection.DOWN
                ),
                toReportPoint(
                        maxSerenityline,
                        CurrencyDailyPoint::serenityline,
                        asOfDate,
                        rangeFrom,
                        rangeTo,
                        orderedPoints,
                        FinanceReportTrendDirection.UP
                ),
                toReportPoint(
                        minAccountBalance,
                        CurrencyDailyPoint::accountBalance,
                        asOfDate,
                        rangeFrom,
                        rangeTo,
                        orderedPoints,
                        FinanceReportTrendDirection.DOWN
                ),
                toReportPoint(
                        maxAccountBalance,
                        CurrencyDailyPoint::accountBalance,
                        asOfDate,
                        rangeFrom,
                        rangeTo,
                        orderedPoints,
                        FinanceReportTrendDirection.UP
                )
        );
    }

    private CurrencyDailyPoint minBy(
            List<CurrencyDailyPoint> points,
            Function<CurrencyDailyPoint, BigDecimal> valueExtractor
    ) {
        CurrencyDailyPoint best = null;

        for (CurrencyDailyPoint point : points) {
            if (best == null) {
                best = point;
                continue;
            }

            BigDecimal pointValue = valueExtractor.apply(point);
            BigDecimal bestValue = valueExtractor.apply(best);

            if (pointValue.compareTo(bestValue) < 0
                    || pointValue.compareTo(bestValue) == 0
                    && point.date().isBefore(best.date())) {
                best = point;
            }
        }

        return best;
    }

    private CurrencyDailyPoint maxBy(
            List<CurrencyDailyPoint> points,
            Function<CurrencyDailyPoint, BigDecimal> valueExtractor
    ) {
        CurrencyDailyPoint best = null;

        for (CurrencyDailyPoint point : points) {
            if (best == null) {
                best = point;
                continue;
            }

            BigDecimal pointValue = valueExtractor.apply(point);
            BigDecimal bestValue = valueExtractor.apply(best);

            if (pointValue.compareTo(bestValue) > 0
                    || pointValue.compareTo(bestValue) == 0
                    && point.date().isBefore(best.date())) {
                best = point;
            }
        }

        return best;
    }

    private FinanceReportPoint toReportPoint(
            CurrencyDailyPoint point,
            Function<CurrencyDailyPoint, BigDecimal> valueExtractor,
            LocalDate asOfDate,
            LocalDate rangeFrom,
            LocalDate rangeTo,
            List<CurrencyDailyPoint> orderedPoints,
            FinanceReportTrendDirection relevantTrendDirection
    ) {
        FinanceReportTrend trend = findMonotonicSuffixTrend(
                orderedPoints,
                valueExtractor,
                relevantTrendDirection,
                financeReportProperties.getTrendMinDays()
        );

        FinanceReportExtremeClassification classification =
                classifyExtreme(
                        point.date(),
                        rangeFrom,
                        rangeTo,
                        trend
                );

        return new FinanceReportPoint(
                point.date(),
                valueExtractor.apply(point),
                temporalPosition(point.date(), asOfDate),
                classification,
                classification == FinanceReportExtremeClassification.MONOTONIC_TREND_WITHIN_HORIZON
                        ? trend
                        : null
        );
    }

    private FinanceReportExtremeClassification classifyExtreme(
            LocalDate date,
            LocalDate rangeFrom,
            LocalDate rangeTo,
            FinanceReportTrend trend
    ) {
        if (date.equals(rangeTo) && trend != null) {
            return FinanceReportExtremeClassification.MONOTONIC_TREND_WITHIN_HORIZON;
        }

        if (date.equals(rangeFrom)) {
            return FinanceReportExtremeClassification.RANGE_START_BOUNDARY;
        }

        if (date.equals(rangeTo)) {
            return FinanceReportExtremeClassification.RANGE_END_BOUNDARY;
        }

        return FinanceReportExtremeClassification.IN_RANGE_EXTREME;
    }

    private FinanceReportTemporalPosition temporalPosition(
            LocalDate date,
            LocalDate asOfDate
    ) {
        if (date.isBefore(asOfDate)) {
            return FinanceReportTemporalPosition.PAST;
        }

        if (date.isAfter(asOfDate)) {
            return FinanceReportTemporalPosition.FUTURE;
        }

        return FinanceReportTemporalPosition.TODAY;
    }

    private FinanceReportTrend findMonotonicSuffixTrend(
            List<CurrencyDailyPoint> points,
            Function<CurrencyDailyPoint, BigDecimal> valueExtractor,
            FinanceReportTrendDirection direction,
            int minTrendDays
    ) {
        if (points.size() < minTrendDays) {
            return null;
        }

        int startIndex = points.size() - 1;
        boolean hasStrictMovement = false;

        while (startIndex > 0) {
            BigDecimal previous = valueExtractor.apply(points.get(startIndex - 1));
            BigDecimal current = valueExtractor.apply(points.get(startIndex));

            int comparison = previous.compareTo(current);

            if (direction == FinanceReportTrendDirection.UP) {
                if (comparison > 0) {
                    break;
                }

                if (comparison < 0) {
                    hasStrictMovement = true;
                }
            } else if (direction == FinanceReportTrendDirection.DOWN) {
                if (comparison < 0) {
                    break;
                }

                if (comparison > 0) {
                    hasStrictMovement = true;
                }
            } else {
                return null;
            }

            startIndex--;
        }

        int trendLength = points.size() - startIndex;

        if (trendLength < minTrendDays || !hasStrictMovement) {
            return null;
        }

        return new FinanceReportTrend(
                direction,
                points.get(startIndex).date(),
                points.get(points.size() - 1).date(),
                true
        );
    }

    private List<FinanceYearEndForecast> calculateYearEndForecasts(
            List<FinanceCalendarDailyBalance> dailyBalances,
            int startYear,
            int yearEndForecastYears
    ) {
        Map<LocalDate, FinanceCalendarDailyBalance> dailyBalanceByDate =
                dailyBalances.stream()
                        .collect(Collectors.toMap(
                                FinanceCalendarDailyBalance::date,
                                Function.identity(),
                                (first, second) -> first,
                                LinkedHashMap::new
                        ));

        List<FinanceYearEndForecast> result = new ArrayList<>();

        for (int offset = 0; offset <= yearEndForecastYears; offset++) {
            int year = startYear + offset;
            LocalDate date = LocalDate.of(year, 12, 31);

            FinanceCalendarDailyBalance dailyBalance =
                    dailyBalanceByDate.get(date);

            List<FinanceYearEndForecastByCurrency> balancesByCurrency =
                    dailyBalance == null
                            ? List.of()
                            : dailyBalance.totalsByCurrency()
                              .stream()
                              .sorted(Comparator.comparing(FinanceCalendarCurrencyDailyBalance::currency))
                              .map(total -> new FinanceYearEndForecastByCurrency(
                                      total.currency(),
                                      total.endOfDayAccountsBalance(),
                                      total.endOfDaySerenityline()
                              ))
                              .toList();

            result.add(new FinanceYearEndForecast(
                    year,
                    date,
                    balancesByCurrency
            ));
        }

        return result;
    }

    private record ReportRequestContext(
            User currentUser,
            UUID userGroupId,
            List<UUID> accountIds,
            List<UUID> simulationGroupIds,
            boolean canReadAllGroupRecurringTransactions
    ) {
    }

    private record CurrencyDailyPoint(
            LocalDate date,
            String currency,
            BigDecimal accountBalance,
            BigDecimal serenityline
    ) {
    }

    private static final class RecurringCurrencyAccumulator {

        private BigDecimal annualIncome = BigDecimal.ZERO;
        private BigDecimal annualExpenses = BigDecimal.ZERO;
    }
}