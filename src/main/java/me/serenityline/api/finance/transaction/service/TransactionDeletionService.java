package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionDeletionService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionDeletionService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
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

        Transaction transaction = findDeletableTransaction(
                currentUser,
                userGroupId,
                transactionId
        );

        transactionRepository.delete(transaction);
        transactionRepository.flush();
    }

    private Transaction findDeletableTransaction(
            User currentUser,
            UUID userGroupId,
            UUID transactionId
    ) {
        if (canOperateOnAllAccounts(currentUser)) {
            return transactionRepository.findByTransactionIdAndUserGroup_UserGroupId(
                    transactionId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.transaction.notFound"));
        }

        if (currentUser.getUserRole() == UserRole.VIEWER_COLLABORATOR) {
            Transaction transaction = transactionRepository.findByTransactionIdAndUserGroup_UserGroupId(
                    transactionId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.transaction.notFound"));

            accountRepository.findVisibleAccountForLinkedUser(
                    transaction.getAccount().getAccountId(),
                    userGroupId,
                    currentUser.getUserId()
            ).orElseThrow(() -> new AccessDeniedException("finance.account.operationNotAllowed"));

            return transaction;
        }

        return transactionRepository.findByTransactionIdAndLinkedUserAccess(
                transactionId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.transaction.notFound"));
    }

    private boolean canOperateOnAllAccounts(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR;
    }
}