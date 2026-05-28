package me.serenityline.api.finance.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinanceCalendarSearchRequest(
        LocalDate from,
        LocalDate to,
        List<UUID> accountIds,
        List<UUID> simulationGroupIds
) {
}