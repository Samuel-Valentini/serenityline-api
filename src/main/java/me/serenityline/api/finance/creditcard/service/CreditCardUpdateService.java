package me.serenityline.api.finance.creditcard.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
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
public class CreditCardUpdateService {

    private static final int MAX_CREDIT_CARD_NAME_LENGTH = 255;
    private static final int MAX_CREDIT_CARD_DESCRIPTION_LENGTH = 2000;

    private final CreditCardRepository creditCardRepository;
    private final UserRepository userRepository;

    public CreditCardUpdateService(
            CreditCardRepository creditCardRepository,
            UserRepository userRepository
    ) {
        this.creditCardRepository = Objects.requireNonNull(creditCardRepository, "creditCardRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public CreditCard updateCreditCard(UUID currentUserId, UUID creditCardId, UpdateCreditCardCommand command) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(creditCardId, "creditCardId");
        Objects.requireNonNull(command, "command");

        User currentUser = findCurrentUser(currentUserId);
        CreditCard creditCard = findUpdatableCreditCard(currentUser, creditCardId);

        String creditCardName = creditCard.getCreditCardName();

        if (command.creditCardName() != null) {
            creditCardName = normalizeCreditCardNameForStorage(command.creditCardName());
            String normalizedCreditCardName = normalizeCreditCardNameForComparison(creditCardName);

            if (creditCardRepository.existsByUserGroupIdAndNormalizedCreditCardNameExcludingCreditCardId(
                    currentUser.getUserGroup().getUserGroupId(),
                    normalizedCreditCardName,
                    creditCard.getCreditCardId()
            )) {
                throw new IllegalStateException("finance.creditCard.nameAlreadyExists");
            }
        }

        String creditCardDescription = creditCard.getCreditCardDescription();

        if (command.creditCardDescription() != null) {
            creditCardDescription = normalizeOptionalText(
                    command.creditCardDescription(),
                    MAX_CREDIT_CARD_DESCRIPTION_LENGTH,
                    "finance.creditCard.description.tooLong"
            );
        }

        short creditCardChargeDay = creditCard.getCreditCardChargeDay();

        if (command.creditCardChargeDay() != null) {
            creditCardChargeDay = normalizeChargeDay(command.creditCardChargeDay());
        }

        creditCard.update(
                creditCardName,
                creditCardDescription,
                creditCardChargeDay
        );

        try {
            return creditCardRepository.saveAndFlush(creditCard);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("finance.creditCard.nameAlreadyExists", exception);
        }
    }

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.creditCard.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.creditCard.notFound");
        }

        return currentUser;
    }

    private CreditCard findUpdatableCreditCard(User currentUser, UUID creditCardId) {
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canUpdateAllGroupCreditCards(currentUser)) {
            return creditCardRepository.findByCreditCardIdAndUserGroup_UserGroupId(
                    creditCardId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.creditCard.notFound"));
        }

        return creditCardRepository.findVisibleCreditCardForLinkedUser(
                creditCardId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.creditCard.notFound"));
    }

    private boolean canUpdateAllGroupCreditCards(User user) {
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

    private short normalizeChargeDay(Integer value) {
        if (value < 1 || value > 31) {
            throw new IllegalArgumentException("finance.creditCard.chargeDay.invalid");
        }

        return value.shortValue();
    }

    private void validateMaxLength(String value, int maxLength, String tooLongMessageKey) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(tooLongMessageKey);
        }
    }
}