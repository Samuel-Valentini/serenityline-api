package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionDeletionService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionAccessService transactionAccessService;

    public TransactionDeletionService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            TransactionAccessService transactionAccessService
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.transactionAccessService = Objects.requireNonNull(transactionAccessService, "transactionAccessService");
    }

    @Transactional
    public void deleteTransaction(
            UUID currentUserId,
            UUID transactionId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(transactionId, "transactionId");

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Transaction transaction = transactionAccessService.findOperableTransaction(
                currentUser,
                userGroupId,
                transactionId
        );

        transactionRepository.delete(transaction);
        transactionRepository.flush();
    }
}