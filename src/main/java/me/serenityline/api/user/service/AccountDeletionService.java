package me.serenityline.api.user.service;

import me.serenityline.api.auth.entity.RefreshTokenRevokeReason;
import me.serenityline.api.auth.entity.SessionRevokeReason;
import me.serenityline.api.auth.service.AuthSessionRevocationService;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final AuthSessionRevocationService authSessionRevocationService;

    public AccountDeletionService(
            UserRepository userRepository,
            AuthSessionRevocationService authSessionRevocationService
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.authSessionRevocationService = Objects.requireNonNull(authSessionRevocationService, "authSessionRevocationService");
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("auth.authentication.user.required");
        }

        Optional<User> userOptional = userRepository.findActiveUserByIdForUpdate(userId);

        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();

        user.markAsSoftDeleted();

        authSessionRevocationService.revokeAllForUser(
                user,
                SessionRevokeReason.ACCOUNT_DELETED,
                RefreshTokenRevokeReason.ACCOUNT_DELETED
        );
    }
}