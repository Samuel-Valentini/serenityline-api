package me.serenityline.api.auth.service;

import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.exception.Email2faConfirmationInvalidOrExpiredException;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.dto.Email2faChallengeResponse;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class Email2faManagementService {

    private static final Duration MIN_CODE_TTL = Duration.ofMinutes(1);
    private static final int CODE_UPPER_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final MessageSource messageSource;
    private final Duration codeTtl;
    private final int maxAttempts;
    private final SecureRandom secureRandom;

    public Email2faManagementService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            AuthActionTokenRepository authActionTokenRepository,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            MessageSource messageSource,
            @Value("${serenityline.auth.login-2fa.code-ttl}") Duration codeTtl,
            @Value("${serenityline.auth.login-2fa.max-attempts}") int maxAttempts
    ) {
        validateCodeTtl(codeTtl);
        validateMaxAttempts(maxAttempts);

        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
        this.codeTtl = codeTtl;
        this.maxAttempts = maxAttempts;
        this.secureRandom = new SecureRandom();
    }

    private static void validateCodeTtl(Duration codeTtl) {
        if (codeTtl == null
                || codeTtl.isZero()
                || codeTtl.isNegative()
                || codeTtl.compareTo(MIN_CODE_TTL) < 0) {
            throw new IllegalStateException("auth.email2fa.codeTtl.invalid");
        }
    }

    private static void validateMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalStateException("auth.email2fa.maxAttempts.invalid");
        }
    }

    private static Email2faConfirmationInvalidOrExpiredException invalidOrExpiredConfirmation() {
        return new Email2faConfirmationInvalidOrExpiredException();
    }

    @Transactional
    public Email2faChallengeResponse requestEnable(
            UUID userId,
            String currentPassword
    ) {
        User user = loadAuthenticatedUser(userId);

        if (user.isEmailTwoFactorEnabled()) {
            throw new IllegalStateException("auth.email2fa.alreadyEnabled");
        }

        validateCurrentPassword(user, currentPassword);

        return createChallenge(
                user,
                AuthActionTokenType.EMAIL_2FA_ENABLE_CONFIRMATION,
                EmailOutboxType.EMAIL_2FA_ENABLE_CONFIRMATION
        );
    }

    @Transactional(noRollbackFor = Email2faConfirmationInvalidOrExpiredException.class)
    public void confirmEnable(
            UUID userId,
            UUID challengeId,
            String code
    ) {
        AuthActionToken actionToken = verifyConfirmation(
                userId,
                challengeId,
                code,
                AuthActionTokenType.EMAIL_2FA_ENABLE_CONFIRMATION
        );

        User user = actionToken.getUser();

        if (user.isEmailTwoFactorEnabled()) {
            throw new IllegalStateException("auth.email2fa.alreadyEnabled");
        }

        try {
            actionToken.markUsed(AuthActionTokenType.EMAIL_2FA_ENABLE_CONFIRMATION);
        } catch (IllegalStateException ex) {
            throw invalidOrExpiredConfirmation();
        }

        user.enableEmailTwoFactorEnabled();
    }

    @Transactional
    public Email2faChallengeResponse requestDisable(
            UUID userId,
            String currentPassword
    ) {
        User user = loadAuthenticatedUser(userId);

        if (!user.isEmailTwoFactorEnabled()) {
            throw new IllegalStateException("auth.email2fa.alreadyDisabled");
        }

        validateCurrentPassword(user, currentPassword);

        return createChallenge(
                user,
                AuthActionTokenType.EMAIL_2FA_DISABLE_CONFIRMATION,
                EmailOutboxType.EMAIL_2FA_DISABLE_CONFIRMATION
        );
    }

    @Transactional(noRollbackFor = Email2faConfirmationInvalidOrExpiredException.class)
    public void confirmDisable(
            UUID userId,
            UUID challengeId,
            String code
    ) {
        AuthActionToken actionToken = verifyConfirmation(
                userId,
                challengeId,
                code,
                AuthActionTokenType.EMAIL_2FA_DISABLE_CONFIRMATION
        );

        User user = actionToken.getUser();

        if (!user.isEmailTwoFactorEnabled()) {
            throw new IllegalStateException("auth.email2fa.alreadyDisabled");
        }

        try {
            actionToken.markUsed(AuthActionTokenType.EMAIL_2FA_DISABLE_CONFIRMATION);
        } catch (IllegalStateException ex) {
            throw invalidOrExpiredConfirmation();
        }

        user.disableEmailTwoFactorEnabled();
    }

    private Email2faChallengeResponse createChallenge(
            User user,
            AuthActionTokenType tokenType,
            EmailOutboxType emailType
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        revokePendingTokens(user, tokenType, now);
        cancelPendingEmails(user, emailType);

        String code = generateCode();
        OffsetDateTime expiresAt = now.plus(codeTtl);

        String temporaryHash = tokenHashingService.hash(
                "email-2fa-management-temporary:" + secureTokenGenerator.generate()
        );

        AuthActionToken actionToken = new AuthActionToken(
                user,
                temporaryHash,
                tokenType,
                expiresAt,
                maxAttempts
        );

        authActionTokenRepository.save(actionToken);

        UUID challengeId = actionToken.getAuthActionTokenId();

        if (challengeId == null) {
            throw new IllegalStateException("auth.email2fa.challengeId.required");
        }

        String finalHash = hashCode(challengeId, code);

        actionToken.replacePendingHash(finalHash);

        EmailOutbox emailOutbox = createEmailOutbox(
                user,
                emailType,
                code,
                now
        );

        emailOutboxRepository.save(emailOutbox);

        return new Email2faChallengeResponse(
                challengeId,
                expiresAt
        );
    }

    private AuthActionToken verifyConfirmation(
            UUID userId,
            UUID challengeId,
            String rawCode,
            AuthActionTokenType expectedType
    ) {
        if (userId == null || challengeId == null) {
            throw invalidOrExpiredConfirmation();
        }

        String code = normalizeCode(rawCode);

        AuthActionToken actionToken = authActionTokenRepository
                .findByIdForUpdate(challengeId)
                .orElseThrow(Email2faManagementService::invalidOrExpiredConfirmation);

        if (actionToken.getAuthActionTokenType() != expectedType) {
            throw invalidOrExpiredConfirmation();
        }

        User user = actionToken.getUser();

        if (!Objects.equals(user.getUserId(), userId)) {
            throw invalidOrExpiredConfirmation();
        }

        if (user.isPendingDeletion() || !user.isUserIsEnabled()) {
            throw invalidOrExpiredConfirmation();
        }

        if (!actionToken.isPending() || actionToken.hasReachedFailedAttemptLimit()) {
            throw invalidOrExpiredConfirmation();
        }

        String expectedHash = hashCode(challengeId, code);

        if (!hashMatches(expectedHash, actionToken.getAuthActionTokenHash())) {
            recordFailedAttempt(actionToken);

            throw invalidOrExpiredConfirmation();
        }

        return actionToken;
    }

    private void validateCurrentPassword(
            User user,
            String currentPassword
    ) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("auth.password.current.required");
        }

        if (!passwordEncoder.matches(currentPassword, user.getUserPasswordHash())) {
            throw new IllegalArgumentException("auth.password.current.invalid");
        }
    }

    private User loadAuthenticatedUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("auth.authentication.user.required");
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("auth.authentication.user.invalid"));

        if (user.isPendingDeletion() || !user.isUserIsEnabled()) {
            throw new IllegalArgumentException("auth.authentication.user.invalid");
        }

        return user;
    }

    private void recordFailedAttempt(AuthActionToken actionToken) {
        try {
            actionToken.recordFailedAttempt();
        } catch (IllegalStateException ex) {
            throw invalidOrExpiredConfirmation();
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw invalidOrExpiredConfirmation();
        }

        String normalizedCode = code.trim();

        if (!normalizedCode.matches("\\d{6}")) {
            throw invalidOrExpiredConfirmation();
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

    private void revokePendingTokens(
            User user,
            AuthActionTokenType tokenType,
            OffsetDateTime now
    ) {
        authActionTokenRepository
                .findAllByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfter(
                        user,
                        tokenType,
                        now
                )
                .forEach(AuthActionToken::revoke);
    }

    private void cancelPendingEmails(
            User user,
            EmailOutboxType emailType
    ) {
        emailOutboxRepository
                .findAllByUserAndEmailTypeAndEmailStatus(
                        user,
                        emailType,
                        EmailOutboxStatus.PENDING
                )
                .forEach(EmailOutbox::cancel);
    }

    private EmailOutbox createEmailOutbox(
            User user,
            EmailOutboxType emailType,
            String code,
            OffsetDateTime scheduledAt
    ) {
        String subject = buildSubject(user, emailType);
        String textBody = buildTextBody(user, emailType, code);

        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(subject);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(textBody);

        return new EmailOutbox(
                user,
                user.getEmail(),
                emailType,
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

    private String buildSubject(
            User user,
            EmailOutboxType emailType
    ) {
        String messageKey = switch (emailType) {
            case EMAIL_2FA_ENABLE_CONFIRMATION -> "auth.email2fa.enable.email.subject";
            case EMAIL_2FA_DISABLE_CONFIRMATION -> "auth.email2fa.disable.email.subject";
            default -> throw new IllegalStateException("auth.email2fa.emailType.invalid");
        };

        return messageSource.getMessage(
                messageKey,
                null,
                resolveUserLocale(user)
        );
    }

    private String buildTextBody(
            User user,
            EmailOutboxType emailType,
            String code
    ) {
        String messageKey = switch (emailType) {
            case EMAIL_2FA_ENABLE_CONFIRMATION -> "auth.email2fa.enable.email.body.text";
            case EMAIL_2FA_DISABLE_CONFIRMATION -> "auth.email2fa.disable.email.body.text";
            default -> throw new IllegalStateException("auth.email2fa.emailType.invalid");
        };

        Locale locale = resolveUserLocale(user);

        return messageSource.getMessage(
                messageKey,
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
            throw new IllegalStateException("auth.email2fa.codeTtl.invalid");
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