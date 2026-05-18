package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.VerifyEmailResponse;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class EmailVerificationService {

    private final Duration emailVerificationTokenTtl;

    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final MessageSource messageSource;
    private final String frontendBaseUrl;

    public EmailVerificationService(
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            AuthActionTokenRepository authActionTokenRepository,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            @Value("${serenityline.auth.email-verification.token-ttl}") Duration emailVerificationTokenTtl,
            MessageSource messageSource,
            @Value("${serenityline.frontend.base-url}") String frontendBaseUrl

    ) {

        validateEmailVerificationTokenTtl(emailVerificationTokenTtl);
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.emailVerificationTokenTtl = emailVerificationTokenTtl;
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
        this.frontendBaseUrl = normalizeFrontendBaseUrl(frontendBaseUrl);
    }

    private static String normalizeFrontendBaseUrl(String frontendBaseUrl) {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalStateException("auth.emailVerification.frontendBaseUrl.required");
        }

        String normalized = frontendBaseUrl.trim();

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalStateException("auth.emailVerification.frontendBaseUrl.invalid");
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private static void ensureUserCanReceiveEmailVerification(User user) {
        if (user.isUserIsEnabled()) {
            throw new IllegalStateException("auth.emailVerification.userAlreadyVerified");
        }

        if (user.isPendingDeletion()) {
            throw new IllegalStateException("auth.emailVerification.userPendingDeletion");
        }
    }

    private static void validateEmailVerificationTokenTtl(Duration tokenTtl) {
        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()
                || tokenTtl.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalStateException("auth.emailVerification.tokenTtl.invalid");
        }
    }

    private static IllegalArgumentException invalidEmailVerificationToken() {
        return new IllegalArgumentException("auth.emailVerification.invalidOrExpired");
    }

    @Transactional
    public void createEmailVerification(User user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.emailVerification.user.required");
        }

        ensureUserCanReceiveEmailVerification(user);

        OffsetDateTime now = OffsetDateTime.now();
        cancelPendingEmailVerificationEmails(user);
        revokePendingEmailVerificationTokens(user, now);

        String plainToken = secureTokenGenerator.generate();
        String tokenHash = tokenHashingService.hash(plainToken);

        OffsetDateTime expiresAt = now.plus(emailVerificationTokenTtl);

        AuthActionToken actionToken = new AuthActionToken(
                user,
                tokenHash,
                AuthActionTokenType.EMAIL_VERIFICATION,
                expiresAt
        );

        authActionTokenRepository.save(actionToken);

        EmailOutbox emailOutbox = createEmailOutbox(user, plainToken, now);

        emailOutboxRepository.save(emailOutbox);
    }

    private EmailOutbox createEmailOutbox(User user, String plainToken, OffsetDateTime scheduledAt) {
        String subject = buildSubject(user);
        String textBody = buildTextBody(user, plainToken);

        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(subject);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(textBody);

        return new EmailOutbox(
                user,
                user.getEmail(),
                EmailOutboxType.EMAIL_VERIFICATION,
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
                "auth.emailVerification.email.subject",
                null,
                resolveUserLocale(user)
        );
    }

    private String buildTextBody(User user, String plainToken) {

        Locale locale = resolveUserLocale(user);

        return messageSource.getMessage(
                "auth.emailVerification.email.body.text",
                new Object[]{
                        user.getUserName(),
                        buildVerificationUrl(plainToken),
                        buildManualVerificationUrl(),
                        plainToken,
                        formatTokenTtl(locale)
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

    private String buildVerificationUrl(String plainToken) {
        return frontendBaseUrl + "/verify-email#token=" + plainToken;
    }

    private String buildManualVerificationUrl() {
        return frontendBaseUrl + "/verify-email";
    }

    private String formatTokenTtl(Locale locale) {
        long totalMinutes = emailVerificationTokenTtl.toMinutes();

        if (totalMinutes <= 0) {
            throw new IllegalStateException("auth.emailVerification.tokenTtl.invalid");
        }

        if (totalMinutes % (24 * 60) == 0) {
            long days = totalMinutes / (24 * 60);

            return messageSource.getMessage(
                    days == 1
                            ? "auth.emailVerification.ttl.days.singular"
                            : "auth.emailVerification.ttl.days.plural",
                    new Object[]{days},
                    locale
            );
        }

        if (totalMinutes % 60 == 0) {
            long hours = totalMinutes / 60;

            return messageSource.getMessage(
                    hours == 1
                            ? "auth.emailVerification.ttl.hours.singular"
                            : "auth.emailVerification.ttl.hours.plural",
                    new Object[]{hours},
                    locale
            );
        }

        return messageSource.getMessage(
                totalMinutes == 1
                        ? "auth.emailVerification.ttl.minutes.singular"
                        : "auth.emailVerification.ttl.minutes.plural",
                new Object[]{totalMinutes},
                locale
        );
    }

    private void revokePendingEmailVerificationTokens(User user, OffsetDateTime now) {
        authActionTokenRepository
                .findAllByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfter(
                        user,
                        AuthActionTokenType.EMAIL_VERIFICATION,
                        now
                )
                .forEach(AuthActionToken::revoke);
    }

    private void cancelPendingEmailVerificationEmails(User user) {
        emailOutboxRepository
                .findAllByUserAndEmailTypeAndEmailStatus(
                        user,
                        EmailOutboxType.EMAIL_VERIFICATION,
                        EmailOutboxStatus.PENDING
                )
                .forEach(EmailOutbox::cancel);
    }

    @Transactional
    public VerifyEmailResponse verifyEmail(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new IllegalArgumentException("auth.token.required");
        }

        String normalizedToken = plainToken.trim();
        String tokenHash = tokenHashingService.hash(normalizedToken);

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHashForUpdate(tokenHash)
                .orElseThrow(EmailVerificationService::invalidEmailVerificationToken);

        if (actionToken.getAuthActionTokenType() != AuthActionTokenType.EMAIL_VERIFICATION) {
            throw invalidEmailVerificationToken();
        }

        User user = actionToken.getUser();

        if (user.isPendingDeletion()) {
            throw invalidEmailVerificationToken();
        }

        if (user.isUserIsEnabled()) {
            return VerifyEmailResponse.verified();
        }

        if (!actionToken.isPending()) {
            throw invalidEmailVerificationToken();
        }

        try {
            actionToken.markUsed(AuthActionTokenType.EMAIL_VERIFICATION);
        } catch (IllegalStateException ex) {
            throw invalidEmailVerificationToken();
        }

        user.setUserIsEnabled(true);

        return VerifyEmailResponse.verified();
    }

}