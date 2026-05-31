package me.serenityline.api.finance.report;

import java.time.LocalDate;

public record FinanceReportTrend(
        FinanceReportTrendDirection direction,
        LocalDate startedAt,
        LocalDate observedUntil,
        boolean monotonicUntilRangeEnd
) {
}