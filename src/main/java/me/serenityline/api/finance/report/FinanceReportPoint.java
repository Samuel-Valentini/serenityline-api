package me.serenityline.api.finance.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceReportPoint(
        LocalDate date,
        BigDecimal value,
        FinanceReportTemporalPosition temporalPosition,
        FinanceReportExtremeClassification classification,
        FinanceReportTrend trend
) {
}