package me.serenityline.api.auth.service;

import me.serenityline.api.auth.config.RefreshTokenProperties;
import me.serenityline.api.auth.dto.IssuedRefreshToken;
import me.serenityline.api.auth.dto.LoginClientMetadata;
import me.serenityline.api.auth.entity.RefreshToken;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class RefreshTokenService {

    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties refreshTokenProperties;
    private final ClientIpHashingService clientIpHashingService;

    public RefreshTokenService(
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            UserSessionRepository userSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenProperties refreshTokenProperties, ClientIpHashingService clientIpHashingService
    ) {
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.userSessionRepository = Objects.requireNonNull(userSessionRepository, "userSessionRepository");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
        this.refreshTokenProperties = Objects.requireNonNull(refreshTokenProperties, "refreshTokenProperties");
        this.clientIpHashingService = Objects.requireNonNull(clientIpHashingService, "clientIpHashingService");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            return null;
        }

        return normalized;
    }

    @Transactional
    public IssuedRefreshToken createForLogin(
            User user,
            LoginClientMetadata metadata
    ) {
        if (user == null) {
            throw new IllegalArgumentException("auth.refreshToken.user.required");
        }

        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plus(refreshTokenProperties.ttl());

        String ipAddressHash = null;
        String userAgent = null;
        String deviceLabel = null;

        if (metadata != null) {
            ipAddressHash = clientIpHashingService.hashIpAddress(metadata.ipAddress());
            userAgent = blankToNull(metadata.userAgent());
            deviceLabel = blankToNull(metadata.deviceLabel());
        }

        UserSession userSession = new UserSession(
                user,
                expiresAt,
                ipAddressHash,
                userAgent,
                deviceLabel
        );

        UserSession savedSession = userSessionRepository.save(userSession);

        String plainRefreshToken = secureTokenGenerator.generate();
        String refreshTokenHash = tokenHashingService.hash(plainRefreshToken);

        RefreshToken refreshToken = new RefreshToken(
                user,
                savedSession,
                refreshTokenHash,
                expiresAt,
                null
        );

        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(
                plainRefreshToken,
                expiresAt
        );
    }
}