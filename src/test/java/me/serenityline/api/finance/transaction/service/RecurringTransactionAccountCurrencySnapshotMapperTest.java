package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.calendar.PaymentAccountCurrencySnapshot;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecurringTransactionAccountCurrencySnapshotMapperTest {

    private final RecurringTransactionAccountCurrencySnapshotMapper mapper =
            new RecurringTransactionAccountCurrencySnapshotMapper();

    private static RecurringTransactionDetailsHistory details(
            LocalDate effectiveFrom,
            UUID accountId,
            String currencyCode
    ) {
        Account account = mock(Account.class);

        when(account.getAccountId()).thenReturn(accountId);
        when(account.getCurrency()).thenReturn(currencyCode);

        RecurringTransactionDetailsHistory details =
                mock(RecurringTransactionDetailsHistory.class);

        when(details.getRecurringTransactionDetailsEffectiveFrom())
                .thenReturn(effectiveFrom);

        when(details.getLinkedAccount())
                .thenReturn(account);

        return details;
    }

    @Test
    void shouldMapSingleDetailsHistoryRowToAccountCurrencySnapshot() {
        UUID accountId = UUID.randomUUID();

        RecurringTransactionDetailsHistory details = details(
                LocalDate.of(2026, 1, 10),
                accountId,
                "EUR"
        );

        List<PaymentAccountCurrencySnapshot> snapshots =
                mapper.toSnapshots(List.of(details));

        assertThat(snapshots).hasSize(1);

        PaymentAccountCurrencySnapshot snapshot = snapshots.getFirst();

        assertThat(snapshot.effectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(snapshot.accountId()).isEqualTo(accountId);
        assertThat(snapshot.currencyCode()).isEqualTo("EUR");
        assertThat(snapshot.precedence()).isEqualTo(1);
    }

    @Test
    void shouldAssignPrecedenceAccordingToInputOrder() {
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        RecurringTransactionDetailsHistory first = details(
                LocalDate.of(2026, 1, 1),
                firstAccountId,
                "EUR"
        );

        RecurringTransactionDetailsHistory second = details(
                LocalDate.of(2026, 7, 1),
                secondAccountId,
                "USD"
        );

        List<PaymentAccountCurrencySnapshot> snapshots =
                mapper.toSnapshots(List.of(first, second));

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::precedence)
                .containsExactly(1L, 2L);

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::accountId)
                .containsExactly(firstAccountId, secondAccountId);

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::currencyCode)
                .containsExactly("EUR", "USD");
    }

    @Test
    void shouldNotSortDetailsRowsByEffectiveFrom() {
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        RecurringTransactionDetailsHistory firstInserted = details(
                LocalDate.of(2026, 7, 1),
                firstAccountId,
                "USD"
        );

        RecurringTransactionDetailsHistory laterInsertedHistoricalCorrection = details(
                LocalDate.of(2026, 1, 1),
                secondAccountId,
                "EUR"
        );

        List<PaymentAccountCurrencySnapshot> snapshots =
                mapper.toSnapshots(List.of(
                        firstInserted,
                        laterInsertedHistoricalCorrection
                ));

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::effectiveFrom)
                .containsExactly(
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 1, 1)
                );

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::precedence)
                .containsExactly(1L, 2L);
    }

    @Test
    void shouldReturnEmptyListWhenDetailsHistoryRowsAreEmpty() {
        assertThat(mapper.toSnapshots(List.of())).isEmpty();
    }

    @Test
    void shouldRejectNullDetailsHistoryRows() {
        assertThatThrownBy(() -> mapper.toSnapshots(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("detailsHistoryRows");
    }

    @Test
    void shouldRejectNullDetailsRowInsideList() {
        assertThatThrownBy(() -> mapper.toSnapshots(Arrays.asList(
                details(
                        LocalDate.of(2026, 1, 1),
                        UUID.randomUUID(),
                        "EUR"
                ),
                null
        )))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("details");
    }

    @Test
    void shouldRejectNullLinkedAccount() {
        RecurringTransactionDetailsHistory details = mock(RecurringTransactionDetailsHistory.class);

        when(details.getLinkedAccount()).thenReturn(null);
        when(details.getRecurringTransactionDetailsEffectiveFrom())
                .thenReturn(LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> mapper.toSnapshots(List.of(details)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("linkedAccount");
    }

    @Test
    void shouldRejectBlankCurrencyCode() {
        RecurringTransactionDetailsHistory details = details(
                LocalDate.of(2026, 1, 1),
                UUID.randomUUID(),
                "   "
        );

        assertThatThrownBy(() -> mapper.toSnapshots(List.of(details)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.account.currencyRequired");
    }

    @Test
    void shouldNormalizeCurrencyCodeThroughSnapshot() {
        RecurringTransactionDetailsHistory details = details(
                LocalDate.of(2026, 1, 1),
                UUID.randomUUID(),
                " eur "
        );

        List<PaymentAccountCurrencySnapshot> snapshots =
                mapper.toSnapshots(List.of(details));

        assertThat(snapshots.getFirst().currencyCode()).isEqualTo("EUR");
    }

    @Test
    void shouldRejectNullEffectiveFrom() {
        RecurringTransactionDetailsHistory details = details(
                null,
                UUID.randomUUID(),
                "EUR"
        );

        assertThatThrownBy(() -> mapper.toSnapshots(List.of(details)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("effectiveFrom");
    }

    @Test
    void shouldRejectNullAccountId() {
        RecurringTransactionDetailsHistory details = details(
                LocalDate.of(2026, 1, 1),
                null,
                "EUR"
        );

        assertThatThrownBy(() -> mapper.toSnapshots(List.of(details)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId");
    }

    @Test
    void shouldRejectNullCurrencyCode() {
        RecurringTransactionDetailsHistory details = details(
                LocalDate.of(2026, 1, 1),
                UUID.randomUUID(),
                null
        );

        assertThatThrownBy(() -> mapper.toSnapshots(List.of(details)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.account.currencyRequired");
    }

    @Test
    void shouldPreserveInputOrderWhenRowsHaveSameEffectiveFrom() {
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        RecurringTransactionDetailsHistory first = details(
                LocalDate.of(2026, 1, 1),
                firstAccountId,
                "EUR"
        );

        RecurringTransactionDetailsHistory second = details(
                LocalDate.of(2026, 1, 1),
                secondAccountId,
                "USD"
        );

        List<PaymentAccountCurrencySnapshot> snapshots =
                mapper.toSnapshots(List.of(first, second));

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::effectiveFrom)
                .containsExactly(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 1)
                );

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::accountId)
                .containsExactly(firstAccountId, secondAccountId);

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::currencyCode)
                .containsExactly("EUR", "USD");

        assertThat(snapshots)
                .extracting(PaymentAccountCurrencySnapshot::precedence)
                .containsExactly(1L, 2L);
    }
}