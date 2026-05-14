package me.serenityline.api.user.service;

import me.serenityline.api.auth.entity.RefreshTokenRevokeReason;
import me.serenityline.api.auth.entity.SessionRevokeReason;
import me.serenityline.api.auth.service.AuthSessionRevocationService;
import me.serenityline.api.auth.service.PasswordPolicyService;
import me.serenityline.api.user.dto.ChangePasswordRequest;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChangePasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRevocationService authSessionRevocationService;
    private final PasswordPolicyService passwordPolicyService;

    public ChangePasswordService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionRevocationService authSessionRevocationService,
            PasswordPolicyService passwordPolicyService
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.authSessionRevocationService = Objects.requireNonNull(authSessionRevocationService, "authSessionRevocationService");
        this.passwordPolicyService = Objects.requireNonNull(passwordPolicyService, "passwordPolicyService");
    }

    @Transactional
    public void changePassword(
            UUID userId,
            ChangePasswordRequest request
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("auth.authentication.user.required");
        }

        if (request == null) {
            throw new IllegalArgumentException("auth.password.request.required");
        }

        Optional<User> userOptional = userRepository.findActiveUserByIdForUpdate(userId);

        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();

        String currentPassword = request.currentPassword();
        String newPassword = request.newPassword();

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("auth.password.current.required");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("auth.password.new.required");
        }

        if (!passwordEncoder.matches(currentPassword, user.getUserPasswordHash())) {
            throw new IllegalArgumentException("auth.password.current.invalid");
        }

        if (passwordEncoder.matches(newPassword, user.getUserPasswordHash())) {
            throw new IllegalArgumentException("auth.password.new.sameAsCurrent");
        }

        passwordPolicyService.validateChangePassword(
                newPassword,
                user.getUserName(),
                user.getEmail()
        );

        String newPasswordHash = passwordEncoder.encode(newPassword);

        user.changePassword(newPasswordHash);

        authSessionRevocationService.revokeAllForUser(
                user,
                SessionRevokeReason.PASSWORD_CHANGED,
                RefreshTokenRevokeReason.PASSWORD_CHANGED
        );
    }
}