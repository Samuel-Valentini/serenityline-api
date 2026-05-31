package me.serenityline.api.finance.report;

import java.math.BigDecimal;

public record FinanceRecurringReportSummaryResponse(
        String currency,
        BigDecimal annualIncome,
        BigDecimal annualExpenses,
        BigDecimal annualNetBalance,
        BigDecimal averageMonthlyIncome,
        BigDecimal averageMonthlyExpenses,
        BigDecimal averageMonthlyNetBalance
) {
    public static FinanceRecurringReportSummaryResponse from(FinanceRecurringReportSummary summary) {
        return new FinanceRecurringReportSummaryResponse(
                summary.currency(),
                summary.annualIncome(),
                summary.annualExpenses(),
                summary.annualNetBalance(),
                summary.averageMonthlyIncome(),
                summary.averageMonthlyExpenses(),
                summary.averageMonthlyNetBalance()
        );
    }
}