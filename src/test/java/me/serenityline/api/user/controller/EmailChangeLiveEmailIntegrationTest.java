package me.serenityline.api.user.controller;

import jakarta.persistence.EntityManager;
import me.serenityline.api.email.outbox.EmailOutboxProcessor;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.email.service.EmailSender;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.user.dto.RequestEmailChangeRequest;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import me.serenityline.api.user.service.EmailChangeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "serenityline.email.provider=resend",
        "serenityline.email.outbox-worker.enabled=false",
        "serenityline.email.outbox-worker.batch-size=10",
        "serenityline.email.outbox-worker.retry-delay=1m",
        "serenityline.frontend.base-url=http://localhost:5173"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")
@EnabledIfSystemProperty(
        named = "serenityline.tests.real-email.enabled",
        matches = "true"
)
class EmailChangeLiveEmailIntegrationTest {

    private static final String OLD_EMAIL = "test@serenityline.me";
    private static final String NEW_EMAIL = "test2@serenityline.me";

    private static final String PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String DEVICE_LABEL = "Live email smoke test";

    private static final Pattern EMAIL_CHANGE_TOKEN_FRAGMENT_PATTERN =
            Pattern.compile("#token=([^\\s]+)");

    private static final Pattern EMAIL_CHANGE_TOKEN_TEXT_PATTERN =
            Pattern.compile("(?i)(?:confirmation token|token di conferma):\\s*([^\\s]+)");

    private final EmailChangeService emailChangeService;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final EmailSender emailSender;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    EmailChangeLiveEmailIntegrationTest(
            EmailChangeService emailChangeService,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            EmailSender emailSender,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager
    ) {
        this.emailChangeService = emailChangeService;
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailOutboxEncryptionService = emailOutboxEncryptionService;
        this.emailSender = emailSender;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private static String extractTokenFromBody(String body) {
        if (body == null || body.isBlank()) {
            throw new AssertionError("Email body is empty");
        }

        Matcher fragmentMatcher = EMAIL_CHANGE_TOKEN_FRAGMENT_PATTERN.matcher(body);

        if (fragmentMatcher.find()) {
            return fragmentMatcher.group(1).trim();
        }

        Matcher textMatcher = EMAIL_CHANGE_TOKEN_TEXT_PATTERN.matcher(body);

        if (textMatcher.find()) {
            return textMatcher.group(1).trim();
        }

        throw new AssertionError("Email change token not found in email body:\n" + body);
    }

    private static void assertSentAndBodyDeleted(EmailOutbox emailOutbox) {
        assertThat(emailOutbox.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.SENT);

        assertThat(emailOutbox.getEmailSentAt())
                .isNotNull();

        assertThat(emailOutbox.getProvider())
                .isEqualTo("resend");

        assertThat(emailOutbox.getProviderMessageId())
                .isNotBlank();

        assertThat(emailOutbox.getEmailBodyDeletedAt())
                .isNotNull();

        assertThat(emailOutbox.getBodyTextEncrypted())
                .isNull();

        assertThat(emailOutbox.getBodyTextIv())
                .isNull();

        assertThat(emailOutbox.getBodyTextTag())
                .isNull();
    }

    @Test
    void liveEmailChangeFlowShouldSendConfirmationAndNotificationEmailsWithResend() {
        assertThat(System.getenv("RESEND_API_KEY"))
                .as("RESEND_API_KEY must be configured for live email test")
                .isNotBlank();

        assertThat(emailSender.provider())
                .as("This test must use the real Resend EmailSender, not the fake sender")
                .isEqualTo("resend");

        cleanupLiveTestData();

        UUID userId = createVerifiedLiveTestUser();

        emailChangeService.requestEmailChange(
                userId,
                new RequestEmailChangeRequest(
                        NEW_EMAIL,
                        PASSWORD
                )
        );

        EmailOutbox confirmationBeforeSend = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                NEW_EMAIL
        );

        String confirmationBody = decryptTextBody(confirmationBeforeSend);
        String confirmationToken = extractTokenFromBody(confirmationBody);

        assertThat(confirmationBeforeSend.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(confirmationBody)
                .contains(OLD_EMAIL)
                .contains(NEW_EMAIL)
                .contains("#token=");

        int firstProcessedCount = processDueEmails();

        assertThat(firstProcessedCount)
                .isEqualTo(1);

        EmailOutbox confirmationAfterSend = emailOutboxById(
                confirmationBeforeSend.getEmailOutboxId()
        );

        assertSentAndBodyDeleted(confirmationAfterSend);

        emailChangeService.confirmEmailChange(confirmationToken);

        User userAfterConfirm = userById(userId);

        assertThat(userAfterConfirm.getEmail())
                .isEqualTo(NEW_EMAIL);

        EmailOutbox oldEmailNotificationBeforeSend = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                OLD_EMAIL
        );

        EmailOutbox newEmailNotificationBeforeSend = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                NEW_EMAIL
        );

