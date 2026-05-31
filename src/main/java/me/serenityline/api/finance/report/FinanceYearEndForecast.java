package me.serenityline.api.finance.report;

import java.time.LocalDate;
import java.util.List;

public record FinanceYearEndForecast(
        int year,
        LocalDate date,
        List<FinanceYearEndForecastByCurrency> balancesByCurrency
) {
}