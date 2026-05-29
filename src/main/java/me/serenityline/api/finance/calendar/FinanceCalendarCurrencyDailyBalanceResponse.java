package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;

public record FinanceCalendarCurrencyDailyBalanceResponse(
        String currency,
        BigDecimal endOfDayAccountsBalance,
        BigDecimal endOfDaySerenityline,
        BigDecimal endOfDayBucketsBalance
) {

    public static FinanceCalendarCurrencyDailyBalanceResponse from(
            FinanceCalendarCurrencyDailyBalance currencyBalance
    ) {
        return new FinanceCalendarCurrencyDailyBalanceResponse(
                currencyBalance.currency(),
                currencyBalance.endOfDayAccountsBalance(),
                currencyBalance.endOfDaySerenityline(),
                currencyBalance.endOfDayBucketsBalance()
        );
    }
}