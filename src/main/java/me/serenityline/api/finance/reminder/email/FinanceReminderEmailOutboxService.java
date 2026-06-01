package me.serenityline.api.finance.reminder.email;

import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotification;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotificationRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class FinanceReminderEmailOutboxService {

    private final FinanceReminderNotificationRepository notificationRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final UserRepository userRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final MessageSource messageSource;
    private final Clock clock;

    public FinanceReminderEmailOutboxService(
            FinanceReminderNotificationRepository notificationRepository,
            EmailOutboxRepository emailOutboxRepository,
            UserRepository userRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            MessageSource messageSource,
            Clock clock
    ) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public UUID ensureEmailOutboxForNotification(UUID financeReminderNotificationId) {
        if (financeReminderNotificationId == null) {
            throw new IllegalArgumentException("finance.reminderNotification.id.required");
        }

        FinanceReminderNotification notification = notificationRepository
                .findByIdForUpdate(financeReminderNotificationId)
                .orElseThrow(() -> new IllegalArgumentException("finance.reminderNotification.notFound"));

        if (notification.getEmailOutboxId() != null) {
            return notification.getEmailOutboxId();
        }

        if (notification.getEmailFinalStatus() != null) {
            throw new IllegalStateException("finance.reminderNotification.emailFinalStatus.alreadyRecorded");
        }

        User user = userRepository
                .findActiveUserByIdForUpdate(notification.getUserId())
                .orElseThrow(() -> new IllegalStateException("finance.reminderEmail.userNotFound"));

        if (!user.isPaymentEmailRemindersEnabled()) {
            throw new IllegalStateException("finance.reminderEmail.paymentEmailRemindersDisabled");
        }

        OffsetDateTime scheduledAt = OffsetDateTime.now(clock);

        EmailOutbox emailOutbox = createEmailOutbox(
                notification,
                user,
                scheduledAt
        );

        EmailOutbox savedEmailOutbox = emailOutboxRepository.saveAndFlush(emailOutbox);

        notification.attachEmailOutbox(savedEmailOutbox.getEmailOutboxId());

        notificationRepository.flush();

        return savedEmailOutbox.getEmailOutboxId();
    }

    private EmailOutbox createEmailOutbox(
            FinanceReminderNotification notification,
            User user,
            OffsetDateTime scheduledAt
    ) {
        EmailOutboxType emailType = resolveEmailOutboxType(notification);

        String subject = buildSubject(emailType, user);
        String textBody = buildTextBody(notification, user, emailType);

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

    private EmailOutboxType resolveEmailOutboxType(FinanceReminderNotification notification) {
        if (notification.getTransactionId() != null) {
            return EmailOutboxType.TRANSACTION_REMINDER;
        }

        if (notification.getRecurringTransactionId() != null) {
            return EmailOutboxType.RECURRING_TRANSACTION_REMINDER;
        }

        throw new IllegalStateException("finance.reminderNotification.source.invalid");
    }

    private String buildSubject(
            EmailOutboxType emailType,
            User user
    ) {
        String messageKey = switch (emailType) {
            case TRANSACTION_REMINDER -> "finance.reminder.email.transaction.subject";
            case RECURRING_TRANSACTION_REMINDER -> "finance.reminder.email.recurring.subject";
            default -> throw new IllegalStateException("finance.reminderEmail.emailType.invalid");
        };

        return messageSource.getMessage(
                messageKey,
                null,
                resolveUserLocale(user)
        );
    }

    private String buildTextBody(
            FinanceReminderNotification notification,
            User user,
            EmailOutboxType emailType
    ) {
        Locale locale = resolveUserLocale(user);

        String messageKey = switch (emailType) {
            case TRANSACTION_REMINDER -> "finance.reminder.email.transaction.body.text";
            case RECURRING_TRANSACTION_REMINDER -> "finance.reminder.email.recurring.body.text";
            default -> throw new IllegalStateException("finance.reminderEmail.emailType.invalid");
        };

        return messageSource.getMessage(
                messageKey,
                new Object[]{
                        user.getUserName(),
                        notification.getNotifiedDescription(),
                        formatAmount(
                                notification.getNotifiedAmount(),
                                notification.getNotifiedCurrency(),
                                locale
                        ),
                        formatDate(notification.getChargeDate(), locale),
                        formatDate(notification.getReminderDate(), locale),
                        notification.getRecurringTransactionLogicalDate() == null
                                ? null
                                : formatDate(notification.getRecurringTransactionLogicalDate(), locale)
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

    private String formatAmount(
            BigDecimal amount,
            String currencyCode,
            Locale locale
    ) {
        if (amount == null) {
            throw new IllegalStateException("finance.reminderNotification.notifiedAmount.required");
        }

        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalStateException("finance.reminderNotification.notifiedCurrency.required");
        }

        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
        formatter.setCurrency(Currency.getInstance(currencyCode));

        return formatter.format(amount);
    }

    private String formatDate(
            LocalDate date,
            Locale locale
    ) {
        if (date == null) {
            throw new IllegalStateException("finance.reminderNotification.date.required");
        }

        return date.format(
                DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(locale)
        );
    }
}