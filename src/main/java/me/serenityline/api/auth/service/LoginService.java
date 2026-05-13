package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.*;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.security.jwt.JwtAccessToken;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class LoginService {

    private static final Duration MIN_RESTORE_ACCOUNT_TOKEN_TTL = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final Duration restoreAccountTokenTtl;
    private final EmailVerificationResendChallengeService emailVerificationResendChallengeService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            AuthActionTokenRepository authActionTokenRepository,
            @Value("${serenityline.auth.restore-account.token-ttl}") Duration restoreAccountTokenTtl,
            EmailVerificationResendChallengeService emailVerificationResendChallengeService,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService
    ) {

        validateRestoreAccountTokenTtl(restoreAccountTokenTtl);

        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.restoreAccountTokenTtl = Objects.requireNonNull(restoreAccountTokenTtl, "restoreAccountTokenTtl");
        this.emailVerificationResendChallengeService = Objects.requireNonNull(emailVerificationResendChallengeService, "emailVerificationResendChallengeService");
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService, "jwtTokenService");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService");
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

    private static void validateRestoreAccountTokenTtl(Duration tokenTtl) {
        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()
                || tokenTtl.compareTo(MIN_RESTORE_ACCOUNT_TOKEN_TTL) < 0) {
            throw new IllegalStateException("auth.restoreAccount.tokenTtl.invalid");
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request, LoginClientMetadata metadata) {
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

            return LoginResult.restoreRequired(createRestoreAccountChallenge(user));
        }

        if (!user.isUserIsEnabled()) {
            return LoginResult.emailVerificationRequired(
                    emailVerificationResendChallengeService.createChallenge(user)
            );
        }

        user.markSuccessfulLogin();

        JwtAccessToken accessToken = jwtTokenService.createAccessToken(user);
        IssuedRefreshToken refreshToken = refreshTokenService.createForLogin(user, metadata);

        return LoginResult.authenticated(
                new AuthenticatedLoginResult(
                        LoginResponse.from(user),
                        accessToken,
                        refreshToken
                )
        );
    }

    private RestoreAccountChallengeResponse createRestoreAccountChallenge(User user) {
        OffsetDateTime now = OffsetDateTime.now();

        revokePendingRestoreAccountTokens(user, now);

        String plainToken = secureTokenGenerator.generate();
        String tokenHash = tokenHashingService.hash(plainToken);
        OffsetDateTime expiresAt = now.plus(restoreAccountTokenTtl);

        AuthActionToken actionToken = new AuthActionToken(
                user,
                tokenHash,
                AuthActionTokenType.RESTORE_ACCOUNT,
                expiresAt
        );

        authActionTokenRepository.save(actionToken);

        return RestoreAccountChallengeResponse.of(plainToken, expiresAt);
    }

    private void revokePendingRestoreAccountTokens(User user, OffsetDateTime now) {
        authActionTokenRepository
                .findAllByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfter(
                        user,
                        AuthActionTokenType.RESTORE_ACCOUNT,
                        now
                )
                .forEach(AuthActionToken::revoke);
    }
}