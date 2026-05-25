package me.serenityline.api.finance.account.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.entity.AccountUser;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class AccountAccessService {

    private final AccountRepository accountRepository;
    private final AccountUserRepository accountUserRepository;
    private final UserRepository userRepository;

    public AccountAccessService(
            AccountRepository accountRepository,
            AccountUserRepository accountUserRepository,
            UserRepository userRepository
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.accountUserRepository = Objects.requireNonNull(accountUserRepository, "accountUserRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public void grantAccountAccess(UUID currentUserId, UUID accountId, UUID targetUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(targetUserId, "targetUserId");

        User currentUser = findCurrentUser(currentUserId);
        Account account = findManageableAccount(currentUser, accountId);
        User targetUser = findTargetUserInSameGroup(
                targetUserId,
                currentUser.getUserGroup().getUserGroupId()
        );

        if (targetUser.getUserRole() == UserRole.OWNER) {
            return;
        }

        if (accountUserRepository.existsByAccount_AccountIdAndUser_UserId(
                account.getAccountId(),
                targetUser.getUserId()
        )) {
            return;
        }

        try {
            accountUserRepository.saveAndFlush(AccountUser.grant(
                    account,
                    targetUser
            ));
        } catch (DataIntegrityViolationException exception) {
            if (accountUserRepository.existsByAccount_AccountIdAndUser_UserId(
                    account.getAccountId(),
                    targetUser.getUserId()
            )) {
                return;
            }

            throw exception;
        }
    }

    @Transactional
    public void revokeAccountAccess(UUID currentUserId, UUID accountId, UUID targetUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(targetUserId, "targetUserId");

        User currentUser = findCurrentUser(currentUserId);
        Account account = findManageableAccount(currentUser, accountId);
        User targetUser = findTargetUserInSameGroup(
                targetUserId,
                currentUser.getUserGroup().getUserGroupId()
        );

        if (targetUser.getUserRole() == UserRole.OWNER) {
            throw new IllegalStateException("finance.accountAccess.ownerCannotBeRemoved");
        }

        accountUserRepository.findByAccount_AccountIdAndUser_UserId(
                account.getAccountId(),
                targetUser.getUserId()
        ).ifPresent(accountUserRepository::delete);
    }

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.account.notFound");
        }

        return currentUser;
    }

    private Account findManageableAccount(User currentUser, UUID accountId) {
        if (!canManageAccountAccess(currentUser)) {
            throw new IllegalStateException("finance.accountAccess.manageForbidden");
        }

        return accountRepository.findByAccountIdAndUserGroup_UserGroupId(
                accountId,
                currentUser.getUserGroup().getUserGroupId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
    }

    private User findTargetUserInSameGroup(UUID targetUserId, UUID userGroupId) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.accountAccess.targetUserNotFound"));

        if (!targetUser.getUserGroup().getUserGroupId().equals(userGroupId)) {
            throw new ResourceNotFoundException("finance.accountAccess.targetUserNotFound");
        }

        if (!targetUser.isUserIsEnabled() || targetUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.accountAccess.targetUserNotFound");
        }

        return targetUser;
    }

    private boolean canManageAccountAccess(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR;
    }
}