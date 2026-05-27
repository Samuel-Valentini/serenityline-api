package me.serenityline.api.finance.transaction.dto;

import java.util.List;
import java.util.UUID;

public record RecurringTransactionHistoryResponse(
        UUID recurringTransactionId,
        List<RecurringTransactionRuleHistoryItemResponse> ruleHistory,
        List<RecurringTransactionDetailsHistoryItemResponse> detailsHistory
) {
}