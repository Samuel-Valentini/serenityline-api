package me.serenityline.api.finance.report;

import java.time.LocalDate;
import java.util.List;

public record FinanceReportSummary(
        LocalDate asOfDate,
        FinanceReportProjectionMode projectionMode,
        FinanceReportRange extremesRange,
        int yearEndForecastYears,
        List<FinanceRecurringReportSummary> recurringByCurrency,
        List<FinanceReportExtremesByCurrency> extremesByCurrency,
        List<FinanceYearEndForecast> yearEndForecasts
) {
}