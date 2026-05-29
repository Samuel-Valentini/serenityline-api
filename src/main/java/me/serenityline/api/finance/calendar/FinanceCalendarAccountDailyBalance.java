package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FinanceCalendarAccountDailyBalance(
        UUID accountId,
        String currency,
        BigDecimal endOfDayAccountBalance,
        BigDecimal endOfDaySerenityline,
        BigDecimal endOfDayBucketsBalance,
        List<FinanceCalendarAccountBucketDailyBalance> buckets
) {
}