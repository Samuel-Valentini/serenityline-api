package me.serenityline.api.finance.report;

import java.math.BigDecimal;

public record FinanceYearEndForecastByCurrency(
        String currency,
        BigDecimal endOfYearAccountBalance,
        BigDecimal endOfYearSerenityline
) {
}