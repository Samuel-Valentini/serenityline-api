package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringTransactionOccurrenceGeneratorTest {

    private final RecurringTransactionOccurrenceGenerator generator =
            new RecurringTransactionOccurrenceGenerator();

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
    void shouldGenerateMonthlyOccurrencesInsideRange() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                List.of(rule(
                        LocalDate.of(2026, 6, 10),
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
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 9, 30)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 9, 10)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00")
                );
    }

    @Test
    void shouldGenerateDailyOccurrencesUsingInterval() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                List.of(rule(
                        LocalDate.of(2026, 6, 1),
                        null,
                        1,
                        2,
                        RecurrenceUnit.DAY,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-10.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 8)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3),
                        LocalDate.of(2026, 6, 5),
                        LocalDate.of(2026, 6, 7)
                );
    }

    @Test
    void shouldGenerateWeeklyOccurrencesUsingDayOfWeekAndInterval() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                List.of(rule(
                        LocalDate.of(2026, 6, 1),
                        null,
                        1,
                        2,
                        RecurrenceUnit.WEEK,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-30.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 15),
                        LocalDate.of(2026, 6, 29)
                );
    }

    @Test
    void shouldGenerateYearlyOccurrencesUsingDayOfYearAndInterval() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 3, 1),
                List.of(rule(
                        LocalDate.of(2026, 3, 1),
                        null,
                        60,
                        1,
                        RecurrenceUnit.YEAR,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-300.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2028, 12, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2027, 3, 1),
                        LocalDate.of(2028, 2, 29)
                );
    }

    @Test
    void shouldUseLatestEffectiveRuleByPrecedence() {
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
                                null,
                                null,
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 8, 1),
                                null,
                                10,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-150.00",
                                null,
                                null,
                                2
                        )
                ),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 9, 30)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-150.00"),
                        new BigDecimal("-150.00")
                );
    }

    @Test
    void shouldApplyBoundedRuleOverrideOnlyInsideEffectiveWindow() {
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
                                null,
                                null,
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 9, 1),
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
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 10, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-200.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00")
                );
    }

    @Test
    void shouldStopGeneratingNormalOccurrencesAfterEndDate() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                List.of(rule(
                        LocalDate.of(2026, 6, 10),
                        null,
                        10,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-100.00",
                        LocalDate.of(2026, 8, 15),
                        null,
                        1
                )),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 10)
                );
    }

    @Test
    void shouldGenerateFinalOccurrenceAtEndDateWhenFinalPaymentAmountExists() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                List.of(rule(
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
                )),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 15)
                );

        RecurringTransactionOccurrence finalOccurrence = occurrences.getLast();

        assertThat(finalOccurrence.amount()).isEqualByComparingTo(new BigDecimal("-50.00"));
        assertThat(finalOccurrence.finalOccurrence()).isTrue();
    }

    @Test
    void shouldReplaceNormalOccurrenceWithFinalOccurrenceWhenEndDateIsScheduledDate() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                List.of(rule(
                        LocalDate.of(2026, 6, 10),
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
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 10)
                );

        RecurringTransactionOccurrence finalOccurrence = occurrences.getLast();

        assertThat(finalOccurrence.amount()).isEqualByComparingTo(new BigDecimal("-50.00"));
        assertThat(finalOccurrence.finalOccurrence()).isTrue();
    }

    @Test
    void shouldMoveWeekendPaymentToPreviousBusinessDay() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 8, 15),
                List.of(rule(
                        LocalDate.of(2026, 8, 15),
                        null,
                        15,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void shouldMoveWeekendPaymentToNextBusinessDay() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 8, 15),
                List.of(rule(
                        LocalDate.of(2026, 8, 15),
                        null,
                        15,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NEXT_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().logicalDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(occurrences.getFirst().chargeDate()).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    @Test
    void shouldUseLastDayOfMonthWhenMonthlyDayDoesNotExist() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 1, 31),
                List.of(rule(
                        LocalDate.of(2026, 1, 31),
                        null,
                        31,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 30),
                        LocalDate.of(2026, 5, 31)
                );
    }

    @Test
    void shouldUseLastDayOfYearWhenYearlyDayDoesNotExist() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2024, 12, 31),
                List.of(rule(
                        LocalDate.of(2024, 12, 31),
                        null,
                        366,
                        1,
                        RecurrenceUnit.YEAR,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-300.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2028, 12, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2024, 12, 31),
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2026, 12, 31),
                        LocalDate.of(2027, 12, 31),
                        LocalDate.of(2028, 12, 31)
                );
    }

    @Test
    void shouldIncludeLogicalPaymentAfterRangeWhenMovedToPreviousBusinessDayInsideRange() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 8, 15),
                List.of(rule(
                        LocalDate.of(2026, 8, 15),
                        null,
                        15,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 14)
        );

        assertThat(occurrences).hasSize(1);

        RecurringTransactionOccurrence occurrence = occurrences.getFirst();

        assertThat(occurrence.logicalDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(occurrence.chargeDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(occurrence.amount()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    void shouldIncludeLogicalPaymentBeforeRangeWhenMovedToNextBusinessDayInsideRange() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 8, 15),
                List.of(rule(
                        LocalDate.of(2026, 8, 15),
                        null,
                        15,
                        1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NEXT_BUSINESS_DAY,
                        "-100.00",
                        null,
                        null,
                        1
                )),
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 17)
        );

        assertThat(occurrences).hasSize(1);

        RecurringTransactionOccurrence occurrence = occurrences.getFirst();

        assertThat(occurrence.logicalDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(occurrence.chargeDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(occurrence.amount()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    void shouldAnchorNewCadenceToLastOldLogicalOccurrenceWhenRecurrenceIntervalChanges() {
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
                                null,
                                null,
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 8, 1),
                                null,
                                10,
                                2,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-200.00",
                                null,
                                null,
                                2
                        )
                ),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 11, 30)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 11, 10)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-200.00"),
                        new BigDecimal("-200.00")
                );
    }

    @Test
    void shouldNotRestartCadenceWhenOnlyAmountChanges() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                List.of(
                        rule(
                                LocalDate.of(2026, 6, 10),
                                null,
                                10,
                                2,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-100.00",
                                null,
                                null,
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 7, 1),
                                null,
                                10,
                                2,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-150.00",
                                null,
                                null,
                                2
                        )
                ),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 10, 10),
                        LocalDate.of(2026, 12, 10)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-150.00"),
                        new BigDecimal("-150.00"),
                        new BigDecimal("-150.00")
                );
    }

    @Test
    void shouldAnchorNewCadenceToLastOldLogicalOccurrenceWhenDayOfUnitChanges() {
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
                                null,
                                null,
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 8, 1),
                                null,
                                20,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-200.00",
                                null,
                                null,
                                2
                        )
                ),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 10, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 9, 20),
                        LocalDate.of(2026, 10, 20)
                );
    }

    @Test
    void shouldAnchorNewCadenceUsingLogicalDateNotAdjustedChargeDate() {
        UUID recurringTransactionId = UUID.randomUUID();

        List<RecurringTransactionOccurrence> occurrences = generator.generateOccurrences(
                recurringTransactionId,
                LocalDate.of(2026, 8, 15),
                List.of(
                        rule(
                                LocalDate.of(2026, 8, 15),
                                null,
                                1,
                                1,
                                RecurrenceUnit.DAY,
                                PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                                "-100.00",
                                null,
                                null,
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 8, 16),
                                null,
                                1,
                                2,
                                RecurrenceUnit.DAY,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-200.00",
                                null,
                                null,
                                2
                        )
                ),
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 20)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::logicalDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 17),
                        LocalDate.of(2026, 8, 19)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 17),
                        LocalDate.of(2026, 8, 19)
                );
    }

    @Test
    void shouldLetEndDateRuleWithHigherPrecedenceStopPreviouslyCreatedFutureRules() {
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
                                null,
                                null,
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
                        ),
                        rule(
                                LocalDate.of(2026, 9, 30),
                                null,
                                10,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-100.00",
                                LocalDate.of(2026, 9, 30),
                                null,
                                3
                        )
                ),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2027, 1, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 9, 10)
                );
    }

    @Test
    void shouldReopenRecurringTransactionWhenLaterRuleClearsEndDate() {
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
                                null,
                                null,
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 9, 30),
                                null,
                                10,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-100.00",
                                LocalDate.of(2026, 9, 30),
                                null,
                                2
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
                                3
                        )
                ),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2027, 1, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::chargeDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 12, 10),
                        LocalDate.of(2027, 1, 10)
                );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::amount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-100.00"),
                        new BigDecimal("-200.00"),
                        new BigDecimal("-200.00")
                );
    }

    @Test
    void shouldNotRecalculateFutureRuleAnchorWhenHistoricalCorrectionIsAddedLater() {
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
                                null,
                                null,
                                1
                        ),
                        rule(
                                LocalDate.of(2026, 9, 1),
                                null,
                                10,
                                2,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-200.00",
                                null,
                                null,
                                2
                        ),
                        rule(
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 9, 1),
                                20,
                                1,
                                RecurrenceUnit.MONTH,
                                PaymentDateAdjustmentPolicy.NONE,
                                "-150.00",
                                null,
                                null,
                                3
                        )
                ),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertThat(occurrences)
                .extracting(RecurringTransactionOccurrence::logicalDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 10, 10),
                        LocalDate.of(2026, 12, 10)
                );
    }
}