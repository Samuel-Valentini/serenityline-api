package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;
import java.util.UUID;

public record FinanceCalendarAccountBucketDailyBalanceResponse(
        UUID bucketId,
        BigDecimal endOfDayBucketBalance
) {

    public static FinanceCalendarAccountBucketDailyBalanceResponse from(
            FinanceCalendarAccountBucketDailyBalance bucketBalance
    ) {
        return new FinanceCalendarAccountBucketDailyBalanceResponse(
                bucketBalance.bucketId(),
                bucketBalance.endOfDayBucketBalance()
        );
    }
}