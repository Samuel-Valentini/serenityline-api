package me.serenityline.api.finance.transaction.service;


import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.dto.TransactionSearchRequest;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionReadService {

    private static final int MAX_TRANSACTION_SEARCH_RANGE_DAYS = 1_830;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionAccessService transactionAccessService;

    public TransactionReadService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            TransactionAccessService transactionAccessService
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.transactionAccessService = Objects.requireNonNull(transactionAccessService, "transactionAccessService");
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(
            UUID currentUserId,
            UUID transactionId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(transactionId, "transactionId");

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Transaction transaction = transactionAccessService.findReadableTransaction(
                currentUser,
                userGroupId,
                transactionId
        );

        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(
            UUID currentUserId,
            TransactionSearchRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(request, "request");

        validateSearchRequest(request);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        List<Transaction> transactions;

        if (transactionAccessService.canReadAllGroupTransactions(currentUser)) {
            transactions = transactionRepository.findGroupTransactionsInRange(
                    userGroupId,
                    request.from(),
                    request.to(),
                    request.accountId(),
                    request.simulationGroupId()
            );
        } else {
            transactions = transactionRepository.findLinkedUserTransactionsInRange(
                    userGroupId,
                    currentUser.getUserId(),
                    request.from(),
                    request.to(),
                    request.accountId(),
                    request.simulationGroupId()
            );
        }

        return transactions.stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private void validateSearchRequest(TransactionSearchRequest request) {
        if (request.from() == null) {
            throw new IllegalArgumentException("finance.transaction.fromRequired");
        }

        if (request.to() == null) {
            throw new IllegalArgumentException("finance.transaction.toRequired");
        }

        if (request.from().isAfter(request.to())) {
            throw new IllegalArgumentException("finance.transaction.invalidDateRange");
        }

        if (request.from().plusDays(MAX_TRANSACTION_SEARCH_RANGE_DAYS).isBefore(request.to())) {
            throw new IllegalArgumentException("finance.transaction.dateRangeTooLarge");
        }
    }
}