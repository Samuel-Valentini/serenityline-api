package me.serenityline.api.support.contact.service;

import me.serenityline.api.email.outbox.EmailOutboxSentEvent;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class SupportContactConfirmationOutboxService {

    private static final Logger log = LoggerFactory.getLogger(SupportContactConfirmationOutboxService.class);
    private static final String DEFAULT_LOCALE = "it-IT";

    private final EmailOutboxRepository emailOutboxRepository;
    private final UserRepository userRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final SupportContactProperties properties;
    private final MessageSource messageSource;

    public SupportContactConfirmationOutboxService(
            EmailOutboxRepository emailOutboxRepository,
            UserRepository userRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            SupportContactProperties properties,
            MessageSource messageSource
    ) {
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueConfirmationAfterSupportNotificationSent(EmailOutboxSentEvent event) {
        try {
            handle(event);
        } catch (Exception ex) {
            log.warn(
                    "Failed to enqueue support contact confirmation after support notification was sent: emailOutboxId={}, userId={}",
                    event == null ? null : event.emailOutboxId(),
                    event == null ? null : event.userId(),
                    ex
            );
        }
    }

    private void handle(EmailOutboxSentEvent event) {
        if (event == null || event.emailType() != EmailOutboxType.SUPPORT_CONTACT) {
            return;
        }

        if (event.userId() == null) {
            return;
        }

        if (!properties.getRecipientEmail().equals(event.recipientEmail())) {
            return;
        }

        User user = userRepository.findById(event.userId())
                .orElse(null);

        if (user == null) {
            return;
        }

        enqueueConfirmation(event.emailOutboxId(), user);
    }

    private void enqueueConfirmation(UUID supportNotificationId, User user) {
        Locale locale = resolveUserLocale(user);
        String reference = shortReference(supportNotificationId);

        String subject = messageSource.getMessage(
                "support.contact.confirmation.subject",
                new Object[]{reference},
                locale
        );

        String body = messageSource.getMessage(
                "support.contact.confirmation.body.text",
                new Object[]{user.getUserName(), reference},
                locale
        );

        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(subject);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(body);

        EmailOutbox confirmationEmail = new EmailOutbox(
                user,
                user.getEmail(),
                EmailOutboxType.SUPPORT_CONTACT_CONFIRMATION,
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
                OffsetDateTime.now()
        );

        emailOutboxRepository.save(confirmationEmail);
    }

    private Locale resolveUserLocale(User user) {
        String preferredLocale = user.getPreferredLocale();

        if (preferredLocale == null || preferredLocale.isBlank()) {
            return Locale.forLanguageTag(DEFAULT_LOCALE);
        }

        return Locale.forLanguageTag(preferredLocale);
    }

    private String shortReference(UUID emailOutboxId) {
        return emailOutboxId
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }
}