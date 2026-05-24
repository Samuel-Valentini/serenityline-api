package me.serenityline.api.finance.account.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccountUpdateService {

    private static final int MAX_ACCOUNT_NAME_LENGTH = 255;
    private static final int MAX_ACCOUNT_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_ISSUING_INSTITUTION_LENGTH = 255;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountUpdateService(
            AccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public Account updateAccount(UUID currentUserId, UUID accountId, UpdateAccountCommand command) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(command, "command");

        User currentUser = findCurrentUser(currentUserId);
        Account account = findUpdatableAccount(currentUser, accountId);

        String accountName = account.getAccountName();

        if (command.accountName() != null) {
            accountName = normalizeAccountNameForStorage(command.accountName());
            String normalizedAccountName = normalizeAccountNameForComparison(accountName);

            if (accountRepository.existsByUserGroupIdAndNormalizedAccountNameExcludingAccountId(
                    currentUser.getUserGroup().getUserGroupId(),
                    normalizedAccountName,
                    account.getAccountId()
            )) {
                throw new IllegalStateException("finance.account.nameAlreadyExists");
            }
        }

        String accountDescription = account.getAccountDescription();

        if (command.accountDescription() != null) {
            accountDescription = normalizeOptionalText(
                    command.accountDescription(),
                    MAX_ACCOUNT_DESCRIPTION_LENGTH,
                    "finance.account.description.tooLong"
            );
        }

        String issuingInstitution = account.getIssuingInstitution();

        if (command.issuingInstitution() != null) {
            issuingInstitution = normalizeOptionalText(
                    command.issuingInstitution(),
                    MAX_ISSUING_INSTITUTION_LENGTH,
                    "finance.account.issuingInstitution.tooLong"
            );
        }

        BigDecimal openingBalance = account.getOpeningBalance();

        if (command.openingBalance() != null) {
            openingBalance = normalizeOpeningBalance(command.openingBalance());
        }

        LocalDate openingBalanceDate = account.getOpeningBalanceDate();

        if (command.openingBalanceDate() != null) {
            openingBalanceDate = command.openingBalanceDate();
        }

        account.update(
                accountName,
                accountDescription,
                issuingInstitution,
                openingBalance,
                openingBalanceDate
        );

        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("finance.account.nameAlreadyExists", exception);
        }
    }

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.account.notFound");
        }

        return currentUser;
    }

    private Account findUpdatableAccount(User currentUser, UUID accountId) {
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canUpdateAllGroupAccounts(currentUser)) {
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

    private boolean canUpdateAllGroupAccounts(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR;
    }

    private String normalizeAccountNameForStorage(String value) {
        String normalized = normalizeRequiredText(
                value,
                "finance.account.name.required"
        );

        validateMaxLength(
                normalized,
                MAX_ACCOUNT_NAME_LENGTH,
                "finance.account.name.tooLong"
        );

        return normalized;
    }

    private String normalizeAccountNameForComparison(String value) {
        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value, int maxLength, String tooLongMessageKey) {
        String normalized = value.trim();

        if (normalized.isBlank()) {
            return null;
        }

        validateMaxLength(normalized, maxLength, tooLongMessageKey);

        return normalized;
    }

    private String normalizeRequiredText(String value, String requiredMessageKey) {
        if (value == null) {
            throw new IllegalArgumentException(requiredMessageKey);
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(requiredMessageKey);
        }

        return normalized;
    }

    private void validateMaxLength(String value, int maxLength, String tooLongMessageKey) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(tooLongMessageKey);
        }
    }

    private BigDecimal normalizeOpeningBalance(BigDecimal value) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("finance.account.openingBalance.invalidScale", exception);
        }
    }
}