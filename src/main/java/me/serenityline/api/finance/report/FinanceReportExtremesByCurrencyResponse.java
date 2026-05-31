package me.serenityline.api.finance.report;

import java.time.LocalDate;

public record FinanceReportExtremesByCurrencyResponse(
        String currency,
        LocalDate asOfDate,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        FinanceReportPoint minSerenityline,
        FinanceReportPoint maxSerenityline,
        FinanceReportPoint minAccountBalance,
        FinanceReportPoint maxAccountBalance
) {
    public static FinanceReportExtremesByCurrencyResponse from(FinanceReportExtremesByCurrency extremes) {
        return new FinanceReportExtremesByCurrencyResponse(
                extremes.currency(),
                extremes.asOfDate(),
                extremes.rangeFrom(),
                extremes.rangeTo(),
                extremes.minSerenityline(),
                extremes.maxSerenityline(),
                extremes.minAccountBalance(),
                extremes.maxAccountBalance()
        );
    }
}