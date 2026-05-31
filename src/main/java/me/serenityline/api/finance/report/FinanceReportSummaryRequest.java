package me.serenityline.api.finance.report;

import java.util.List;
import java.util.UUID;

public record FinanceReportSummaryRequest(
        List<UUID> accountIds,
        List<UUID> simulationGroupIds
) {
}