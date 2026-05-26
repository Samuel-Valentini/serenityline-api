package me.serenityline.api.finance.transaction.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TransactionSearchRequest(
        LocalDate from,
        LocalDate to,
        UUID accountId,
        UUID simulationGroupId
) {
}