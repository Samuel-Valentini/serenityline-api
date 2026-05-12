package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.LoginRequest;
import me.serenityline.api.auth.dto.LoginResponse;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw invalidCredentials();
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException invalidCredentials() {
        return new IllegalArgumentException("auth.login.invalidCredentials");
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("auth.login.request.required");
        }

        String email = normalizeEmail(request.email());
        String password = request.password();

        if (password == null || password.isBlank()) {
            throw invalidCredentials();
        }

        User user = userRepository.findLoginCandidateByEmail(email)
                .orElseThrow(LoginService::invalidCredentials);

        if (!passwordEncoder.matches(password, user.getUserPasswordHash())) {
            throw invalidCredentials();
        }

        if (user.isPendingDeletion()) {
            if (user.isHardDeletionDue()) {
                throw invalidCredentials();
            }

            throw new IllegalStateException("auth.login.accountPendingDeletion");
        }

        if (!user.isUserIsEnabled()) {
            throw new IllegalStateException("auth.login.emailNotVerified");
        }

        user.markSuccessfulLogin();

        return LoginResponse.from(user);
    }
}