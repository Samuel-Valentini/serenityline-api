package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class RecurringTransactionAccessService {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final JdbcTemplate jdbcTemplate;

    public RecurringTransactionAccessService(
            RecurringTransactionRepository recurringTransactionRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.recurringTransactionRepository = Objects.requireNonNull(
                recurringTransactionRepository,
                "recurringTransactionRepository"
        );
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public RecurringTransaction findReadableRecurringTransaction(
            User currentUser,
            UUID userGroupId,
            UUID recurringTransactionId
    ) {
        Objects.requireNonNull(currentUser, "currentUser");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");

        if (canReadAllGroupRecurringTransactions(currentUser)) {
            return recurringTransactionRepository.findByRecurringTransactionIdAndUserGroup_UserGroupId(
                    recurringTransactionId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.notFound"));
        }

        return recurringTransactionRepository.findReadableByLinkedUserAccess(
                recurringTransactionId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.notFound"));
    }

    public void assertReadableAccountFilter(
            User currentUser,
            UUID userGroupId,
            UUID accountId
    ) {
        Objects.requireNonNull(currentUser, "currentUser");
        Objects.requireNonNull(userGroupId, "userGroupId");

        if (accountId == null) {
            return;
        }

        if (canReadAllGroupRecurringTransactions(currentUser)) {
            if (!accountExistsInGroup(accountId, userGroupId)) {
                throw new ResourceNotFoundException("finance.account.notFound");
            }
            return;
        }

        if (!accountIsLinkedToUser(accountId, userGroupId, currentUser.getUserId())) {
            throw new ResourceNotFoundException("finance.account.notFound");
        }
    }

    public boolean canReadAllGroupRecurringTransactions(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR
                || user.getUserRole() == UserRole.VIEWER_COLLABORATOR;
    }

    private boolean accountExistsInGroup(
            UUID accountId,
            UUID userGroupId
    ) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM accounts
                        WHERE account_id = ?
                          AND user_group_id = ?
                        """,
                Long.class,
                accountId,
                userGroupId
        );

        return count != null && count > 0;
    }

    private boolean accountIsLinkedToUser(
            UUID accountId,
            UUID userGroupId,
            UUID userId
    ) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM accounts_users
                        WHERE account_id = ?
                          AND user_group_id = ?
                          AND user_id = ?
                        """,
                Long.class,
                accountId,
                userGroupId,
                userId
        );

        return count != null && count > 0;
    }
}