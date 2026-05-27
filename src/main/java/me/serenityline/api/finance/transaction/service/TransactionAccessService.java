package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionAccessService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionAccessService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository
    ) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    }

    public Transaction findReadableTransaction(
            User currentUser,
            UUID userGroupId,
            UUID transactionId
    ) {
        Objects.requireNonNull(currentUser, "currentUser");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(transactionId, "transactionId");

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

    public Transaction findOperableTransaction(
            User currentUser,
            UUID userGroupId,
            UUID transactionId
    ) {
        Objects.requireNonNull(currentUser, "currentUser");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(transactionId, "transactionId");

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

    public boolean canReadAllGroupTransactions(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR
                || user.getUserRole() == UserRole.VIEWER_COLLABORATOR;
    }

    public boolean canOperateOnAllAccounts(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR;
    }

    public Account findOperableAccount(
            User currentUser,
            UUID userGroupId,
            UUID accountId
    ) {
        Objects.requireNonNull(currentUser, "currentUser");
        Objects.requireNonNull(userGroupId, "userGroupId");

        if (accountId == null) {
            throw new IllegalArgumentException("finance.transaction.accountRequired");
        }

        if (canOperateOnAllAccounts(currentUser)) {
            return accountRepository.findByAccountIdAndUserGroup_UserGroupId(accountId, userGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
        }

        if (currentUser.getUserRole() == UserRole.VIEWER_COLLABORATOR) {
            Account account = accountRepository.findByAccountIdAndUserGroup_UserGroupId(accountId, userGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));

            accountRepository.findVisibleAccountForLinkedUser(
                    accountId,
                    userGroupId,
                    currentUser.getUserId()
            ).orElseThrow(() -> new AccessDeniedException("finance.account.operationNotAllowed"));

            return account;
        }

        return accountRepository.findVisibleAccountForLinkedUser(
                accountId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
    }
}