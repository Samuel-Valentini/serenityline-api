package me.serenityline.api.finance.financialpriority.dto;

import java.util.UUID;

public record FinancialPriorityResponse(
        UUID financialPriorityId,
        String financialPriorityCode,
        String financialPriorityDisplayName,
        String financialPriorityDescription,
        short financialPriorityRanking
) {
}