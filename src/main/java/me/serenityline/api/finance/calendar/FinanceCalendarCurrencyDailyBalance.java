package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;

public record FinanceCalendarCurrencyDailyBalance(
        String currency,
        BigDecimal endOfDayAccountsBalance,
        BigDecimal endOfDaySerenityline,
        BigDecimal endOfDayBucketsBalance
) {
}