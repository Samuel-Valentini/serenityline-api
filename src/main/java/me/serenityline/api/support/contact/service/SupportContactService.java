package me.serenityline.api.support.contact.service;

import jakarta.persistence.EntityManager;
import me.serenityline.api.common.error.BadRequestException;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.auth.AuthenticatedUser;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.support.contact.dto.SupportContactRequest;
import me.serenityline.api.user.entity.User;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SupportContactService {

    private static final int SUBJECT_VISIBLE_MAX_LENGTH = 96;
    private static final int USER_AGENT_MAX_LENGTH = 500;
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String DEFAULT_LOCALE = "it-IT";

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final SupportContactProperties properties;
    private final InMemorySupportContactRateLimiter rateLimiter;
    private final MessageSource messageSource;
    private final EntityManager entityManager;

    public SupportContactService(
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            SupportContactProperties properties,
            InMemorySupportContactRateLimiter rateLimiter,
            MessageSource messageSource,
            EntityManager entityManager
    ) {
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Transactional
    public void submit(
            SupportContactRequest request,
            AuthenticatedUser authenticatedUser,
            String remoteAddress,
            String userAgent
    ) {
        Objects.requireNonNull(request, "request");

        if (hasText(request.website())) {
            return;
        }

        SupportContactIdentity identity = resolveIdentity(request, authenticatedUser);
        applyRateLimit(identity, remoteAddress);

        EncryptedValue temporarySubject = emailOutboxEncryptionService.encrypt("[SL-SUPPORT] Richiesta di supporto");
        EncryptedValue temporaryTextBody = emailOutboxEncryptionService.encrypt("Richiesta di supporto in preparazione.");

        EmailOutbox emailOutbox = new EmailOutbox(
                identity.user(),
                properties.getRecipientEmail(),
                EmailOutboxType.SUPPORT_CONTACT,
                emailOutboxEncryptionService.getEncryptionKeyId(),
                temporarySubject.encrypted(),
                temporarySubject.iv(),
                temporarySubject.tag(),
                null,
                null,
                null,
                temporaryTextBody.encrypted(),
                temporaryTextBody.iv(),
                temporaryTextBody.tag(),
                false,
                OffsetDateTime.now()
        );

        EmailOutbox savedEmailOutbox = emailOutboxRepository.saveAndFlush(emailOutbox);

        UUID emailOutboxId = savedEmailOutbox.getEmailOutboxId();
        String shortReference = shortReference(emailOutboxId);

        String subject = buildInternalSubject(shortReference, request.subject());
        String body = buildInternalTextBody(
                emailOutboxId,
                shortReference,
                request,
                identity,
                remoteAddress,
                userAgent
        );

        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(subject);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(body);

        savedEmailOutbox.replaceEncryptedContent(
                encryptedSubject.encrypted(),
                encryptedSubject.iv(),
                encryptedSubject.tag(),
                null,
                null,
                null,
                encryptedTextBody.encrypted(),
                encryptedTextBody.iv(),
                encryptedTextBody.tag()
        );
    }

    private SupportContactIdentity resolveIdentity(
            SupportContactRequest request,
            AuthenticatedUser authenticatedUser
    ) {
        if (authenticatedUser != null) {
            User user = entityManager.getReference(User.class, authenticatedUser.userId());

            return new SupportContactIdentity(
                    user,
                    authenticatedUser.userId(),
                    authenticatedUser.userGroupId(),
                    authenticatedUser.userName(),
                    authenticatedUser.email(),
                    true,
                    authenticatedUser.preferredLocale()
            );
        }

        String email = normalizeAnonymousEmail(request.email());
        String name = normalizeOptionalText(request.name(), 120, "support.contact.name.tooLong");

        return new SupportContactIdentity(
                null,
                null,
                null,
                name,
                email,
                false,
                DEFAULT_LOCALE
        );
    }

    private String normalizeAnonymousEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("support.contact.email.required");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (normalized.length() > 320) {
            throw new BadRequestException("support.contact.email.tooLong");
        }

        if (!BASIC_EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("support.contact.email.invalid");
        }

        return normalized;
    }

    private void applyRateLimit(SupportContactIdentity identity, String remoteAddress) {
        String ip = normalizeRateLimitValue(remoteAddress, "unknown");

        rateLimiter.check("ip:" + ip);

        if (identity.authenticated()) {
            rateLimiter.check("user:" + identity.userId());
            return;
        }

        rateLimiter.check("email:" + identity.email());
    }

    private String normalizeRateLimitValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String buildInternalSubject(String shortReference, String rawSubject) {
        return "[SL-SUPPORT " + shortReference + "] " + sanitizeSubject(rawSubject);
    }

    private String buildInternalTextBody(
            UUID emailOutboxId,
            String shortReference,
            SupportContactRequest request,
            SupportContactIdentity identity,
            String remoteAddress,
            String userAgent
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("Nuova richiesta di supporto SerenityLine\n\n");
        builder.append("Riferimento breve: ").append(shortReference).append('\n');
        builder.append("Email outbox ID: ").append(emailOutboxId).append('\n');
        builder.append("Origine: ").append(identity.authenticated() ? "utente autenticato" : "form pubblico anonimo").append('\n');

        if (identity.userId() != null) {
            builder.append("User ID: ").append(identity.userId()).append('\n');
        }

        if (identity.userGroupId() != null) {
            builder.append("User group ID: ").append(identity.userGroupId()).append('\n');
        }

        appendLine(builder, "Nome", identity.name());
        appendLine(builder, "Email", identity.email());

        builder.append("Argomento: ").append(request.topic()).append('\n');
        builder.append("Oggetto: ").append(sanitizeSingleLine(request.subject())).append('\n');

        appendLine(builder, "Locale", identity.locale());
        appendLine(builder, "Remote address", remoteAddress);
        appendLine(builder, "User-Agent", truncateSingleLine(userAgent, USER_AGENT_MAX_LENGTH));

        builder.append("\nMessaggio:\n");
        builder.append(sanitizeMultiline(request.message())).append('\n');

        return builder.toString();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (!hasText(value)) {
            return;
        }

        builder.append(label)
                .append(": ")
                .append(sanitizeSingleLine(value))
                .append('\n');
    }

    private String sanitizeSubject(String value) {
        String sanitized = sanitizeSingleLine(value);

        if (sanitized.length() > SUBJECT_VISIBLE_MAX_LENGTH) {
            return sanitized.substring(0, SUBJECT_VISIBLE_MAX_LENGTH).trim() + "...";
        }

        return sanitized;
    }

    private String sanitizeSingleLine(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
                .trim()
                .replaceAll("[ \\t]+", " ");
    }

    private String sanitizeMultiline(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
                .trim();
    }

    private String truncateSingleLine(String value, int maxLength) {
        String sanitized = sanitizeSingleLine(value);

        if (sanitized.isBlank()) {
            return null;
        }

        if (sanitized.length() <= maxLength) {
            return sanitized;
        }

        return sanitized.substring(0, maxLength);
    }

    private String normalizeOptionalText(String value, int maxLength, String tooLongKey) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() > maxLength) {
            throw new BadRequestException(tooLongKey);
        }

        return normalized;
    }

    private String shortReference(UUID emailOutboxId) {
        return emailOutboxId
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String acceptedMessage(Locale locale) {
        return messageSource.getMessage(
                "support.contact.accepted",
                null,
                resolveLocale(locale)
        );
    }

    private Locale resolveLocale(Locale locale) {
        return locale == null ? Locale.forLanguageTag(DEFAULT_LOCALE) : locale;
    }

    private record SupportContactIdentity(
            User user,
            UUID userId,
            UUID userGroupId,
            String name,
            String email,
            boolean authenticated,
            String locale
    ) {
        private SupportContactIdentity {
            if (email == null || email.isBlank()) {
                throw new BadRequestException("support.contact.email.required");
            }
        }
    }
}