package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.user.entity.UserGroup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RecurringTransactionRuleSnapshotMapperTest {

    private final RecurringTransactionRuleSnapshotMapper mapper =
            new RecurringTransactionRuleSnapshotMapper();

    private static RecurringTransactionHistory history(
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            short dayOfUnit,
            short recurrenceInterval,
            RecurrenceUnit recurrenceUnit,
            PaymentDateAdjustmentPolicy paymentDateAdjustmentPolicy,
            String paymentAmount,
            LocalDate recurringTransactionEndDate,
            String finalPaymentAmount
    ) {
        return RecurringTransactionHistory.create(
                recurringTransaction(effectiveFrom),
                effectiveFrom,
                effectiveTo,
                dayOfUnit,
                recurrenceInterval,
                recurrenceUnit,
                paymentDateAdjustmentPolicy,
                new BigDecimal(paymentAmount),
                recurringTransactionEndDate,
                finalPaymentAmount == null ? null : new BigDecimal(finalPaymentAmount)
        );
    }

    private static RecurringTransaction recurringTransaction(LocalDate firstPaymentDate) {
        return RecurringTransaction.create(
                true,
                firstPaymentDate,
                false,
                null,
                true,
                (short) 7,
                mock(UserGroup.class)
        );
    }

    @Test
    void shouldMapSingleHistoryRowToSnapshot() {
        RecurringTransactionHistory history = history(
                LocalDate.of(2026, 1, 10),
                null,
                (short) 10,
                (short) 1,
                RecurrenceUnit.MONTH,
                PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                "-100.00",
                LocalDate.of(2026, 12, 31),
                "-50.00"
        );

        List<RecurringTransactionRuleSnapshot> snapshots =
                mapper.toSnapshots(List.of(history));

        assertThat(snapshots).hasSize(1);

        RecurringTransactionRuleSnapshot snapshot = snapshots.getFirst();

        assertThat(snapshot.effectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(snapshot.effectiveTo()).isNull();
        assertThat(snapshot.dayOfUnit()).isEqualTo((short) 10);
        assertThat(snapshot.recurrenceInterval()).isEqualTo((short) 1);
        assertThat(snapshot.recurrenceUnit()).isEqualTo(RecurrenceUnit.MONTH);
        assertThat(snapshot.paymentDateAdjustmentPolicy())
                .isEqualTo(PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY);
        assertThat(snapshot.paymentAmount()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(snapshot.recurringTransactionEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(snapshot.finalPaymentAmount()).isEqualByComparingTo(new BigDecimal("-50.00"));
        assertThat(snapshot.precedence()).isEqualTo(1);
    }

    @Test
    void shouldAssignPrecedenceAccordingToInputOrder() {
        RecurringTransactionHistory first = history(
                LocalDate.of(2026, 1, 1),
                null,
                (short) 1,
                (short) 1,
                RecurrenceUnit.MONTH,
                PaymentDateAdjustmentPolicy.NONE,
                "-100.00",
                null,
                null
        );

        RecurringTransactionHistory second = history(
                LocalDate.of(2026, 2, 1),
                null,
                (short) 1,
                (short) 1,
                RecurrenceUnit.MONTH,
                PaymentDateAdjustmentPolicy.NONE,
                "-200.00",
                null,
                null
        );

        List<RecurringTransactionRuleSnapshot> snapshots =
                mapper.toSnapshots(List.of(first, second));

        assertThat(snapshots)
                .extracting(RecurringTransactionRuleSnapshot::precedence)
                .containsExactly(1L, 2L);

        assertThat(snapshots)
                .extracting(RecurringTransactionRuleSnapshot::paymentAmount)
                .containsExactly(
                        new BigDecimal("-100.00"),
                        new BigDecimal("-200.00")
                );
    }

    @Test
    void shouldReturnEmptyListWhenHistoryRowsAreEmpty() {
        assertThat(mapper.toSnapshots(List.of())).isEmpty();
    }

    @Test
    void shouldRejectNullHistoryRows() {
        assertThatThrownBy(() -> mapper.toSnapshots(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("historyRows");
    }

    @Test
    void shouldRejectNullHistoryRowInsideList() {
        assertThatThrownBy(() -> mapper.toSnapshots(Arrays.asList(
                history(
                        LocalDate.of(2026, 1, 1),
                        null,
                        (short) 1,
                        (short) 1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NONE,
                        "-100.00",
                        null,
                        null
                ),
                null
        )))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("history");
    }

    @Test
    void shouldNotSortHistoryRowsByEffectiveFrom() {
        RecurringTransactionHistory firstInserted = history(
                LocalDate.of(2026, 3, 1),
                null,
                (short) 1,
                (short) 1,
                RecurrenceUnit.MONTH,
                PaymentDateAdjustmentPolicy.NONE,
                "-300.00",
                null,
                null
        );

        RecurringTransactionHistory laterInsertedHistoricalCorrection = history(
                LocalDate.of(2026, 1, 1),
                null,
                (short) 1,
                (short) 1,
                RecurrenceUnit.MONTH,
                PaymentDateAdjustmentPolicy.NONE,
                "-100.00",
                null,
                null
        );

        List<RecurringTransactionRuleSnapshot> snapshots =
                mapper.toSnapshots(List.of(
                        firstInserted,
                        laterInsertedHistoricalCorrection
                ));

        assertThat(snapshots)
                .extracting(RecurringTransactionRuleSnapshot::effectiveFrom)
                .containsExactly(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 1, 1)
                );

        assertThat(snapshots)
                .extracting(RecurringTransactionRuleSnapshot::precedence)
                .containsExactly(1L, 2L);
    }

    @Test
    void shouldPreserveNullableFieldsAsNull() {
        RecurringTransactionHistory history = history(
                LocalDate.of(2026, 1, 1),
                null,
                (short) 1,
                (short) 1,
                RecurrenceUnit.MONTH,
                PaymentDateAdjustmentPolicy.NONE,
                "-100.00",
                null,
                null
        );

        List<RecurringTransactionRuleSnapshot> snapshots =
                mapper.toSnapshots(List.of(history));

        RecurringTransactionRuleSnapshot snapshot = snapshots.getFirst();

        assertThat(snapshot.effectiveTo()).isNull();
        assertThat(snapshot.recurringTransactionEndDate()).isNull();
        assertThat(snapshot.finalPaymentAmount()).isNull();
    }
}