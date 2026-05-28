package me.serenityline.api.finance.calendar;

import java.time.LocalDate;
import java.util.*;

public final class AccountCurrencyPaymentBusinessCalendarProvider implements PaymentBusinessCalendarProvider {

    private final List<PaymentAccountCurrencySnapshot> sortedSnapshots;
    private final CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver;

    public AccountCurrencyPaymentBusinessCalendarProvider(
            List<PaymentAccountCurrencySnapshot> snapshots,
            CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver
    ) {
        Objects.requireNonNull(snapshots, "snapshots");
        this.currencyBusinessCalendarResolver = Objects.requireNonNull(
                currencyBusinessCalendarResolver,
                "currencyBusinessCalendarResolver"
        );

        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("finance.paymentCalendar.snapshotsRequired");
        }

        this.sortedSnapshots = snapshots.stream()
                .sorted(Comparator.comparingLong(PaymentAccountCurrencySnapshot::precedence))
                .toList();

        validateUniquePrecedence(this.sortedSnapshots);
    }

    @Override
    public BusinessCalendar calendarAt(LocalDate logicalDate) {
        Objects.requireNonNull(logicalDate, "logicalDate");

        PaymentAccountCurrencySnapshot activeSnapshot = null;

        for (PaymentAccountCurrencySnapshot snapshot : sortedSnapshots) {
            if (snapshot.isEffectiveAt(logicalDate)) {
                activeSnapshot = snapshot;
            }
        }

        if (activeSnapshot == null) {
            throw new IllegalStateException("finance.paymentCalendar.currencyNotFound");
        }

        return Objects.requireNonNull(
                currencyBusinessCalendarResolver.resolveByCurrency(activeSnapshot.currencyCode()),
                "currencyBusinessCalendarResolver.resolveByCurrency(currencyCode)"
        );
    }

    @Override
    public int adjustmentWindowDays() {
        return currencyBusinessCalendarResolver.adjustmentWindowDays();
    }

    private void validateUniquePrecedence(List<PaymentAccountCurrencySnapshot> sortedSnapshots) {
        Set<Long> seen = new HashSet<>();

        for (PaymentAccountCurrencySnapshot snapshot : sortedSnapshots) {
            if (!seen.add(snapshot.precedence())) {
                throw new IllegalArgumentException("finance.paymentCalendar.snapshotPrecedenceDuplicated");
            }
        }
    }
}