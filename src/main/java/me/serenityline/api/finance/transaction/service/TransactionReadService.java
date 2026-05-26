package me.serenityline.api.finance.transaction.service;


import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.dto.TransactionSearchRequest;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
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

    public TransactionReadService(
            UserRepository userRepository,
            TransactionRepository transactionRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
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

        Transaction transaction = findReadableTransaction(
                currentUser,
                transactionId,
                userGroupId
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

        if (canReadAllGroupTransactions(currentUser)) {
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

    private Transaction findReadableTransaction(
            User currentUser,
            UUID transactionId,
            UUID userGroupId
    ) {
        if (canReadAllGroupTransactions(currentUser)) {
            return transactionRepository.findByTransactionIdAndUserGroup_UserGroupId(
                    transactionId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.transaction.notFound"));
        }

        return transactionRepository.findByTransactionIdAndLinkedUserAccess(
                transactionId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.transaction.notFound"));
    }

    private boolean canReadAllGroupTransactions(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR
                || user.getUserRole() == UserRole.VIEWER_COLLABORATOR;
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