        String oldEmailNotificationBody = decryptTextBody(oldEmailNotificationBeforeSend);
        String newEmailNotificationBody = decryptTextBody(newEmailNotificationBeforeSend);

        assertThat(oldEmailNotificationBeforeSend.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(newEmailNotificationBeforeSend.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(oldEmailNotificationBody)
                .contains(OLD_EMAIL)
                .contains(NEW_EMAIL)
                .doesNotContain(confirmationToken)
                .doesNotContain("#token=");

        assertThat(newEmailNotificationBody)
                .contains(OLD_EMAIL)
                .contains(NEW_EMAIL)
                .doesNotContain(confirmationToken)
                .doesNotContain("#token=");

        int secondProcessedCount = processDueEmails();

        assertThat(secondProcessedCount)
                .isEqualTo(2);

        EmailOutbox oldEmailNotificationAfterSend = emailOutboxById(
                oldEmailNotificationBeforeSend.getEmailOutboxId()
        );

        EmailOutbox newEmailNotificationAfterSend = emailOutboxById(
                newEmailNotificationBeforeSend.getEmailOutboxId()
        );

        assertSentAndBodyDeleted(oldEmailNotificationAfterSend);
        assertSentAndBodyDeleted(newEmailNotificationAfterSend);
    }

    private int processDueEmails() {
        EmailOutboxProcessor processor = new EmailOutboxProcessor(
                emailOutboxRepository,
                emailOutboxEncryptionService,
                emailSender,
                10,
                Duration.ofMinutes(1)
        );

        return transactionTemplate.execute(status -> processor.processDueEmails());
    }

    private UUID createVerifiedLiveTestUser() {
        return transactionTemplate.execute(status -> {
            UserGroup userGroup = new UserGroup("Live email smoke test " + UUID.randomUUID());

            entityManager.persist(userGroup);

            User user = new User(
                    "Live Email Smoke Test",
                    OLD_EMAIL,
                    userGroup,
                    UserRole.OWNER,
                    passwordEncoder.encode(PASSWORD)
            );

            user.setUserIsEnabled(true);

            entityManager.persist(user);
            entityManager.flush();

            return user.getUserId();
        });
    }

    private void cleanupLiveTestData() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            delete from email_outbox
                            where recipient_email in (:oldEmail, :newEmail)
                            """)
                    .setParameter("oldEmail", OLD_EMAIL)
                    .setParameter("newEmail", NEW_EMAIL)
                    .executeUpdate();

            entityManager.createNativeQuery("""
                            delete from users
                            where email in (:oldEmail, :newEmail)
                            """)
                    .setParameter("oldEmail", OLD_EMAIL)
                    .setParameter("newEmail", NEW_EMAIL)
                    .executeUpdate();

            entityManager.flush();
            entityManager.clear();
        });
    }

    private EmailOutbox latestEmailOutbox(
            EmailOutboxType emailType,
            String recipientEmail
    ) {
        return transactionTemplate.execute(status -> emailOutboxRepository.findAll()
                .stream()
                .filter(emailOutbox -> emailOutbox.getEmailType() == emailType)
                .filter(emailOutbox -> recipientEmail.equals(emailOutbox.getRecipientEmail()))
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow());
    }

    private EmailOutbox emailOutboxById(UUID emailOutboxId) {
        return transactionTemplate.execute(status -> emailOutboxRepository.findById(emailOutboxId)
                .orElseThrow());
    }

    private User userById(UUID userId) {
        return transactionTemplate.execute(status -> userRepository.findById(userId)
                .orElseThrow());
    }

    private String decryptTextBody(EmailOutbox emailOutbox) {
        return emailOutboxEncryptionService.decrypt(
                new EncryptedValue(
                        emailOutbox.getBodyTextEncrypted(),
                        emailOutbox.getBodyTextIv(),
                        emailOutbox.getBodyTextTag()
                )
        );
    }
}