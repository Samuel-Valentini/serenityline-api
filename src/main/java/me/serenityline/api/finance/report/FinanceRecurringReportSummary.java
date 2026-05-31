package me.serenityline.api.finance.report;

import java.math.BigDecimal;

public record FinanceRecurringReportSummary(
        String currency,
        BigDecimal annualIncome,
        BigDecimal annualExpenses,
        BigDecimal annualNetBalance,
        BigDecimal averageMonthlyIncome,
        BigDecimal averageMonthlyExpenses,
        BigDecimal averageMonthlyNetBalance
) {
}