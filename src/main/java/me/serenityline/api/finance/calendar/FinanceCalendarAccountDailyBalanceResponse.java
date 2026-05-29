package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FinanceCalendarAccountDailyBalanceResponse(
        UUID accountId,
        String currency,
        BigDecimal endOfDayAccountBalance,
        BigDecimal endOfDaySerenityline,
        BigDecimal endOfDayBucketsBalance,
        List<FinanceCalendarAccountBucketDailyBalanceResponse> buckets
) {

    public static FinanceCalendarAccountDailyBalanceResponse from(
            FinanceCalendarAccountDailyBalance accountBalance
    ) {
        return new FinanceCalendarAccountDailyBalanceResponse(
                accountBalance.accountId(),
                accountBalance.currency(),
                accountBalance.endOfDayAccountBalance(),
                accountBalance.endOfDaySerenityline(),
                accountBalance.endOfDayBucketsBalance(),
                accountBalance.buckets()
                        .stream()
                        .map(FinanceCalendarAccountBucketDailyBalanceResponse::from)
                        .toList()
        );
    }
}