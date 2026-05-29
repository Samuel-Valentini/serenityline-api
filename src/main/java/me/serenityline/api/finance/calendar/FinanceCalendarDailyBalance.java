package me.serenityline.api.finance.calendar;

import java.time.LocalDate;
import java.util.List;

public record FinanceCalendarDailyBalance(
        LocalDate date,
        List<FinanceCalendarAccountDailyBalance> accounts,
        List<FinanceCalendarBucketDailyBalance> buckets,
        List<FinanceCalendarCurrencyDailyBalance> totalsByCurrency
) {
}