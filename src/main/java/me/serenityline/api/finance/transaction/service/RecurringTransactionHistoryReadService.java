package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionDetailsHistoryItemResponse;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionHistoryResponse;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionRuleHistoryItemResponse;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecurringTransactionHistoryReadService {

    private final UserRepository userRepository;
    private final RecurringTransactionAccessService recurringTransactionAccessService;
    private final RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;

    public RecurringTransactionHistoryReadService(
            UserRepository userRepository,
            RecurringTransactionAccessService recurringTransactionAccessService,
            RecurringTransactionHistoryRepository recurringTransactionHistoryRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.recurringTransactionAccessService = Objects.requireNonNull(
                recurringTransactionAccessService,
                "recurringTransactionAccessService"
        );
        this.recurringTransactionHistoryRepository = Objects.requireNonNull(
                recurringTransactionHistoryRepository,
                "recurringTransactionHistoryRepository"
        );
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(
                recurringTransactionDetailsHistoryRepository,
                "recurringTransactionDetailsHistoryRepository"
        );
    }

    @Transactional(readOnly = true)
    public RecurringTransactionHistoryResponse getRecurringTransactionHistory(
            UUID currentUserId,
            UUID recurringTransactionId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        RecurringTransaction recurringTransaction = recurringTransactionAccessService
                .findReadableRecurringTransaction(
                        currentUser,
                        userGroupId,
                        recurringTransactionId
                );

        if (!recurringTransactionAccessService.canReadAllGroupRecurringTransactions(currentUser)) {
            throw new AccessDeniedException("finance.recurringTransaction.historyOperationNotAllowed");
        }

        List<RecurringTransactionHistory> ruleHistory = recurringTransactionHistoryRepository
                .findAllHistoryByRecurringTransactionId(
                        recurringTransaction.getRecurringTransactionId()
                );

        if (ruleHistory.isEmpty()) {
            throw new ResourceNotFoundException("finance.recurringTransaction.historyNotFound");
        }

        List<RecurringTransactionDetailsHistory> detailsHistory = recurringTransactionDetailsHistoryRepository
                .findAllHistoryByRecurringTransactionIdAndUserGroupId(
                        recurringTransaction.getRecurringTransactionId(),
                        userGroupId
                );

        if (detailsHistory.isEmpty()) {
            throw new ResourceNotFoundException("finance.recurringTransaction.detailsNotFound");
        }

        return new RecurringTransactionHistoryResponse(
                recurringTransaction.getRecurringTransactionId(),
                ruleHistory.stream()
                        .map(RecurringTransactionRuleHistoryItemResponse::from)
                        .toList(),
                detailsHistory.stream()
                        .map(RecurringTransactionDetailsHistoryItemResponse::from)
                        .toList()
        );
    }
}