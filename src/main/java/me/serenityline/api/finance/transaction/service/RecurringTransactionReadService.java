package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionResponse;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class RecurringTransactionReadService {

    private final UserRepository userRepository;
    private final RecurringTransactionAccessService recurringTransactionAccessService;
    private final RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;

    public RecurringTransactionReadService(
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
    public RecurringTransactionResponse getRecurringTransaction(
            UUID currentUserId,
            UUID recurringTransactionId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        RecurringTransaction recurringTransaction = recurringTransactionAccessService.findReadableRecurringTransaction(
                currentUser,
                userGroupId,
                recurringTransactionId
        );

        RecurringTransactionHistory currentHistory = recurringTransactionHistoryRepository
                .findFirstByRecurringTransaction_RecurringTransactionIdAndEffectiveToIsNullOrderByEffectiveFromDescRecurringTransactionHistoryCreatedAtDesc(
                        recurringTransaction.getRecurringTransactionId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.historyNotFound"));

        RecurringTransactionDetailsHistory currentDetails = recurringTransactionDetailsHistoryRepository
                .findFirstByRecurringTransaction_RecurringTransactionIdAndUserGroup_UserGroupIdOrderByRecurringTransactionDetailsEffectiveFromDescRecurringTransactionDetailsHistoryCreatedAtDesc(
                        recurringTransaction.getRecurringTransactionId(),
                        userGroupId
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.detailsNotFound"));

        return RecurringTransactionResponse.from(
                recurringTransaction,
                currentHistory,
                currentDetails
        );
    }
}