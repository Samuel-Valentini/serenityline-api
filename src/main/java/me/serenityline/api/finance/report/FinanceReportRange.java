package me.serenityline.api.finance.report;

import java.time.LocalDate;

public record FinanceReportRange(
        LocalDate from,
        LocalDate to
) {
}