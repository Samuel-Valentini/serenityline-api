package me.serenityline.api.finance.report;

import java.time.LocalDate;
import java.util.List;

public record FinanceYearEndForecastResponse(
        int year,
        LocalDate date,
        List<FinanceYearEndForecastByCurrencyResponse> balancesByCurrency
) {
    public static FinanceYearEndForecastResponse from(FinanceYearEndForecast forecast) {
        return new FinanceYearEndForecastResponse(
                forecast.year(),
                forecast.date(),
                forecast.balancesByCurrency()
                        .stream()
                        .map(FinanceYearEndForecastByCurrencyResponse::from)
                        .toList()
        );
    }
}