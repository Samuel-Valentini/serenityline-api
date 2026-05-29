package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record FinanceCalendarMovement(
        FinanceCalendarMovementType type,
        UUID transactionId,
        UUID recurringTransactionId,
        LocalDate logicalDate,
        LocalDate chargeDate,
        String description,
        BigDecimal amount,
        boolean affectsAccountBalance,
        boolean affectsSerenityline,
        UUID categoryId,
        UUID financialPriorityId,
        UUID accountId,
        UUID creditCardId,
        UUID bucketId,
        boolean confirmed,
        boolean simulated,
        UUID simulationGroupId,
        boolean userEntered,
        boolean finalOccurrence
) {
    public FinanceCalendarMovement {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(logicalDate, "logicalDate");
        Objects.requireNonNull(chargeDate, "chargeDate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(accountId, "accountId");

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("finance.calendar.descriptionRequired");
        }

        description = description.trim();

        if (!affectsAccountBalance && !affectsSerenityline) {
            throw new IllegalArgumentException("finance.calendar.affectsSomethingRequired");
        }

        if (type == FinanceCalendarMovementType.PERSISTED_TRANSACTION
                && transactionId == null) {
            throw new IllegalArgumentException("finance.calendar.transactionIdRequired");
        }

        if (type == FinanceCalendarMovementType.PROJECTED_RECURRING_TRANSACTION
                && recurringTransactionId == null) {
            throw new IllegalArgumentException("finance.calendar.recurringTransactionIdRequired");
        }

        if (type == FinanceCalendarMovementType.PERSISTED_TRANSACTION
                && finalOccurrence) {
            throw new IllegalArgumentException("finance.calendar.finalOccurrenceNotAllowed");
        }

        if (!simulated && simulationGroupId != null) {
            throw new IllegalArgumentException("finance.calendar.simulationGroupNotAllowed");
        }

        if (simulated && simulationGroupId == null) {
            throw new IllegalArgumentException("finance.calendar.simulationGroupRequired");
        }
    }
}