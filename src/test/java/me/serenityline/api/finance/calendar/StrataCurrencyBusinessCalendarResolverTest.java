package me.serenityline.api.finance.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StrataCurrencyBusinessCalendarResolverTest {

    private final StrataCurrencyBusinessCalendarResolver resolver =
            new StrataCurrencyBusinessCalendarResolver();

    @Test
    void shouldResolveEuroCurrencyToTargetCalendarIgnoringCaseAndSpaces() {
        BusinessCalendar calendar = resolver.resolveByCurrency(" eur ");

        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void shouldCacheKnownCurrencyCalendarInstances() {
        BusinessCalendar first = resolver.resolveByCurrency("EUR");
        BusinessCalendar second = resolver.resolveByCurrency("eur");

        assertThat(second).isSameAs(first);
    }

    @Test
    void shouldFallbackToWeekendOnlyCalendarForNullCurrency() {
        BusinessCalendar calendar = resolver.resolveByCurrency(null);

        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 1, 2));

        assertThat(calendar.nextOrSame(LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void shouldFallbackToWeekendOnlyCalendarForBlankCurrency() {
        BusinessCalendar calendar = resolver.resolveByCurrency("   ");

        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 1, 2));

        assertThat(calendar.nextOrSame(LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void shouldFallbackToWeekendOnlyCalendarForUnknownCurrency() {
        BusinessCalendar calendar = resolver.resolveByCurrency("UNKNOWN");

        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 1, 1));

        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @Test
    void providerForCurrencyShouldUseResolvedCalendar() {
        PaymentBusinessCalendarProvider provider = resolver.providerForCurrency("EUR");

        BusinessCalendar calendar = provider.calendarAt(LocalDate.of(2026, 1, 1));

        assertThat(provider.adjustmentWindowDays()).isGreaterThanOrEqualTo(14);
        assertThat(calendar.previousOrSame(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void providerForCurrencyAtShouldResolveCurrencyForEachLogicalDate() {
        PaymentBusinessCalendarProvider provider = resolver.providerForCurrencyAt(logicalDate -> {
            if (logicalDate.isBefore(LocalDate.of(2026, 2, 1))) {
                return "EUR";
            }

            return "UNKNOWN";
        });

        assertThat(provider.calendarAt(LocalDate.of(2026, 1, 1))
                .previousOrSame(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2025, 12, 31));

        assertThat(provider.calendarAt(LocalDate.of(2026, 2, 2))
                .previousOrSame(LocalDate.of(2026, 2, 2)))
                .isEqualTo(LocalDate.of(2026, 2, 2));
    }

    @Test
    void providerForCurrencyAtShouldCallCurrencyResolverLazilyForEachDate() {
        AtomicInteger calls = new AtomicInteger();

        PaymentBusinessCalendarProvider provider = resolver.providerForCurrencyAt(logicalDate -> {
            calls.incrementAndGet();
            return "UNKNOWN";
        });

        provider.calendarAt(LocalDate.of(2026, 1, 1));
        provider.calendarAt(LocalDate.of(2026, 1, 2));
        provider.calendarAt(LocalDate.of(2026, 1, 3));

        assertThat(calls).hasValue(3);
    }
}