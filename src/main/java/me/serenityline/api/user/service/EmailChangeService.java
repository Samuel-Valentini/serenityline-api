package me.serenityline.api.user.service;

import me.serenityline.api.auth.entity.*;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.auth.service.AuthSessionRevocationService;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.dto.RequestEmailChangeRequest;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class EmailChangeService {

    private static final int EMAIL_MAX_LENGTH = 320;
    private static final Duration MIN_TOKEN_TTL = Duration.ofMinutes(1);
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRevocationService authSessionRevocationService;
    private final MessageSource messageSource;
    private final Duration tokenTtl;
    private final String frontendBaseUrl;

    public EmailChangeService(
            UserRepository userRepository,
            AuthActionTokenRepository authActionTokenRepository,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            PasswordEncoder passwordEncoder,
            AuthSessionRevocationService authSessionRevocationService,
            MessageSource messageSource,
            @Value("${serenityline.auth.email-change.token-ttl:30m}") Duration tokenTtl,
            @Value("${serenityline.frontend.base-url}") String frontendBaseUrl
    ) {
        validateTokenTtl(tokenTtl);

        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.authSessionRevocationService = Objects.requireNonNull(authSessionRevocationService, "authSessionRevocationService");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
        this.tokenTtl = tokenTtl;
        this.frontendBaseUrl = normalizeFrontendBaseUrl(frontendBaseUrl);
    }

    private static String requireEmailChangeTargetValue(AuthActionToken actionToken) {
        try {
            return actionToken.requireEmailChangeTargetValue();
        } catch (RuntimeException ex) {
            throw invalidOrExpiredEmailChangeToken();
        }
    }

    private static String normalizeAndValidateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("auth.emailChange.newEmail.required");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (normalizedEmail.length() > EMAIL_MAX_LENGTH) {
            throw new IllegalArgumentException("user.email.tooLong");
        }

        if (!BASIC_EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("user.email.invalid");
        }

        return normalizedEmail;
    }

    private static void validateTokenTtl(Duration tokenTtl) {
        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()
                || tokenTtl.compareTo(MIN_TOKEN_TTL) < 0) {
            throw new IllegalStateException("auth.emailChange.tokenTtl.invalid");
        }
    }

    private static String normalizeFrontendBaseUrl(String frontendBaseUrl) {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalStateException("auth.emailChange.frontendBaseUrl.required");
        }

        String normalized = frontendBaseUrl.trim();

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalStateException("auth.emailChange.frontendBaseUrl.invalid");
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private static IllegalArgumentException invalidOrExpiredEmailChangeToken() {
        return new IllegalArgumentException("auth.emailChange.invalidOrExpired");
    }

    @Transactional
    public void requestEmailChange(UUID userId, RequestEmailChangeRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("auth.emailChange.user.required");
        }

        if (request == null) {
            throw new IllegalArgumentException("auth.emailChange.request.required");
        }

        User user = userRepository.findActiveUserByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("auth.emailChange.userNotFound"));

        validateCurrentPassword(request.currentPassword(), user);

        String newEmail = normalizeAndValidateEmail(request.newEmail());

        if (newEmail.equals(user.getEmail())) {
            throw new IllegalArgumentException("auth.emailChange.sameEmail");
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("auth.emailChange.emailAlreadyInUse");
        }

        OffsetDateTime now = OffsetDateTime.now();

        cancelPendingEmailChangeEmails(user);
        revokePendingEmailChangeTokens(user, now);

        String plainToken = secureTokenGenerator.generate();
        String tokenHash = tokenHashingService.hash(plainToken);
        OffsetDateTime expiresAt = now.plus(tokenTtl);

        AuthActionToken actionToken = new AuthActionToken(
                user,
                tokenHash,
                AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION,
                expiresAt
        );

        actionToken.setEmailChangeTargetValue(newEmail);

        authActionTokenRepository.save(actionToken);

        EmailOutbox emailOutbox = createConfirmationEmailOutbox(
                user,
                newEmail,
                plainToken,
                now
        );

        emailOutboxRepository.save(emailOutbox);
    }

    @Transactional
    public void confirmEmailChange(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw invalidOrExpiredEmailChangeToken();
        }

        String tokenHash = tokenHashingService.hash(plainToken.trim());

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHashForUpdate(tokenHash)
                .orElseThrow(EmailChangeService::invalidOrExpiredEmailChangeToken);

        if (actionToken.getAuthActionTokenType() != AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION) {
            throw invalidOrExpiredEmailChangeToken();
        }

        if (!actionToken.isPending()) {
            throw invalidOrExpiredEmailChangeToken();
        }

        User user = actionToken.getUser();

        if (user == null || user.isPendingDeletion() || !user.isUserIsEnabled()) {
            throw invalidOrExpiredEmailChangeToken();
        }

        String newEmail = requireEmailChangeTargetValue(actionToken);

        if (newEmail.equals(user.getEmail())) {
            throw new IllegalArgumentException("auth.emailChange.sameEmail");
        }

        if (userRepository.existsByEmailAndUserIdNot(newEmail, user.getUserId())) {
            throw new IllegalArgumentException("auth.emailChange.emailAlreadyInUse");
        }

        try {
            actionToken.markUsed(AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION);
        } catch (IllegalStateException ex) {
            throw invalidOrExpiredEmailChangeToken();
        }

        try {
            user.setEmail(newEmail);
            user.incrementTokenVersion();

            authSessionRevocationService.revokeAllForUser(
                    user,
                    SessionRevokeReason.EMAIL_CHANGED,
                    RefreshTokenRevokeReason.EMAIL_CHANGED
            );

            userRepository.flush();

        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("auth.emailChange.emailAlreadyInUse", ex);
        }
    }

    private void validateCurrentPassword(String currentPassword, User user) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("auth.emailChange.currentPassword.required");
        }

        if (!passwordEncoder.matches(currentPassword, user.getUserPasswordHash())) {
            throw new IllegalArgumentException("auth.emailChange.currentPassword.invalid");
        }
    }

    private EmailOutbox createConfirmationEmailOutbox(
            User user,
            String newEmail,
            String plainToken,
            OffsetDateTime scheduledAt
    ) {
        String subject = buildConfirmationSubject(user);
        String textBody = buildConfirmationTextBody(user, newEmail, plainToken);

        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(subject);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(textBody);

        return new EmailOutbox(
                user,
                newEmail,
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
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

    private String buildConfirmationSubject(User user) {
        return messageSource.getMessage(
                "auth.emailChange.email.subject",
                null,
                resolveUserLocale(user)
        );
    }

    private String buildConfirmationTextBody(User user, String newEmail, String plainToken) {
        Locale locale = resolveUserLocale(user);

        return messageSource.getMessage(
                "auth.emailChange.email.body.text",
                new Object[]{
                        user.getUserName(),
                        user.getEmail(),
                        newEmail,
                        buildConfirmationUrl(plainToken),
                        buildManualConfirmationUrl(),
                        plainToken,
                        formatTokenTtl(locale)
                },
                locale
        );
    }

    private String buildConfirmationUrl(String plainToken) {
        return frontendBaseUrl + "/change-email/confirm#token=" + plainToken;
    }

    private String buildManualConfirmationUrl() {
        return frontendBaseUrl + "/change-email/confirm";
    }

    private void revokePendingEmailChangeTokens(User user, OffsetDateTime now) {
        authActionTokenRepository
                .findAllByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfter(
                        user,
                        AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION,
                        now
                )
                .forEach(AuthActionToken::revoke);
    }

    private void cancelPendingEmailChangeEmails(User user) {
        emailOutboxRepository
                .findAllByUserAndEmailTypeAndEmailStatus(
                        user,
                        EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                        EmailOutboxStatus.PENDING
                )
                .forEach(EmailOutbox::cancel);
    }

    private Locale resolveUserLocale(User user) {
        String preferredLocale = user.getPreferredLocale();

        if (preferredLocale == null || preferredLocale.isBlank()) {
            return Locale.forLanguageTag("it-IT");
        }

        return Locale.forLanguageTag(preferredLocale);
    }

    private String formatTokenTtl(Locale locale) {
        long totalMinutes = tokenTtl.toMinutes();

        if (totalMinutes <= 0) {
            throw new IllegalStateException("auth.emailChange.tokenTtl.invalid");
        }

        if (totalMinutes % (24 * 60) == 0) {
            long days = totalMinutes / (24 * 60);

            return messageSource.getMessage(
                    days == 1
                            ? "auth.emailChange.ttl.days.singular"
                            : "auth.emailChange.ttl.days.plural",
                    new Object[]{days},
                    locale
            );
        }

        if (totalMinutes % 60 == 0) {
            long hours = totalMinutes / 60;

            return messageSource.getMessage(
                    hours == 1
                            ? "auth.emailChange.ttl.hours.singular"
                            : "auth.emailChange.ttl.hours.plural",
                    new Object[]{hours},
                    locale
            );
        }

        return messageSource.getMessage(
                totalMinutes == 1
                        ? "auth.emailChange.ttl.minutes.singular"
                        : "auth.emailChange.ttl.minutes.plural",
                new Object[]{totalMinutes},
                locale
        );
    }
}