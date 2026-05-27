package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Component
public class RecurringTransactionOccurrenceGenerator {

    private static final int WEEKEND_ADJUSTMENT_WINDOW_DAYS = 2;

    public List<RecurringTransactionOccurrence> generateOccurrences(
            UUID recurringTransactionId,
            LocalDate firstPaymentDate,
            List<RecurringTransactionRuleSnapshot> ruleHistory,
            LocalDate from,
            LocalDate to
    ) {
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");
        Objects.requireNonNull(firstPaymentDate, "firstPaymentDate");
        Objects.requireNonNull(ruleHistory, "ruleHistory");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("finance.calendar.dateRangeInvalid");
        }

        if (ruleHistory.isEmpty()) {
            return List.of();
        }

        List<RecurringTransactionRuleSnapshot> sortedRules = ruleHistory.stream()
                .sorted(Comparator.comparingLong(RecurringTransactionRuleSnapshot::precedence))
                .toList();

        validateUniquePrecedence(sortedRules);

        List<PreparedRule> preparedRules = prepareRules(firstPaymentDate, sortedRules);

        Map<LocalDate, RecurringTransactionOccurrence> occurrencesByLogicalDate = new LinkedHashMap<>();

        LocalDate scanStart = from.minusDays(WEEKEND_ADJUSTMENT_WINDOW_DAYS);
        if (scanStart.isBefore(firstPaymentDate)) {
            scanStart = firstPaymentDate;
        }

        LocalDate scanEnd = to.plusDays(WEEKEND_ADJUSTMENT_WINDOW_DAYS);

        for (LocalDate logicalDate = scanStart; !logicalDate.isAfter(scanEnd); logicalDate = logicalDate.plusDays(1)) {
            Optional<PreparedRule> activeRule = activeRuleAt(preparedRules, logicalDate);

            if (activeRule.isEmpty()) {
                continue;
            }

            PreparedRule preparedRule = activeRule.get();
            RecurringTransactionRuleSnapshot rule = preparedRule.rule();

            if (!isScheduledLogicalDate(preparedRule.sequenceAnchorDate(), logicalDate, rule)) {
                continue;
            }

            if (isAfterRecurringEndDate(logicalDate, rule)) {
                continue;
            }

            if (isScheduledFinalReplacementDate(logicalDate, rule)) {
                continue;
            }

            LocalDate chargeDate = adjustPaymentDate(
                    logicalDate,
                    rule.paymentDateAdjustmentPolicy()
            );

            if (chargeDate.isBefore(from) || chargeDate.isAfter(to)) {
                continue;
            }

            occurrencesByLogicalDate.put(
                    logicalDate,
                    new RecurringTransactionOccurrence(
                            recurringTransactionId,
                            logicalDate,
                            chargeDate,
                            rule.paymentAmount(),
                            false
                    )
            );
        }

        addFinalOccurrences(
                recurringTransactionId,
                preparedRules,
                from,
                to,
                occurrencesByLogicalDate
        );

