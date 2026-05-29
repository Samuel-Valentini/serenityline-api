package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;
import java.util.UUID;

public record FinanceCalendarAccountBucketDailyBalance(
        UUID bucketId,
        BigDecimal endOfDayBucketBalance
) {
}