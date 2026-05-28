package me.serenityline.api.finance.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountCurrencyPaymentBusinessCalendarProviderTest {

    private final BusinessCalendar eurCalendar = fixedCalendar(LocalDate.of(2026, 12, 24));
    private final BusinessCalendar usdCalendar = fixedCalendar(LocalDate.of(2026, 12, 23));
    private final BusinessCalendar fallbackCalendar = fixedCalendar(LocalDate.of(2026, 12, 22));

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

    private static BusinessCalendar fixedCalendar(LocalDate returnedDate) {
        return new BusinessCalendar() {
            @Override
            public LocalDate previousOrSame(LocalDate date) {
                return returnedDate;
            }

            @Override
            public LocalDate nextOrSame(LocalDate date) {
                return returnedDate;
            }
        };
    }

    @Test
    void shouldUseCurrencyActiveAtLogicalDate() {
        UUID accountId = UUID.randomUUID();

        AccountCurrencyPaymentBusinessCalendarProvider provider =
                new AccountCurrencyPaymentBusinessCalendarProvider(
                        List.of(
                                snapshot(LocalDate.of(2026, 1, 1), accountId, "EUR", 1),
                                snapshot(LocalDate.of(2026, 7, 1), accountId, "USD", 2)
                        ),
                        resolver(Map.of(
                                "EUR", eurCalendar,
                                "USD", usdCalendar
                        ))
                );

        assertThat(provider.calendarAt(LocalDate.of(2026, 6, 30)))
                .isSameAs(eurCalendar);

        assertThat(provider.calendarAt(LocalDate.of(2026, 7, 1)))
                .isSameAs(usdCalendar);
    }

    @Test
    void shouldUseHighestPrecedenceSnapshotWhenMultipleSnapshotsAreEffective() {
        UUID accountId = UUID.randomUUID();

        AccountCurrencyPaymentBusinessCalendarProvider provider =
                new AccountCurrencyPaymentBusinessCalendarProvider(
                        List.of(
                                snapshot(LocalDate.of(2026, 1, 1), accountId, "EUR", 1),
                                snapshot(LocalDate.of(2026, 1, 1), accountId, "USD", 2)
                        ),
                        resolver(Map.of(
                                "EUR", eurCalendar,
                                "USD", usdCalendar
                        ))
                );

        assertThat(provider.calendarAt(LocalDate.of(2026, 1, 1)))
                .isSameAs(usdCalendar);
    }

    @Test
    void shouldBeIndependentFromInputOrderBecausePrecedenceIsSortedInternally() {
        UUID accountId = UUID.randomUUID();

        AccountCurrencyPaymentBusinessCalendarProvider provider =
                new AccountCurrencyPaymentBusinessCalendarProvider(
                        List.of(
                                snapshot(LocalDate.of(2026, 7, 1), accountId, "USD", 2),
                                snapshot(LocalDate.of(2026, 1, 1), accountId, "EUR", 1)
                        ),
                        resolver(Map.of(
                                "EUR", eurCalendar,
                                "USD", usdCalendar
                        ))
                );

        assertThat(provider.calendarAt(LocalDate.of(2026, 6, 30)))
                .isSameAs(eurCalendar);

        assertThat(provider.calendarAt(LocalDate.of(2026, 7, 1)))
                .isSameAs(usdCalendar);
    }

    @Test
    void shouldRejectDuplicatedSnapshotPrecedence() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> new AccountCurrencyPaymentBusinessCalendarProvider(
                List.of(
                        snapshot(LocalDate.of(2026, 1, 1), accountId, "EUR", 1),
                        snapshot(LocalDate.of(2026, 7, 1), accountId, "USD", 1)
                ),
                resolver(Map.of(
                        "EUR", eurCalendar,
                        "USD", usdCalendar
                ))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.paymentCalendar.snapshotPrecedenceDuplicated");
    }

    @Test
    void shouldRejectEmptySnapshots() {
        assertThatThrownBy(() -> new AccountCurrencyPaymentBusinessCalendarProvider(
                List.of(),
                resolver(Map.of())
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.paymentCalendar.snapshotsRequired");
    }

    @Test
    void shouldRejectDateBeforeFirstSnapshot() {
        UUID accountId = UUID.randomUUID();

        AccountCurrencyPaymentBusinessCalendarProvider provider =
                new AccountCurrencyPaymentBusinessCalendarProvider(
                        List.of(snapshot(LocalDate.of(2026, 7, 1), accountId, "EUR", 1)),
                        resolver(Map.of("EUR", eurCalendar))
                );

        assertThatThrownBy(() -> provider.calendarAt(LocalDate.of(2026, 6, 30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.paymentCalendar.currencyNotFound");
    }

    @Test
    void shouldExposeResolverAdjustmentWindowDays() {
        UUID accountId = UUID.randomUUID();

        AccountCurrencyPaymentBusinessCalendarProvider provider =
                new AccountCurrencyPaymentBusinessCalendarProvider(
                        List.of(snapshot(LocalDate.of(2026, 1, 1), accountId, "EUR", 1)),
                        resolver(Map.of("EUR", eurCalendar), 21)
                );

        assertThat(provider.adjustmentWindowDays()).isEqualTo(21);
    }

    @Test
    void shouldRejectNullCalendarReturnedByResolver() {
        UUID accountId = UUID.randomUUID();

        AccountCurrencyPaymentBusinessCalendarProvider provider =
                new AccountCurrencyPaymentBusinessCalendarProvider(
                        List.of(snapshot(LocalDate.of(2026, 1, 1), accountId, "EUR", 1)),
                        new CurrencyBusinessCalendarResolver() {
                            @Override
                            public BusinessCalendar resolveByCurrency(String currencyCode) {
                                return null;
                            }

                            @Override
                            public int adjustmentWindowDays() {
                                return 14;
                            }
                        }
                );

        assertThatThrownBy(() -> provider.calendarAt(LocalDate.of(2026, 1, 1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currencyBusinessCalendarResolver.resolveByCurrency(currencyCode)");
    }

    @Test
    void shouldNormalizeCurrencyCodeInSnapshot() {
        UUID accountId = UUID.randomUUID();

        PaymentAccountCurrencySnapshot snapshot =
                snapshot(LocalDate.of(2026, 1, 1), accountId, " eur ", 1);

        assertThat(snapshot.currencyCode()).isEqualTo("EUR");
    }

    private CurrencyBusinessCalendarResolver resolver(Map<String, BusinessCalendar> calendarsByCurrency) {
        return resolver(calendarsByCurrency, 14);
    }

    private CurrencyBusinessCalendarResolver resolver(
            Map<String, BusinessCalendar> calendarsByCurrency,
            int adjustmentWindowDays
    ) {
        return new CurrencyBusinessCalendarResolver() {
            @Override
            public BusinessCalendar resolveByCurrency(String currencyCode) {
                return calendarsByCurrency.getOrDefault(currencyCode, fallbackCalendar);
            }

            @Override
            public int adjustmentWindowDays() {
                return adjustmentWindowDays;
            }
        };
    }
}