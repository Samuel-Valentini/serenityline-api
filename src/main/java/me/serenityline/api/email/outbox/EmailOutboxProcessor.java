package me.serenityline.api.email.outbox;

import me.serenityline.api.auth.entity.EmailOutbox;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.email.service.EmailSendException;
import me.serenityline.api.email.service.EmailSender;
import me.serenityline.api.email.service.OutboundEmail;
import me.serenityline.api.email.service.SentEmail;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@ConditionalOnProperty(
        prefix = "serenityline.email.outbox-worker",
        name = "enabled",
        havingValue = "true"
)
public class EmailOutboxProcessor {

    private static final int LAST_ERROR_MAX_LENGTH = 2000;

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final EmailSender emailSender;
    private final int batchSize;
    private final Duration retryDelay;

    public EmailOutboxProcessor(
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            EmailSender emailSender,
            @Value("${serenityline.email.outbox-worker.batch-size:20}") int batchSize,
            @Value("${serenityline.email.outbox-worker.retry-delay:5m}") Duration retryDelay
    ) {
        if (batchSize <= 0) {
            throw new IllegalStateException("emailOutbox.worker.batchSize.invalid");
        }

        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalStateException("emailOutbox.worker.retryDelay.invalid");
        }

        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.emailSender = Objects.requireNonNull(emailSender, "emailSender");
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
    }

    @Transactional
    public int processDueEmails() {
        OffsetDateTime now = OffsetDateTime.now();

        List<EmailOutbox> pendingEmails = emailOutboxRepository.findPendingDueForUpdate(
                now,
                batchSize
        );

        pendingEmails.forEach(this::processOne);

        return pendingEmails.size();
    }

    private void processOne(EmailOutbox emailOutbox) {
        try {
            OutboundEmail outboundEmail = decrypt(emailOutbox);

            SentEmail sentEmail = emailSender.send(outboundEmail);

            emailOutbox.markSent(
                    emailSender.provider(),
                    sentEmail == null ? null : sentEmail.providerMessageId()
            );

        } catch (Exception ex) {
            OffsetDateTime nextScheduledAt = OffsetDateTime.now().plus(retryDelay);

            emailOutbox.markFailed(
                    safeLastError(ex),
                    nextScheduledAt
            );
        }
    }

    private OutboundEmail decrypt(EmailOutbox emailOutbox) {
        String subject = decryptRequired(
                emailOutbox.getSubjectEncrypted(),
                emailOutbox.getSubjectIv(),
                emailOutbox.getSubjectTag()
        );

        String text = decryptNullable(
                emailOutbox.getBodyTextEncrypted(),
                emailOutbox.getBodyTextIv(),
                emailOutbox.getBodyTextTag()
        );

        String html = decryptNullable(
                emailOutbox.getBodyHtmlEncrypted(),
                emailOutbox.getBodyHtmlIv(),
                emailOutbox.getBodyHtmlTag()
        );

        return new OutboundEmail(
                emailOutbox.getRecipientEmail(),
                subject,
                text,
                html
        );
    }

    private String decryptRequired(byte[] encrypted, byte[] iv, byte[] tag) {
        if (encrypted == null || iv == null || tag == null) {
            throw new IllegalStateException("emailOutbox.encryptedValue.required");
        }

        return emailOutboxEncryptionService.decrypt(
                new EncryptedValue(encrypted, iv, tag)
        );
    }

    private String decryptNullable(byte[] encrypted, byte[] iv, byte[] tag) {
        boolean allNull = encrypted == null && iv == null && tag == null;
        boolean allPresent = encrypted != null && iv != null && tag != null;

        if (allNull) {
            return null;
        }

        if (!allPresent) {
            throw new IllegalStateException("emailOutbox.encryptedValue.incomplete");
        }

        return emailOutboxEncryptionService.decrypt(
                new EncryptedValue(encrypted, iv, tag)
        );
    }

    private String safeLastError(Exception ex) {
        if (ex instanceof EmailSendException emailSendException) {
            return truncate(emailSendException.getSafeMessage());
        }

        return truncate(ex.getClass().getSimpleName());
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() <= LAST_ERROR_MAX_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}