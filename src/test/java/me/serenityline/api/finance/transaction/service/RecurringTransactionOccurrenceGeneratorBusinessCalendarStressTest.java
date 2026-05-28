package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.calendar.BusinessCalendar;
import me.serenityline.api.finance.calendar.PaymentAccountCurrencySnapshot;
import me.serenityline.api.finance.calendar.PaymentBusinessCalendarProvider;
import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurringTransactionOccurrenceGeneratorBusinessCalendarStressTest {

    private final RecurringTransactionOccurrenceGenerator generator =
            new RecurringTransactionOccurrenceGenerator();

    private static PaymentBusinessCalendarProvider fixedProvider(
            BusinessCalendar calendar,
            int adjustmentWindowDays
    ) {
        return new PaymentBusinessCalendarProvider() {
            @Override
            public BusinessCalendar calendarAt(LocalDate logicalDate) {
                Objects.requireNonNull(logicalDate, "logicalDate");
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

    private static PaymentAccountCurrencySnapshot snapshot(
            LocalDate effectiveFrom,
            UUID accountId,
            String currencyCode,
            long precedence
    ) {
        return new PaymentAccountCurrencySnapshot(
                effectiveFrom,
                accountId,
                currencyCode,
                precedence
        );
    }

    @Test
    void shouldRejectNegativeAdjustmentWindowDays() {
        PaymentBusinessCalendarProvider provider = new PaymentBusinessCalendarProvider() {
            @Override
            public BusinessCalendar calendarAt(LocalDate logicalDate) {
                return WeekendAndFixedHolidayCalendar.withNoHolidays();
            }

            @Override
            public int adjustmentWindowDays() {
                return -1;
            }
        };

        assertThatThrownBy(() -> generator.generateOccurrences(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                List.of(rule(
                        LocalDate.of(2026, 1, 1),
                        null,
                        1,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                provider
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.adjustmentWindowInvalid");
    }

    @Test
    void shouldRejectNullCalendarReturnedByProvider() {
        PaymentBusinessCalendarProvider provider = new PaymentBusinessCalendarProvider() {
            @Override
            public BusinessCalendar calendarAt(LocalDate logicalDate) {
                return null;
            }

            @Override
            public int adjustmentWindowDays() {
                return 14;
            }
        };

        assertThatThrownBy(() -> generator.generateOccurrences(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 10),
                List.of(rule(
                        LocalDate.of(2026, 1, 10),
                        null,
                        10,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                provider
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("calendarProvider.calendarAt");
    }

    @Test
    void shouldNotMoveDateWhenPolicyIsNoneEvenIfDateIsHoliday() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                new WeekendAndFixedHolidayCalendar(Set.of(LocalDate.of(2026, 12, 25))),
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
                        PaymentDateAdjustmentPolicy.NONE,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 12, 25),
                provider
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 12, 25));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void shouldMoveAcrossHolidayWeekendClusterToPreviousBusinessDay() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                new WeekendAndFixedHolidayCalendar(Set.of(
                        LocalDate.of(2026, 12, 24),
                        LocalDate.of(2026, 12, 25)
                )),
                14
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 12, 26),
                List.of(rule(
                        LocalDate.of(2026, 12, 26),
                        null,
                        26,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 12, 23),
                LocalDate.of(2026, 12, 23),
                provider
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 12, 26));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 12, 23));
    }

    @Test
    void shouldMoveAcrossHolidayWeekendClusterToNextBusinessDay() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                new WeekendAndFixedHolidayCalendar(Set.of(
                        LocalDate.of(2026, 12, 24),
                        LocalDate.of(2026, 12, 25)
                )),
                14
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 12, 24),
                List.of(rule(
                        LocalDate.of(2026, 12, 24),
                        null,
                        24,
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
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    void shouldIncludeNormalOccurrenceAfterRangeWhenHolidayCalendarMovesItInsideRange() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                new WeekendAndFixedHolidayCalendar(Set.of(
                        LocalDate.of(2026, 12, 24),
                        LocalDate.of(2026, 12, 25)
                )),
                14
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 12, 26),
                List.of(rule(
                        LocalDate.of(2026, 12, 26),
                        null,
                        26,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 12, 23),
                LocalDate.of(2026, 12, 23),
                provider
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 12, 26));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 12, 23));
    }

    @Test
    void shouldReturnEmptyWhenAdjustmentWindowIsTooSmallToReachLogicalDateAfterRange() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                new WeekendAndFixedHolidayCalendar(Set.of(
                        LocalDate.of(2026, 12, 24),
                        LocalDate.of(2026, 12, 25)
                )),
                1
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 12, 26),
                List.of(rule(
                        LocalDate.of(2026, 12, 26),
                        null,
                        26,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 12, 23),
                LocalDate.of(2026, 12, 23),
                provider
        );

        assertThat(occurrences).isEmpty();
    }

    @Test
    void shouldIncludeFinalOccurrenceAfterRangeWhenMovedToPreviousBusinessDayInsideRange() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                new WeekendAndFixedHolidayCalendar(Set.of(
                        LocalDate.of(2026, 12, 24),
                        LocalDate.of(2026, 12, 25)
                )),
                14
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 12, 1),
                List.of(rule(
                        LocalDate.of(2026, 12, 1),
                        null,
                        1,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-100.00",
                        LocalDate.of(2026, 12, 26),
                        "-50.00",
                        1
                )),
                LocalDate.of(2026, 12, 23),
                LocalDate.of(2026, 12, 23),
                provider
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 12, 26));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 12, 23));
        assertThat(occurrences.getFirst().amount()).isEqualByComparingTo(new BigDecimal("-50.00"));
        assertThat(occurrences.getFirst().finalOccurrence()).isTrue();
    }

    @Test
    void shouldUseCalendarSelectedByLogicalDateNotByRangeDate() {
        UUID recurringTransactionId = UUID.randomUUID();

        BusinessCalendar strictHolidayCalendar = new WeekendAndFixedHolidayCalendar(Set.of(
                LocalDate.of(2026, 12, 25)
        ));

        BusinessCalendar weekendOnlyCalendar = WeekendAndFixedHolidayCalendar.withNoHolidays();

        PaymentBusinessCalendarProvider provider = new PaymentBusinessCalendarProvider() {
            @Override
            public BusinessCalendar calendarAt(LocalDate logicalDate) {
                if (logicalDate.isEqual(LocalDate.of(2026, 12, 25))) {
                    return strictHolidayCalendar;
                }

                return weekendOnlyCalendar;
            }

            @Override
            public int adjustmentWindowDays() {
                return 14;
            }
        };

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
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 12, 25));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 12, 24));
    }

    @Test
    void shouldAllowMultipleDifferentLogicalDatesToHaveSameChargeDate() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                WeekendAndFixedHolidayCalendar.withNoHolidays(),
                2
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 8, 14),
                List.of(rule(
                        LocalDate.of(2026, 8, 14),
                        null,
                        1,
                        1,
                        RecurrenceUnit.DAY,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-10.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 14),
                provider
        );

        assertThat(occurrences).hasSize(3);

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::logicalDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 16)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-10.00"),
                        new BigDecimal("-10.00"),
                        new BigDecimal("-10.00")
                );
    }

    @Test
    void shouldAllowTwoDifferentLogicalDatesToHaveSameChargeDate() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                WeekendAndFixedHolidayCalendar.withNoHolidays(),
                2
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 8, 15),
                List.of(rule(
                        LocalDate.of(2026, 8, 15),
                        null,
                        1,
                        1,
                        RecurrenceUnit.DAY,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-10.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 14),
                provider
        );

        assertThat(occurrences).hasSize(2);

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::logicalDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 16)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14)
                );
    }

    @Test
    void shouldSortByChargeDateThenLogicalDateThenFinalFlagWhenCalendarAdjustsDates() {
        UUID recurringTransactionId = UUID.randomUUID();

        PaymentBusinessCalendarProvider provider = fixedProvider(
                WeekendAndFixedHolidayCalendar.withNoHolidays(),
                2
        );

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 8, 14),
                List.of(rule(
                        LocalDate.of(2026, 8, 14),
                        null,
                        1,
                        1,
                        RecurrenceUnit.DAY,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-10.00",
                        LocalDate.of(2026, 8, 16),
                        "-99.00",
                        1
                )),
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 14),
                provider
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::logicalDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 16)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::finalOccurrence)
                .containsExactly(false, false, true);
    }

    @Test
    void shouldNotRequireCalendarWhenFinalOccurrencePolicyIsNone() {
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
                LocalDate.of(2026, 8, 10),
                List.of(rule(
                        LocalDate.of(2026, 8, 10),
                        null,
                        10,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-100.00",
                        LocalDate.of(2026, 8, 10),
                        "-50.00",
                        1
                )),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 10),
                provider
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(occurrences.getFirst().amount()).isEqualByComparingTo(new BigDecimal("-50.00"));
        assertThat(occurrences.getFirst().finalOccurrence()).isTrue();
    }

    @Test
    void shouldNotGenerateOldFinalOccurrenceWhenLaterRuleClearsEndDateBeforeOldEndDate() {
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
                                LocalDate.of(2026, 9, 30),
                                "-50.00",
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 8, 1),
                                null,
                                10,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-100.00",
                                null,
                                null,
                                2
                        )
                ),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 10, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::logicalDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 10, 10)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::finalOccurrence)
                .containsExactly(false, false, false);
    }

    @Test
    void shouldKeepHistoricalFinalOccurrenceWhenRecurringIsReopenedAfterEndDate() {
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
                                LocalDate.of(2026, 9, 30),
                                "-50.00",
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 12, 1),
                                null,
                                10,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-200.00",
                                null,
                                null,
                                2
                        )
                ),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::logicalDate)
                .containsExactly(
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 30),
                        LocalDate.of(2026, 12, 10)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-50.00"),
                        new BigDecimal("-200.00")
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::finalOccurrence)
                .containsExactly(false, true, false);
    }

    @Test
    void shouldRejectBlankCurrencyCodeInSnapshot() {
        assertThatThrownBy(() -> snapshot(
                LocalDate.of(2026, 1, 1),
                UUID.randomUUID(),
                "   ",
                1
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.account.currencyRequired");
    }

    @Test
    void shouldRejectNonPositiveSnapshotPrecedence() {
        assertThatThrownBy(() -> snapshot(
                LocalDate.of(2026, 1, 1),
                UUID.randomUUID(),
                "EUR",
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.accountCurrency.precedenceInvalid");
    }

    private static final class WeekendAndFixedHolidayCalendar implements BusinessCalendar {

        private final Set<LocalDate> holidays;

        private WeekendAndFixedHolidayCalendar(Set<LocalDate> holidays) {
            this.holidays = Set.copyOf(holidays);
        }

        private static WeekendAndFixedHolidayCalendar withNoHolidays() {
            return new WeekendAndFixedHolidayCalendar(Set.of());
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
                    || date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        }
    }
}