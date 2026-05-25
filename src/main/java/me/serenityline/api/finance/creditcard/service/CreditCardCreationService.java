package me.serenityline.api.finance.creditcard.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.creditcard.repository.CreditCardRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class CreditCardCreationService {

    private static final int MAX_CREDIT_CARD_NAME_LENGTH = 255;
    private static final int MAX_CREDIT_CARD_DESCRIPTION_LENGTH = 2000;

    private final CreditCardRepository creditCardRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public CreditCardCreationService(
            CreditCardRepository creditCardRepository,
            AccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this.creditCardRepository = Objects.requireNonNull(creditCardRepository, "creditCardRepository");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public CreditCard createCreditCard(UUID currentUserId, CreateCreditCardCommand command) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(command, "command");

        User currentUser = findCurrentUser(currentUserId);
        Account linkedAccount = findOperableAccount(currentUser, command.accountId());

        String creditCardName = normalizeCreditCardNameForStorage(command.creditCardName());
        String normalizedCreditCardName = normalizeCreditCardNameForComparison(creditCardName);

        if (creditCardRepository.existsByUserGroupIdAndNormalizedCreditCardName(
                currentUser.getUserGroup().getUserGroupId(),
                normalizedCreditCardName
        )) {
            throw new IllegalStateException("finance.creditCard.nameAlreadyExists");
        }

        String creditCardDescription = normalizeOptionalText(
                command.creditCardDescription(),
                MAX_CREDIT_CARD_DESCRIPTION_LENGTH,
                "finance.creditCard.description.tooLong"
        );

        short creditCardChargeDay = normalizeChargeDay(command.creditCardChargeDay());

        try {
            return creditCardRepository.saveAndFlush(CreditCard.create(
                    creditCardName,
                    creditCardDescription,
                    creditCardChargeDay,
                    linkedAccount,
                    currentUser.getUserGroup()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("finance.creditCard.nameAlreadyExists", exception);
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

    private Account findOperableAccount(User currentUser, UUID accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("finance.creditCard.account.required");
        }

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canOperateAllGroupAccounts(currentUser)) {
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

    private boolean canOperateAllGroupAccounts(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR;
    }

    private String normalizeCreditCardNameForStorage(String value) {
        String normalized = normalizeRequiredText(
                value,
                "finance.creditCard.name.required"
        );

        validateMaxLength(
                normalized,
                MAX_CREDIT_CARD_NAME_LENGTH,
                "finance.creditCard.name.tooLong"
        );

        return normalized;
    }

    private String normalizeCreditCardNameForComparison(String value) {
        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value, int maxLength, String tooLongMessageKey) {
        if (value == null) {
            return null;
        }

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

    private short normalizeChargeDay(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("finance.creditCard.chargeDay.required");
        }

        if (value < 1 || value > 31) {
            throw new IllegalArgumentException("finance.creditCard.chargeDay.invalid");
        }

        return value.shortValue();
    }
}