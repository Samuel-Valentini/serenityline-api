package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.calendar.PaymentAccountCurrencySnapshot;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RecurringTransactionAccountCurrencySnapshotMapper {

    public List<PaymentAccountCurrencySnapshot> toSnapshots(
            List<RecurringTransactionDetailsHistory> detailsHistoryRows
    ) {
        Objects.requireNonNull(detailsHistoryRows, "detailsHistoryRows");

        AtomicLong precedence = new AtomicLong(1);

        return detailsHistoryRows.stream()
                .map(details -> toSnapshot(
                        details,
                        precedence.getAndIncrement()
                ))
                .toList();
    }

    private PaymentAccountCurrencySnapshot toSnapshot(
            RecurringTransactionDetailsHistory details,
            long precedence
    ) {
        Objects.requireNonNull(details, "details");

        Account linkedAccount = Objects.requireNonNull(
                details.getLinkedAccount(),
                "linkedAccount"
        );

        return new PaymentAccountCurrencySnapshot(
                details.getRecurringTransactionDetailsEffectiveFrom(),
                linkedAccount.getAccountId(),
                linkedAccount.getCurrency(),
                precedence
        );
    }
}