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

import java.util.Objects;
import java.util.UUID;

@Service
public class CreditCardDeletionService {

    private final CreditCardRepository creditCardRepository;
    private final UserRepository userRepository;

    public CreditCardDeletionService(
            CreditCardRepository creditCardRepository,
            UserRepository userRepository
    ) {
        this.creditCardRepository = Objects.requireNonNull(creditCardRepository, "creditCardRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public void deleteCreditCard(UUID currentUserId, UUID creditCardId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(creditCardId, "creditCardId");

        User currentUser = findCurrentUser(currentUserId);
        CreditCard creditCard = findDeletableCreditCard(currentUser, creditCardId);

//        TODO:
//        if (creditCardUsageChecker.isCreditCardUsed(creditCard.getCreditCardId())) {
//            throw new IllegalStateException("finance.creditCard.alreadyUsed");
//        }


        try {
            creditCardRepository.delete(creditCard);
            creditCardRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("finance.creditCard.alreadyUsed", exception);
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

    private CreditCard findDeletableCreditCard(User currentUser, UUID creditCardId) {
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canDeleteAllGroupCreditCards(currentUser)) {
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

    private boolean canDeleteAllGroupCreditCards(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR;
    }
}