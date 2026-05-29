package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
public class RecurringTransactionProjectedMovementAssembler {

    public List<RecurringTransactionProjectedMovement> assemble(
            List<RecurringTransactionOccurrence> occurrences,
            List<RecurringTransactionDetailsHistory> detailsHistoryRows
    ) {
        Objects.requireNonNull(occurrences, "occurrences");
        Objects.requireNonNull(detailsHistoryRows, "detailsHistoryRows");

        if (occurrences.isEmpty()) {
            return List.of();
        }

        if (detailsHistoryRows.isEmpty()) {
            throw new IllegalStateException("finance.recurringTransaction.detailsHistoryRequired");
        }

        return occurrences.stream()
                .map(occurrence -> toProjectedMovement(
                        occurrence,
                        findDetailsActiveAt(
                                detailsHistoryRows,
                                occurrence.logicalDate()
                        )
                ))
                .toList();
    }

    private RecurringTransactionDetailsHistory findDetailsActiveAt(
            List<RecurringTransactionDetailsHistory> detailsHistoryRows,
            LocalDate logicalDate
    ) {
        Objects.requireNonNull(logicalDate, "logicalDate");

        RecurringTransactionDetailsHistory activeDetails = null;

        for (RecurringTransactionDetailsHistory details : detailsHistoryRows) {
            Objects.requireNonNull(details, "details");

            LocalDate effectiveFrom = Objects.requireNonNull(
                    details.getRecurringTransactionDetailsEffectiveFrom(),
                    "recurringTransactionDetailsEffectiveFrom"
            );

            if (!effectiveFrom.isAfter(logicalDate)) {
                activeDetails = details;
            }
        }

        if (activeDetails == null) {
            throw new IllegalStateException("finance.recurringTransaction.detailsNotFound");
        }

        return activeDetails;
    }

    private RecurringTransactionProjectedMovement toProjectedMovement(
            RecurringTransactionOccurrence occurrence,
            RecurringTransactionDetailsHistory details
    ) {
        Objects.requireNonNull(occurrence, "occurrence");
        Objects.requireNonNull(details, "details");

        return new RecurringTransactionProjectedMovement(
                occurrence.recurringTransactionId(),
                occurrence.logicalDate(),
                occurrence.chargeDate(),
                occurrence.amount(),
                occurrence.finalOccurrence(),
                details.getRecurringTransactionDescription(),
                details.getCategory(),
                details.getFinancialPriority(),
                details.getLinkedAccount(),
                details.getLinkedCreditCard(),
                details.getLinkedBucket(),
                details.isRecurringTransactionAffectsAccountBalance(),
                details.isRecurringTransactionAffectsSerenityline()
        );
    }
}