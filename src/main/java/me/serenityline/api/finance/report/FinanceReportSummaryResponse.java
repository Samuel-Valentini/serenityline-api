package me.serenityline.api.finance.report;

import java.time.LocalDate;
import java.util.List;

public record FinanceReportSummaryResponse(
        LocalDate asOfDate,
        FinanceReportProjectionMode projectionMode,
        FinanceReportRange extremesRange,
        int yearEndForecastYears,
        List<FinanceRecurringReportSummaryResponse> recurringByCurrency,
        List<FinanceReportExtremesByCurrencyResponse> extremesByCurrency,
        List<FinanceYearEndForecastResponse> yearEndForecasts
) {
    public static FinanceReportSummaryResponse from(FinanceReportSummary summary) {
        return new FinanceReportSummaryResponse(
                summary.asOfDate(),
                summary.projectionMode(),
                summary.extremesRange(),
                summary.yearEndForecastYears(),
                summary.recurringByCurrency()
                        .stream()
                        .map(FinanceRecurringReportSummaryResponse::from)
                        .toList(),
                summary.extremesByCurrency()
                        .stream()
                        .map(FinanceReportExtremesByCurrencyResponse::from)
                        .toList(),
                summary.yearEndForecasts()
                        .stream()
                        .map(FinanceYearEndForecastResponse::from)
                        .toList()
        );
    }
}