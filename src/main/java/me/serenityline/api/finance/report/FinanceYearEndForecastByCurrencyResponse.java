package me.serenityline.api.finance.report;

import java.math.BigDecimal;

public record FinanceYearEndForecastByCurrencyResponse(
        String currency,
        BigDecimal endOfYearAccountBalance,
        BigDecimal endOfYearSerenityline
) {
    public static FinanceYearEndForecastByCurrencyResponse from(FinanceYearEndForecastByCurrency forecast) {
        return new FinanceYearEndForecastByCurrencyResponse(
                forecast.currency(),
                forecast.endOfYearAccountBalance(),
                forecast.endOfYearSerenityline()
        );
    }
}