        return occurrencesByLogicalDate.values()
                .stream()
                .sorted(Comparator
                        .comparing(RecurringTransactionOccurrence::chargeDate)
                        .thenComparing(RecurringTransactionOccurrence::logicalDate)
                        .thenComparing(RecurringTransactionOccurrence::finalOccurrence))
                .toList();
    }

    private void validateUniquePrecedence(List<RecurringTransactionRuleSnapshot> sortedRules) {
        Set<Long> seen = new HashSet<>();

        for (RecurringTransactionRuleSnapshot rule : sortedRules) {
            if (!seen.add(rule.precedence())) {
                throw new IllegalArgumentException("finance.recurringTransaction.rulePrecedenceDuplicated");
            }
        }
    }

    private List<PreparedRule> prepareRules(
            LocalDate firstPaymentDate,
            List<RecurringTransactionRuleSnapshot> sortedRules
    ) {
        List<PreparedRule> preparedRules = new ArrayList<>();

        for (RecurringTransactionRuleSnapshot rule : sortedRules) {
            LocalDate sequenceAnchorDate = resolveSequenceAnchorDate(
                    firstPaymentDate,
                    preparedRules,
                    rule
            );

            preparedRules.add(new PreparedRule(rule, sequenceAnchorDate));
        }

        return List.copyOf(preparedRules);
    }

    private LocalDate resolveSequenceAnchorDate(
            LocalDate firstPaymentDate,
            List<PreparedRule> preparedRules,
            RecurringTransactionRuleSnapshot newRule
    ) {
        if (preparedRules.isEmpty()) {
            return firstPaymentDate;
        }

        LocalDate previousDate = newRule.effectiveFrom().minusDays(1);

        Optional<PreparedRule> previousActiveRule = activeRuleAt(preparedRules, previousDate);

        if (previousActiveRule.isEmpty()) {
            return firstPaymentDate;
        }

        PreparedRule previousRule = previousActiveRule.get();

        if (hasSameCadence(previousRule.rule(), newRule)) {
            return previousRule.sequenceAnchorDate();
        }

        return findLastCadenceLogicalOccurrenceBefore(
                firstPaymentDate,
                preparedRules,
                newRule.effectiveFrom()
        ).orElse(previousRule.sequenceAnchorDate());
    }

    private Optional<LocalDate> findLastCadenceLogicalOccurrenceBefore(
            LocalDate firstPaymentDate,
            List<PreparedRule> preparedRules,
            LocalDate beforeDate
    ) {
        LocalDate scanEnd = beforeDate.minusDays(1);

        if (scanEnd.isBefore(firstPaymentDate)) {
            return Optional.empty();
        }

        LocalDate lastOccurrence = null;

        for (LocalDate logicalDate = firstPaymentDate; !logicalDate.isAfter(scanEnd); logicalDate = logicalDate.plusDays(1)) {
            Optional<PreparedRule> activeRule = activeRuleAt(preparedRules, logicalDate);

            if (activeRule.isEmpty()) {
                continue;
            }

            PreparedRule preparedRule = activeRule.get();
            RecurringTransactionRuleSnapshot rule = preparedRule.rule();

            if (!isScheduledLogicalDate(preparedRule.sequenceAnchorDate(), logicalDate, rule)) {
                continue;
            }

            if (isAfterRecurringEndDate(logicalDate, rule)) {
                continue;
            }

            lastOccurrence = logicalDate;
        }

        return Optional.ofNullable(lastOccurrence);
    }

    private boolean hasSameCadence(
            RecurringTransactionRuleSnapshot left,
            RecurringTransactionRuleSnapshot right
    ) {
        return left.recurrenceUnit() == right.recurrenceUnit()
                && left.recurrenceInterval() == right.recurrenceInterval()
                && left.dayOfUnit() == right.dayOfUnit();
    }

    private void addFinalOccurrences(
            UUID recurringTransactionId,
            List<PreparedRule> preparedRules,
            LocalDate from,
            LocalDate to,
            Map<LocalDate, RecurringTransactionOccurrence> occurrencesByLogicalDate
    ) {
        Set<LocalDate> endDates = new HashSet<>();

        for (PreparedRule preparedRule : preparedRules) {
            if (preparedRule.rule().recurringTransactionEndDate() != null) {
                endDates.add(preparedRule.rule().recurringTransactionEndDate());
            }
        }

        for (LocalDate endDate : endDates) {
            Optional<PreparedRule> activeRule = activeRuleAt(preparedRules, endDate);

            if (activeRule.isEmpty()) {
                continue;
            }

            RecurringTransactionRuleSnapshot rule = activeRule.get().rule();

            if (rule.finalPaymentAmount() == null) {
                continue;
            }

            LocalDate chargeDate = adjustPaymentDate(
                    endDate,
                    rule.paymentDateAdjustmentPolicy()
            );

            if (chargeDate.isBefore(from) || chargeDate.isAfter(to)) {
                continue;
            }

            occurrencesByLogicalDate.put(
                    endDate,
                    new RecurringTransactionOccurrence(
                            recurringTransactionId,
                            endDate,
                            chargeDate,
                            rule.finalPaymentAmount(),
                            true
                    )
            );
        }
    }

    private Optional<PreparedRule> activeRuleAt(
            List<PreparedRule> preparedRules,
            LocalDate date
    ) {
        PreparedRule active = null;

        for (PreparedRule preparedRule : preparedRules) {
            if (preparedRule.rule().isEffectiveAt(date)) {
                active = preparedRule;
            }
        }

        return Optional.ofNullable(active);
    }

    private boolean isScheduledLogicalDate(
            LocalDate sequenceAnchorDate,
            LocalDate logicalDate,
            RecurringTransactionRuleSnapshot rule
    ) {
        if (logicalDate.isBefore(sequenceAnchorDate)) {
            return false;
        }

        return switch (rule.recurrenceUnit()) {
            case DAY -> isScheduledDay(sequenceAnchorDate, logicalDate, rule.recurrenceInterval());
            case WEEK -> isScheduledWeek(sequenceAnchorDate, logicalDate, rule.dayOfUnit(), rule.recurrenceInterval());
            case MONTH ->
                    isScheduledMonth(sequenceAnchorDate, logicalDate, rule.dayOfUnit(), rule.recurrenceInterval());
            case YEAR -> isScheduledYear(sequenceAnchorDate, logicalDate, rule.dayOfUnit(), rule.recurrenceInterval());
        };
    }

    private boolean isScheduledDay(
            LocalDate sequenceAnchorDate,
            LocalDate logicalDate,
            short interval
    ) {
        long days = ChronoUnit.DAYS.between(sequenceAnchorDate, logicalDate);
        return days >= 0 && days % interval == 0;
    }

    private boolean isScheduledWeek(
            LocalDate sequenceAnchorDate,
            LocalDate logicalDate,
            short dayOfUnit,
            short interval
    ) {
        if (logicalDate.getDayOfWeek().getValue() != dayOfUnit) {
            return false;
        }

        LocalDate anchorWeekStart = sequenceAnchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate logicalWeekStart = logicalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        long weeks = ChronoUnit.WEEKS.between(anchorWeekStart, logicalWeekStart);
        return weeks >= 0 && weeks % interval == 0;
    }

    private boolean isScheduledMonth(
            LocalDate sequenceAnchorDate,
            LocalDate logicalDate,
            short dayOfUnit,
            short interval
    ) {
        YearMonth logicalYearMonth = YearMonth.from(logicalDate);

        int scheduledDayOfMonth = Math.min(
                dayOfUnit,
                logicalYearMonth.lengthOfMonth()
        );

        if (logicalDate.getDayOfMonth() != scheduledDayOfMonth) {
            return false;
        }

        long months = ChronoUnit.MONTHS.between(
                YearMonth.from(sequenceAnchorDate),
                logicalYearMonth
        );

        return months >= 0 && months % interval == 0;
    }

    private boolean isScheduledYear(
            LocalDate sequenceAnchorDate,
            LocalDate logicalDate,
            short dayOfUnit,
            short interval
    ) {
        int scheduledDayOfYear = Math.min(
                dayOfUnit,
                logicalDate.lengthOfYear()
        );

        if (logicalDate.getDayOfYear() != scheduledDayOfYear) {
            return false;
        }

        long years = ChronoUnit.YEARS.between(
                sequenceAnchorDate.withDayOfYear(1),
                logicalDate.withDayOfYear(1)
        );

        return years >= 0 && years % interval == 0;
    }

    private LocalDate adjustPaymentDate(
            LocalDate date,
            PaymentDateAdjustmentPolicy policy
    ) {
        return switch (policy) {
            case NONE -> date;
            case PREVIOUS_BUSINESS_DAY -> previousBusinessDayIfNeeded(date);
            case NEXT_BUSINESS_DAY -> nextBusinessDayIfNeeded(date);
        };
    }

    private LocalDate previousBusinessDayIfNeeded(LocalDate date) {
        LocalDate adjusted = date;

        while (isWeekend(adjusted)) {
            adjusted = adjusted.minusDays(1);
        }

        return adjusted;
    }

    private LocalDate nextBusinessDayIfNeeded(LocalDate date) {
        LocalDate adjusted = date;

        while (isWeekend(adjusted)) {
            adjusted = adjusted.plusDays(1);
        }

        return adjusted;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private boolean isAfterRecurringEndDate(
            LocalDate logicalDate,
            RecurringTransactionRuleSnapshot rule
    ) {
        return rule.recurringTransactionEndDate() != null
                && logicalDate.isAfter(rule.recurringTransactionEndDate());
    }

    private boolean isScheduledFinalReplacementDate(
            LocalDate logicalDate,
            RecurringTransactionRuleSnapshot rule
    ) {
        return rule.recurringTransactionEndDate() != null
                && logicalDate.isEqual(rule.recurringTransactionEndDate())
                && rule.finalPaymentAmount() != null;
    }

    private record PreparedRule(
            RecurringTransactionRuleSnapshot rule,
            LocalDate sequenceAnchorDate
    ) {
    }
}