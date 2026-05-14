package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.ForgotPasswordRequest;
import me.serenityline.api.auth.dto.ResetPasswordRequest;
import me.serenityline.api.auth.entity.*;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class PasswordResetService {

    private final Duration passwordResetTokenTtl;

    private final UserRepository userRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final AuthSessionRevocationService authSessionRevocationService;
    private final MessageSource messageSource;
    private final String frontendBaseUrl;

    public PasswordResetService(
            UserRepository userRepository,
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            AuthActionTokenRepository authActionTokenRepository,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            AuthSessionRevocationService authSessionRevocationService,
            MessageSource messageSource,
            @Value("${serenityline.auth.password-reset.token-ttl}") Duration passwordResetTokenTtl,
            @Value("${serenityline.frontend.base-url}") String frontendBaseUrl
    ) {
        validatePasswordResetTokenTtl(passwordResetTokenTtl);

        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.passwordPolicyService = Objects.requireNonNull(passwordPolicyService, "passwordPolicyService");
        this.authSessionRevocationService = Objects.requireNonNull(authSessionRevocationService, "authSessionRevocationService");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
        this.passwordResetTokenTtl = passwordResetTokenTtl;
        this.frontendBaseUrl = normalizeFrontendBaseUrl(frontendBaseUrl);
    }

    private static void validatePasswordResetTokenTtl(Duration tokenTtl) {
        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()
                || tokenTtl.compareTo(Duration.ofMinutes(5)) < 0
                || tokenTtl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalStateException("auth.passwordReset.tokenTtl.invalid");
        }
    }

    private static String normalizeFrontendBaseUrl(String frontendBaseUrl) {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalStateException("auth.passwordReset.frontendBaseUrl.required");
        }

        String normalized = frontendBaseUrl.trim();

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalStateException("auth.passwordReset.frontendBaseUrl.invalid");
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private static IllegalArgumentException invalidPasswordResetToken() {
        return new IllegalArgumentException("auth.passwordReset.invalidOrExpired");
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("auth.passwordReset.request.required");
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("user.email.required");
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        userRepository.findLoginCandidateByEmail(normalizedEmail)
                .filter(user -> !user.isHardDeletionDue())
                .ifPresent(this::createPasswordReset);
    }

    private void createPasswordReset(User user) {
        OffsetDateTime now = OffsetDateTime.now();

        cancelPendingPasswordResetEmails(user);
        revokePendingPasswordResetTokens(user, now);

        String plainToken = secureTokenGenerator.generate();
        String tokenHash = tokenHashingService.hash(plainToken);

        OffsetDateTime expiresAt = now.plus(passwordResetTokenTtl);

        AuthActionToken actionToken = new AuthActionToken(
                user,
                tokenHash,
                AuthActionTokenType.PASSWORD_RESET,
                expiresAt
        );

        authActionTokenRepository.save(actionToken);

        EmailOutbox emailOutbox = createEmailOutbox(user, plainToken, now);

        emailOutboxRepository.save(emailOutbox);
    }

    private EmailOutbox createEmailOutbox(
            User user,
            String plainToken,
            OffsetDateTime scheduledAt
    ) {
        String subject = buildSubject(user);
        String textBody = buildTextBody(user, plainToken);

        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(subject);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(textBody);

        return new EmailOutbox(
                user,
                user.getEmail(),
                EmailOutboxType.PASSWORD_RESET,
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
                "auth.passwordReset.email.subject",
                null,
                resolveUserLocale(user)
        );
    }

    private String buildTextBody(User user, String plainToken) {
        Locale locale = resolveUserLocale(user);

        return messageSource.getMessage(
                "auth.passwordReset.email.body.text",
                new Object[]{
                        user.getUserName(),
                        buildResetPasswordUrl(plainToken),
                        buildManualResetPasswordUrl(),
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

    private String buildResetPasswordUrl(String plainToken) {
        return frontendBaseUrl + "/reset-password#token=" + plainToken;
    }

    private String buildManualResetPasswordUrl() {
        return frontendBaseUrl + "/reset-password";
    }

    private String formatTokenTtl(Locale locale) {
        long totalMinutes = passwordResetTokenTtl.toMinutes();

        if (totalMinutes <= 0) {
            throw new IllegalStateException("auth.passwordReset.tokenTtl.invalid");
        }

        if (totalMinutes % (24 * 60) == 0) {
            long days = totalMinutes / (24 * 60);

            return messageSource.getMessage(
                    days == 1
                            ? "auth.passwordReset.ttl.days.singular"
                            : "auth.passwordReset.ttl.days.plural",
                    new Object[]{days},
                    locale
            );
        }

        if (totalMinutes % 60 == 0) {
            long hours = totalMinutes / 60;

            return messageSource.getMessage(
                    hours == 1
                            ? "auth.passwordReset.ttl.hours.singular"
                            : "auth.passwordReset.ttl.hours.plural",
                    new Object[]{hours},
                    locale
            );
        }

        return messageSource.getMessage(
                totalMinutes == 1
                        ? "auth.passwordReset.ttl.minutes.singular"
                        : "auth.passwordReset.ttl.minutes.plural",
                new Object[]{totalMinutes},
                locale
        );
    }

    private void revokePendingPasswordResetTokens(User user, OffsetDateTime now) {
        authActionTokenRepository
                .findAllByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfter(
                        user,
                        AuthActionTokenType.PASSWORD_RESET,
                        now
                )
                .forEach(AuthActionToken::revoke);
    }

    private void cancelPendingPasswordResetEmails(User user) {
        emailOutboxRepository
                .findAllByUserAndEmailTypeAndEmailStatus(
                        user,
                        EmailOutboxType.PASSWORD_RESET,
                        EmailOutboxStatus.PENDING
                )
                .forEach(EmailOutbox::cancel);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("auth.passwordReset.request.required");
        }

        if (request.resetToken() == null || request.resetToken().isBlank()) {
            throw new IllegalArgumentException("auth.token.required");
        }

        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new IllegalArgumentException("auth.password.new.required");
        }

        String normalizedToken = request.resetToken().trim();
        String tokenHash = tokenHashingService.hash(normalizedToken);

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHashForUpdate(tokenHash)
                .orElseThrow(PasswordResetService::invalidPasswordResetToken);

        if (actionToken.getAuthActionTokenType() != AuthActionTokenType.PASSWORD_RESET) {
            throw invalidPasswordResetToken();
        }

        if (!actionToken.isPending()) {
            throw invalidPasswordResetToken();
        }

        User user = actionToken.getUser();

        if (user.isHardDeletionDue()) {
            throw invalidPasswordResetToken();
        }

        if (passwordEncoder.matches(request.newPassword(), user.getUserPasswordHash())) {
            throw new IllegalArgumentException("auth.password.new.sameAsCurrent");
        }

        passwordPolicyService.validateChangePassword(
                request.newPassword(),
                user.getUserName(),
                user.getEmail()
        );

        String newPasswordHash = passwordEncoder.encode(request.newPassword());

        try {
            actionToken.markUsed(AuthActionTokenType.PASSWORD_RESET);
        } catch (IllegalStateException ex) {
            throw invalidPasswordResetToken();
        }

        user.changePassword(newPasswordHash);

        revokePendingPasswordResetTokens(user, OffsetDateTime.now());

        authSessionRevocationService.revokeAllForUser(
                user,
                SessionRevokeReason.PASSWORD_CHANGED,
                RefreshTokenRevokeReason.PASSWORD_CHANGED
        );
    }
}