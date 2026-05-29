package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;
import java.util.UUID;

public record FinanceCalendarBucketDailyBalanceResponse(
        UUID bucketId,
        String currency,
        BigDecimal endOfDayBucketBalance
) {

    public static FinanceCalendarBucketDailyBalanceResponse from(
            FinanceCalendarBucketDailyBalance bucketBalance
    ) {
        return new FinanceCalendarBucketDailyBalanceResponse(
                bucketBalance.bucketId(),
                bucketBalance.currency(),
                bucketBalance.endOfDayBucketBalance()
        );
    }
}