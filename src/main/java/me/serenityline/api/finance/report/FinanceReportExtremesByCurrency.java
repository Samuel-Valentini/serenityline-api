package me.serenityline.api.finance.report;

import java.time.LocalDate;

public record FinanceReportExtremesByCurrency(
        String currency,
        LocalDate asOfDate,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        FinanceReportPoint minSerenityline,
        FinanceReportPoint maxSerenityline,
        FinanceReportPoint minAccountBalance,
        FinanceReportPoint maxAccountBalance
) {
}