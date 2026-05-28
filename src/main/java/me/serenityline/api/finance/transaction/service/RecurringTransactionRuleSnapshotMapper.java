package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RecurringTransactionRuleSnapshotMapper {

    public List<RecurringTransactionRuleSnapshot> toSnapshots(
            List<RecurringTransactionHistory> historyRows
    ) {
        Objects.requireNonNull(historyRows, "historyRows");

        AtomicLong precedence = new AtomicLong(1);

        return historyRows.stream()
                .map(history -> toSnapshot(
                        history,
                        precedence.getAndIncrement()
                ))
                .toList();
    }

    private RecurringTransactionRuleSnapshot toSnapshot(
            RecurringTransactionHistory history,
            long precedence
    ) {
        Objects.requireNonNull(history, "history");

        return new RecurringTransactionRuleSnapshot(
                history.getEffectiveFrom(),
                history.getEffectiveTo(),
                history.getDayOfUnit(),
                history.getRecurrenceInterval(),
                history.getRecurrenceUnit(),
                history.getPaymentDateAdjustmentPolicy(),
                history.getPaymentAmount(),
                history.getRecurringTransactionEndDate(),
                history.getFinalPaymentAmount(),
                precedence
        );
    }
}