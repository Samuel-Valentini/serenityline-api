package me.serenityline.api.finance.account.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountQueryService(
            AccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional(readOnly = true)
    public List<Account> findVisibleAccounts(UUID currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canSeeAllGroupAccounts(currentUser)) {
            return accountRepository.findAllByUserGroup_UserGroupIdOrderByAccountNameAsc(userGroupId);
        }

        return accountRepository.findAllVisibleToLinkedUser(
                userGroupId,
                currentUser.getUserId()
        );
    }

    @Transactional(readOnly = true)
    public Account findVisibleAccount(UUID currentUserId, UUID accountId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(accountId, "accountId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canSeeAllGroupAccounts(currentUser)) {
            return accountRepository.findByAccountIdAndUserGroup_UserGroupId(
                    accountId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
        }

        return accountRepository.findVisibleAccountForLinkedUser(
                accountId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
    }

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.account.notFound");
        }

        return currentUser;
    }

    private boolean canSeeAllGroupAccounts(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR
                || userRole == UserRole.VIEWER_COLLABORATOR;
    }
}