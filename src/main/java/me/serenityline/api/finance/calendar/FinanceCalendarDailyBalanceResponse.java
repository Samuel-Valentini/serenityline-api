package me.serenityline.api.finance.calendar;

import java.time.LocalDate;
import java.util.List;

public record FinanceCalendarDailyBalanceResponse(
        LocalDate date,
        List<FinanceCalendarAccountDailyBalanceResponse> accounts,
        List<FinanceCalendarBucketDailyBalanceResponse> buckets,
        List<FinanceCalendarCurrencyDailyBalanceResponse> totalsByCurrency
) {

    public static FinanceCalendarDailyBalanceResponse from(
            FinanceCalendarDailyBalance dailyBalance
    ) {
        return new FinanceCalendarDailyBalanceResponse(
                dailyBalance.date(),
                dailyBalance.accounts()
                        .stream()
                        .map(FinanceCalendarAccountDailyBalanceResponse::from)
                        .toList(),
                dailyBalance.buckets()
                        .stream()
                        .map(FinanceCalendarBucketDailyBalanceResponse::from)
                        .toList(),
                dailyBalance.totalsByCurrency()
                        .stream()
                        .map(FinanceCalendarCurrencyDailyBalanceResponse::from)
                        .toList()
        );
    }
}