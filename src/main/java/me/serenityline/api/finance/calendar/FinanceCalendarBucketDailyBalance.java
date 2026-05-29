package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;
import java.util.UUID;

public record FinanceCalendarBucketDailyBalance(
        UUID bucketId,
        String currency,
        BigDecimal endOfDayBucketBalance
) {
}