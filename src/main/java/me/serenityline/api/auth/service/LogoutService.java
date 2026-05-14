package me.serenityline.api.auth.service;

import me.serenityline.api.auth.entity.RefreshToken;
import me.serenityline.api.auth.entity.RefreshTokenRevokeReason;
import me.serenityline.api.auth.entity.SessionRevokeReason;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashingService tokenHashingService;
    private final UserRepository userRepository;
    private final AuthSessionRevocationService authSessionRevocationService;

    public LogoutService(
            RefreshTokenRepository refreshTokenRepository,
            TokenHashingService tokenHashingService,
            UserRepository userRepository,
            AuthSessionRevocationService authSessionRevocationService
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.authSessionRevocationService = Objects.requireNonNull(authSessionRevocationService, "authSessionRevocationService");
    }

    /*
     * Logout revokes the refresh token/session only.
     * Already issued access tokens remain valid until expiration because JWTs are stateless
     * and are not currently bound to user_sessions.
     * If you also want to revoke the access tokens,
     * you must log out of all devices.
     */

    @Transactional
    public void logout(String plainRefreshToken) {
        if (plainRefreshToken == null || plainRefreshToken.isBlank()) {
            return;
        }

        String normalizedToken = plainRefreshToken.trim();
        String refreshTokenHash = tokenHashingService.hash(normalizedToken);

        Optional<RefreshToken> refreshTokenOptional = refreshTokenRepository
                .findByRefreshTokenHashForUpdate(refreshTokenHash);

        if (refreshTokenOptional.isEmpty()) {
            return;
        }

        RefreshToken refreshToken = refreshTokenOptional.get();
        UserSession session = refreshToken.getUserSession();

        if (refreshToken.isActive()) {
            refreshToken.revoke(RefreshTokenRevokeReason.USER_LOGOUT);
        }

        if (!session.isRevoked()) {
            session.revoke(SessionRevokeReason.USER_LOGOUT);
        }
    }

    @Transactional
    public void logoutAllDevices(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("auth.authentication.user.required");
        }

        Optional<User> userOptional = userRepository.findActiveUserByIdForUpdate(userId);

        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();

        user.incrementTokenVersion();

        authSessionRevocationService.revokeAllForUser(
                user,
                SessionRevokeReason.USER_LOGOUT,
                RefreshTokenRevokeReason.USER_LOGOUT
        );
    }
}