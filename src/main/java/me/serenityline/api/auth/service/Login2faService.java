package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.*;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.exception.Login2faInvalidOrExpiredException;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.jwt.JwtAccessToken;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class Login2faService {

    private static final Duration MIN_CODE_TTL = Duration.ofMinutes(1);
    private static final int CODE_UPPER_BOUND = 1_000_000;

    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final MessageSource messageSource;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Duration codeTtl;
    private final int maxAttempts;
    private final SecureRandom secureRandom;

    public Login2faService(
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            AuthActionTokenRepository authActionTokenRepository,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            MessageSource messageSource,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            @Value("${serenityline.auth.login-2fa.code-ttl}") Duration codeTtl,
            @Value("${serenityline.auth.login-2fa.max-attempts}") int maxAttempts
    ) {
        validateCodeTtl(codeTtl);
        validateMaxAttempts(maxAttempts);

        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService, "jwtTokenService");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService");
        this.codeTtl = codeTtl;
        this.maxAttempts = maxAttempts;
        this.secureRandom = new SecureRandom();
    }

    private static void validateCodeTtl(Duration codeTtl) {
        if (codeTtl == null
                || codeTtl.isZero()
                || codeTtl.isNegative()
                || codeTtl.compareTo(MIN_CODE_TTL) < 0) {
            throw new IllegalStateException("auth.login2fa.codeTtl.invalid");
        }
    }

    private static void validateMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalStateException("auth.login2fa.maxAttempts.invalid");
        }
    }

    private static Login2faInvalidOrExpiredException invalidOrExpiredCode() {
        return new Login2faInvalidOrExpiredException();
    }

    @Transactional
    public Login2faRequiredResponse createChallenge(User user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.login2fa.user.required");
        }

        if (user.isPendingDeletion()) {
            throw new IllegalStateException("auth.login2fa.userPendingDeletion");
        }

        if (!user.isUserIsEnabled()) {
            throw new IllegalStateException("auth.login2fa.userNotVerified");
        }

        if (!user.isEmailTwoFactorEnabled()) {
            throw new IllegalStateException("auth.login2fa.notEnabled");
        }

        OffsetDateTime now = OffsetDateTime.now();

        revokePendingLogin2faTokens(user, now);
        cancelPendingLogin2faEmails(user);

        String code = generateCode();
        OffsetDateTime expiresAt = now.plus(codeTtl);

        String temporaryHash = tokenHashingService.hash(
                "login-2fa-temporary:" + secureTokenGenerator.generate()
        );

        AuthActionToken actionToken = new AuthActionToken(
                user,
                temporaryHash,
                AuthActionTokenType.LOGIN_2FA_CODE,
                expiresAt,
                maxAttempts
        );

        authActionTokenRepository.save(actionToken);

        UUID challengeId = actionToken.getAuthActionTokenId();

        if (challengeId == null) {
            throw new IllegalStateException("auth.login2fa.challengeId.required");
        }

        String finalHash = hashCode(challengeId, code);

        actionToken.replacePendingHash(finalHash);

        EmailOutbox emailOutbox = createEmailOutbox(user, code, now);

        emailOutboxRepository.save(emailOutbox);

        return new Login2faRequiredResponse(
                challengeId,
                expiresAt
        );
    }

    @Transactional(noRollbackFor = Login2faInvalidOrExpiredException.class)
    public AuthenticatedLoginResult verify(
            VerifyLogin2faRequest request,
            LoginClientMetadata metadata
    ) {
        if (request == null) {
            throw new IllegalArgumentException("auth.login2fa.request.required");
        }

        UUID challengeId = request.challengeId();

        if (challengeId == null) {
            throw invalidOrExpiredCode();
        }

        String code = normalizeCode(request.code());

        AuthActionToken actionToken = authActionTokenRepository
                .findByIdForUpdate(challengeId)
                .orElseThrow(Login2faService::invalidOrExpiredCode);

        if (actionToken.getAuthActionTokenType() != AuthActionTokenType.LOGIN_2FA_CODE) {
            throw invalidOrExpiredCode();
        }

        User user = actionToken.getUser();

        if (user.isPendingDeletion()
                || !user.isUserIsEnabled()
                || !user.isEmailTwoFactorEnabled()) {
            throw invalidOrExpiredCode();
        }

        if (!actionToken.isPending() || actionToken.hasReachedFailedAttemptLimit()) {
            throw invalidOrExpiredCode();
        }

        String expectedHash = hashCode(challengeId, code);

        if (!hashMatches(expectedHash, actionToken.getAuthActionTokenHash())) {
            recordFailedAttempt(actionToken);

            throw invalidOrExpiredCode();
        }

        try {
            actionToken.markUsed(AuthActionTokenType.LOGIN_2FA_CODE);
        } catch (IllegalStateException ex) {
            throw invalidOrExpiredCode();
        }

        user.markSuccessfulLogin();

        JwtAccessToken accessToken = jwtTokenService.createAccessToken(user);
        IssuedRefreshToken refreshToken = refreshTokenService.createForLogin(user, metadata);

        return new AuthenticatedLoginResult(
                LoginResponse.from(user),
                accessToken,
                refreshToken
        );
    }

    private void recordFailedAttempt(AuthActionToken actionToken) {
        try {
            actionToken.recordFailedAttempt();
        } catch (IllegalStateException ex) {
            throw invalidOrExpiredCode();
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw invalidOrExpiredCode();
        }

        String normalizedCode = code.trim();

        if (!normalizedCode.matches("\\d{6}")) {
            throw invalidOrExpiredCode();
        }

        return normalizedCode;
    }

    private String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(CODE_UPPER_BOUND));
    }

    private String hashCode(UUID challengeId, String code) {
        return tokenHashingService.hash(challengeId + ":" + code);
    }

    private boolean hashMatches(String expectedHash, String actualHash) {
        if (expectedHash == null || actualHash == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void revokePendingLogin2faTokens(User user, OffsetDateTime now) {
        authActionTokenRepository
                .findAllByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfter(
                        user,
                        AuthActionTokenType.LOGIN_2FA_CODE,
                        now
                )
                .forEach(AuthActionToken::revoke);
    }

    private void cancelPendingLogin2faEmails(User user) {
        emailOutboxRepository
                .findAllByUserAndEmailTypeAndEmailStatus(
                        user,
                        EmailOutboxType.LOGIN_2FA_CODE,
                        EmailOutboxStatus.PENDING
                )
                .forEach(EmailOutbox::cancel);
    }

    private EmailOutbox createEmailOutbox(User user, String code, OffsetDateTime scheduledAt) {
        String subject = buildSubject(user);
        String textBody = buildTextBody(user, code);

        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(subject);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(textBody);

        return new EmailOutbox(
                user,
                user.getEmail(),
                EmailOutboxType.LOGIN_2FA_CODE,
                emailOutboxEncryptionService.getEncryptionKeyId(),
                encryptedSubject.encrypted(),
                encryptedSubject.iv(),
                encryptedSubject.tag(),
                null,
                null,
                null,
                encryptedTextBody.encrypted(),
                encryptedTextBody.iv(),
                encryptedTextBody.tag(),
                true,
                scheduledAt
        );
    }

    private String buildSubject(User user) {
        return messageSource.getMessage(
                "auth.login2fa.email.subject",
                null,
                resolveUserLocale(user)
        );
    }

    private String buildTextBody(User user, String code) {
        Locale locale = resolveUserLocale(user);

        return messageSource.getMessage(
                "auth.login2fa.email.body.text",
                new Object[]{
                        user.getUserName(),
                        code,
                        formatCodeTtl(locale)
                },
                locale
        );
    }

    private Locale resolveUserLocale(User user) {
        String preferredLocale = user.getPreferredLocale();

        if (preferredLocale == null || preferredLocale.isBlank()) {
            return Locale.forLanguageTag("it-IT");
        }

        return Locale.forLanguageTag(preferredLocale);
    }

    private String formatCodeTtl(Locale locale) {
        long totalMinutes = codeTtl.toMinutes();

        if (totalMinutes <= 0) {
            throw new IllegalStateException("auth.login2fa.codeTtl.invalid");
        }

        if (totalMinutes % 60 == 0) {
            long hours = totalMinutes / 60;

            return messageSource.getMessage(
                    hours == 1
                            ? "auth.login2fa.ttl.hours.singular"
                            : "auth.login2fa.ttl.hours.plural",
                    new Object[]{hours},
                    locale
            );
        }

        return messageSource.getMessage(
                totalMinutes == 1
                        ? "auth.login2fa.ttl.minutes.singular"
                        : "auth.login2fa.ttl.minutes.plural",
                new Object[]{totalMinutes},
                locale
        );
    }
}