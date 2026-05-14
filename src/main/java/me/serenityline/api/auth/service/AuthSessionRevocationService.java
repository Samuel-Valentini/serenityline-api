package me.serenityline.api.auth.service;

import me.serenityline.api.auth.entity.RefreshToken;
import me.serenityline.api.auth.entity.RefreshTokenRevokeReason;
import me.serenityline.api.auth.entity.SessionRevokeReason;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
import me.serenityline.api.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AuthSessionRevocationService {

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public AuthSessionRevocationService(
            UserSessionRepository userSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock
    ) {
        this.userSessionRepository = Objects.requireNonNull(userSessionRepository, "userSessionRepository");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public void revokeAllForUser(
            User user,
            SessionRevokeReason sessionRevokeReason,
            RefreshTokenRevokeReason refreshTokenRevokeReason
    ) {
        if (user == null) {
            throw new IllegalArgumentException("auth.sessionRevocation.user.required");
        }

        if (sessionRevokeReason == null) {
            throw new IllegalArgumentException("auth.sessionRevocation.sessionReason.required");
        }

        if (refreshTokenRevokeReason == null) {
            throw new IllegalArgumentException("auth.sessionRevocation.refreshTokenReason.required");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        List<RefreshToken> activeRefreshTokens = refreshTokenRepository.findActiveByUserForUpdate(
                user,
                now
        );

        for (RefreshToken refreshToken : activeRefreshTokens) {
            if (!refreshToken.isRevoked()) {
                refreshToken.revoke(refreshTokenRevokeReason);
            }
        }

        List<UserSession> activeSessions = userSessionRepository.findActiveByUserForUpdate(
                user,
                now
        );

        for (UserSession session : activeSessions) {
            if (!session.isRevoked()) {
                session.revoke(sessionRevokeReason);
            }
        }
    }
}