package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.calendar.BusinessCalendar;
import me.serenityline.api.finance.calendar.PaymentBusinessCalendarProvider;
import me.serenityline.api.finance.calendar.StrataCurrencyBusinessCalendarResolver;
import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringTransactionOccurrenceGeneratorBusinessCalendarTest {

    private final RecurringTransactionOccurrenceGenerator generator =
            new RecurringTransactionOccurrenceGenerator();

    private static PaymentBusinessCalendarProvider fixedProvider(
            BusinessCalendar calendar,
            int adjustmentWindowDays
    ) {
        return new PaymentBusinessCalendarProvider() {
            @Override
            public BusinessCalendar calendarAt(LocalDate logicalDate) {
                return calendar;
            }

            @Override
            public int adjustmentWindowDays() {
                return adjustmentWindowDays;
            }
        };
    }

    private static RecurringTransactionRuleSnapshot rule(
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int dayOfUnit,
            int recurrenceInterval,
            RecurrenceUnit recurrenceUnit,
            PaymentDateAdjustmentPolicy paymentDateAdjustmentPolicy,
            String paymentAmount,
            LocalDate recurringTransactionEndDate,
            String finalPaymentAmount,
            long precedence
    ) {
        return new RecurringTransactionRuleSnapshot(
                effectiveFrom,
                effectiveTo,
                (short) dayOfUnit,
                (short) recurrenceInterval,
                recurrenceUnit,
                paymentDateAdjustmentPolicy,
                new BigDecimal(paymentAmount),
                recurringTransactionEndDate,
                finalPaymentAmount == null ? null : new BigDecimal(finalPaymentAmount),
                precedence
        );
    }

    @Test
    void shouldUseCustomCalendarToMoveHolidayToPreviousBusinessDayInsideRange() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                new FixedHolidayCalendar(Set.of(LocalDate.of(2026, 12, 25))),
                14
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 12, 25),
                List.of(rule(
                        LocalDate.of(2026, 12, 25),
                        null,
                        25,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 12, 24),
                LocalDate.of(2026, 12, 24),
                provider
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.get(0).logicalDate()).isEqualTo(LocalDate.of(2026, 12, 25));
        assertThat(occurrences.get(0).chargeDate()).isEqualTo(LocalDate.of(2026, 12, 24));
    }

    @Test
    void shouldUseCustomCalendarToMoveHolidayToNextBusinessDayInsideRange() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                new FixedHolidayCalendar(Set.of(LocalDate.of(2026, 12, 25))),
                14
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 12, 25),
                List.of(rule(
                        LocalDate.of(2026, 12, 25),
                        null,
                        25,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NEXT_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2026, 12, 28),
                provider
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.get(0).logicalDate()).isEqualTo(LocalDate.of(2026, 12, 25));
        assertThat(occurrences.get(0).chargeDate()).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    void shouldResolveEuroCurrencyToTargetCalendar() {
        StrataCurrencyBusinessCalendarResolver resolver =
                new StrataCurrencyBusinessCalendarResolver();

        BusinessCalendar calendar = resolver.resolveByCurrency("EUR");

        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void shouldFallbackToWeekendOnlyCalendarForUnknownCurrency() {
        StrataCurrencyBusinessCalendarResolver resolver =
                new StrataCurrencyBusinessCalendarResolver();

        BusinessCalendar calendar = resolver.resolveByCurrency("UNKNOWN");

        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void shouldFallbackToWeekendOnlyCalendarAndMoveWeekendForUnknownCurrency() {
        StrataCurrencyBusinessCalendarResolver resolver =
                new StrataCurrencyBusinessCalendarResolver();

        BusinessCalendar calendar = resolver.resolveByCurrency("UNKNOWN");

        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 1, 2));

        assertThat(calendar.nextOrSame(LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void shouldNotGenerateObsoleteFinalOccurrenceWhenLaterRuleChangesEndDate() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                List.of(
                        rule(
                                LocalDate.of(2026, 6, 10),
                                null,
                                10,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-100.00",
                                LocalDate.of(2026, 8, 15),
                                "-50.00",
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 7, 1),
                                null,
                                10,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-100.00",
                                LocalDate.of(2026, 9, 15),
                                "-75.00",
                                2
                        )
                ),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 30)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::logicalDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 15)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-75.00")
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::finalOccurrence)
                .containsExactly(false, false, true);
    }

    @Test
    void shouldNotRequireCalendarWhenPolicyIsNone() {
        PaymentBusinessCalendarProvider provider = new PaymentBusinessCalendarProvider() {
            @Override
            public BusinessCalendar calendarAt(LocalDate logicalDate) {
                return null;
            }

            @Override
            public int adjustmentWindowDays() {
                return 0;
            }
        };

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 10),
                List.of(rule(
                        LocalDate.of(2026, 1, 10),
                        null,
                        10,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 10),
                provider
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    private static final class FixedHolidayCalendar implements BusinessCalendar {

        private final Set<LocalDate> holidays;

        private FixedHolidayCalendar(Set<LocalDate> holidays) {
            this.holidays = holidays;
        }

        @Override
        public LocalDate previousOrSame(LocalDate date) {
            LocalDate adjusted = date;

            while (isHolidayOrWeekend(adjusted)) {
                adjusted = adjusted.minusDays(1);
            }

            return adjusted;
        }

        @Override
        public LocalDate nextOrSame(LocalDate date) {
            LocalDate adjusted = date;

            while (isHolidayOrWeekend(adjusted)) {
                adjusted = adjusted.plusDays(1);
            }

            return adjusted;
        }

        private boolean isHolidayOrWeekend(LocalDate date) {
            return holidays.contains(date)
                    || date.getDayOfWeek().getValue() >= 6;
        }
    }
}