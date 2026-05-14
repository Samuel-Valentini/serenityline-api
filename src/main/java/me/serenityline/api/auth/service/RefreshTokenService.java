package me.serenityline.api.auth.service;

import me.serenityline.api.auth.config.RefreshTokenProperties;
import me.serenityline.api.auth.dto.AuthenticatedLoginResult;
import me.serenityline.api.auth.dto.IssuedRefreshToken;
import me.serenityline.api.auth.dto.LoginClientMetadata;
import me.serenityline.api.auth.dto.LoginResponse;
import me.serenityline.api.auth.entity.RefreshToken;
import me.serenityline.api.auth.entity.SessionRevokeReason;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
import me.serenityline.api.security.jwt.JwtAccessToken;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final TokenHashingService tokenHashingService;
    private final RefreshTokenProperties refreshTokenProperties;
    private final ClientIpHashingService clientIpHashingService;
    private final SecureTokenGenerator secureTokenGenerator;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserSessionRepository userSessionRepository,
            TokenHashingService tokenHashingService,
            RefreshTokenProperties refreshTokenProperties,
            ClientIpHashingService clientIpHashingService,
            SecureTokenGenerator secureTokenGenerator,
            JwtTokenService jwtTokenService,
            Clock clock
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
        this.userSessionRepository = Objects.requireNonNull(userSessionRepository, "userSessionRepository");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.refreshTokenProperties = Objects.requireNonNull(refreshTokenProperties, "refreshTokenProperties");
        this.clientIpHashingService = Objects.requireNonNull(clientIpHashingService, "clientIpHashingService");
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService, "jwtTokenService");
        this.clock = Objects.requireNonNull(clock, "clock");
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

    private static OffsetDateTime earlierOf(
            OffsetDateTime first,
            OffsetDateTime second
    ) {
        if (first.isAfter(second)) {
            return second;
        }

        return first;
    }

    @Transactional
    public IssuedRefreshToken createForLogin(
            User user,
            LoginClientMetadata metadata
    ) {
        if (user == null) {
            throw new IllegalArgumentException("auth.refreshToken.user.required");
        }

        OffsetDateTime expiresAt = OffsetDateTime.now(clock)
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

        String plainRefreshToken = secureTokenGenerator.generateRefreshToken();
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

    @Transactional
    public Optional<AuthenticatedLoginResult> refresh(String plainRefreshToken) {
        if (plainRefreshToken == null || plainRefreshToken.isBlank()) {
            return Optional.empty();
        }

        String normalizedToken = plainRefreshToken.trim();
        String refreshTokenHash = tokenHashingService.hash(normalizedToken);

        Optional<RefreshToken> refreshTokenOptional = refreshTokenRepository
                .findByRefreshTokenHashForUpdate(refreshTokenHash);

        if (refreshTokenOptional.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken currentToken = refreshTokenOptional.get();
        UserSession session = currentToken.getUserSession();

        if (currentToken.isUsed()) {
            currentToken.markReuseDetected();

            if (!session.isRevoked()) {
                session.revoke(SessionRevokeReason.TOKEN_REUSE_DETECTED);
            }

            return Optional.empty();
        }

        if (!currentToken.isActive()) {
            return Optional.empty();
        }

        if (!session.isActive()) {
            return Optional.empty();
        }

        User user = currentToken.getUser();

        if (user.isPendingDeletion()) {
            return Optional.empty();
        }

        if (!user.isUserIsEnabled()) {
            return Optional.empty();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        OffsetDateTime replacementExpiresAt = earlierOf(
                now.plus(refreshTokenProperties.ttl()),
                session.getSessionExpiresAt()
        );

        String replacementPlainToken = secureTokenGenerator.generateRefreshToken();
        String replacementTokenHash = tokenHashingService.hash(replacementPlainToken);

        RefreshToken replacementToken = new RefreshToken(
                user,
                session,
                replacementTokenHash,
                replacementExpiresAt,
                currentToken
        );

        refreshTokenRepository.save(replacementToken);

        currentToken.markUsedAndReplacedBy(replacementToken);
        session.markSeen();

        JwtAccessToken accessToken = jwtTokenService.createAccessToken(user);

        return Optional.of(new AuthenticatedLoginResult(
                LoginResponse.from(user),
                accessToken,
                new IssuedRefreshToken(
                        replacementPlainToken,
                        replacementExpiresAt
                )
        ));
    }

}