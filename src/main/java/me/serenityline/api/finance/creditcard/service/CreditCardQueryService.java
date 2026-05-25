package me.serenityline.api.finance.creditcard.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.creditcard.repository.CreditCardRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CreditCardQueryService {

    private final CreditCardRepository creditCardRepository;
    private final UserRepository userRepository;

    public CreditCardQueryService(
            CreditCardRepository creditCardRepository,
            UserRepository userRepository
    ) {
        this.creditCardRepository = Objects.requireNonNull(creditCardRepository, "creditCardRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional(readOnly = true)
    public List<CreditCard> findVisibleCreditCards(UUID currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canSeeAllGroupCreditCards(currentUser)) {
            return creditCardRepository.findAllByUserGroup_UserGroupIdOrderByCreditCardNameAsc(
                    userGroupId
            );
        }

        return creditCardRepository.findAllVisibleToLinkedUser(
                userGroupId,
                currentUser.getUserId()
        );
    }

    @Transactional(readOnly = true)
    public CreditCard findVisibleCreditCard(UUID currentUserId, UUID creditCardId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(creditCardId, "creditCardId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canSeeAllGroupCreditCards(currentUser)) {
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

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.creditCard.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.creditCard.notFound");
        }

        return currentUser;
    }

    private boolean canSeeAllGroupCreditCards(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR
                || userRole == UserRole.VIEWER_COLLABORATOR;
    }
}