package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.EmailVerificationRequiredResponse;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class EmailVerificationResendChallengeService {

    private static final Duration MIN_TOKEN_TTL = Duration.ofMinutes(1);

    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final Duration tokenTtl;
    private final Duration cooldown;

    public EmailVerificationResendChallengeService(
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            AuthActionTokenRepository authActionTokenRepository,
            @Value("${serenityline.auth.email-verification-resend.token-ttl}") Duration tokenTtl,
            @Value("${serenityline.auth.email-verification-resend.cooldown}") Duration cooldown
    ) {
        validateTokenTtl(tokenTtl);
        validateCooldown(cooldown);

        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.tokenTtl = tokenTtl;
        this.cooldown = cooldown;
    }

    private static void validateTokenTtl(Duration tokenTtl) {
        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()
                || tokenTtl.compareTo(MIN_TOKEN_TTL) < 0) {
            throw new IllegalStateException("auth.emailVerificationResend.tokenTtl.invalid");
        }
    }

    private static void validateCooldown(Duration cooldown) {
        if (cooldown == null || cooldown.isNegative()) {
            throw new IllegalStateException("auth.emailVerificationResend.cooldown.invalid");
        }
    }

    public EmailVerificationRequiredResponse createChallenge(User user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.emailVerification.user.required");
        }

        if (user.isPendingDeletion()) {
            throw new IllegalStateException("auth.emailVerification.userPendingDeletion");
        }

        if (user.isUserIsEnabled()) {
            throw new IllegalStateException("auth.emailVerification.userAlreadyVerified");
        }

        OffsetDateTime now = OffsetDateTime.now();

        revokePendingResendTokens(user, now);

        String plainToken = secureTokenGenerator.generate();
        String tokenHash = tokenHashingService.hash(plainToken);
        OffsetDateTime expiresAt = now.plus(tokenTtl);
        OffsetDateTime availableAt = calculateResendAvailableAt(user, now);

        AuthActionToken actionToken = new AuthActionToken(
                user,
                tokenHash,
                AuthActionTokenType.EMAIL_VERIFICATION_RESEND,
                expiresAt
        );

        authActionTokenRepository.save(actionToken);

        return EmailVerificationRequiredResponse.of(
                user,
                plainToken,
                expiresAt,
                availableAt
        );
    }

    public OffsetDateTime calculateResendAvailableAt(User user, OffsetDateTime now) {
        if (user == null) {
            throw new IllegalArgumentException("auth.emailVerification.user.required");
        }

        OffsetDateTime safeNow = now == null ? OffsetDateTime.now() : now;

        return authActionTokenRepository
                .findFirstByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfterOrderByAuthActionCreatedAtDesc(
                        user,
                        AuthActionTokenType.EMAIL_VERIFICATION,
                        safeNow
                )
                .map(AuthActionToken::getAuthActionCreatedAt)
                .map(createdAt -> createdAt.plus(cooldown))
                .filter(availableAt -> availableAt.isAfter(safeNow))
                .orElse(safeNow);
    }

    private void revokePendingResendTokens(User user, OffsetDateTime now) {
        authActionTokenRepository
                .findAllByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfter(
                        user,
                        AuthActionTokenType.EMAIL_VERIFICATION_RESEND,
                        now
                )
                .forEach(AuthActionToken::revoke);
    }
}