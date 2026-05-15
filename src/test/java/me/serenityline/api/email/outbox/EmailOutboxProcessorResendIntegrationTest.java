package me.serenityline.api.email.outbox;

import me.serenityline.api.auth.entity.EmailOutbox;
import me.serenityline.api.auth.entity.EmailOutboxStatus;
import me.serenityline.api.auth.entity.EmailOutboxType;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.email.service.EmailSender;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(
        properties = {
                "serenityline.email.provider=resend",
                "serenityline.email.outbox-worker.enabled=false"
        }
)
@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")
@EnabledIfSystemProperty(
        named = "serenityline.tests.real-email.enabled",
        matches = "true"
)
class EmailOutboxProcessorResendIntegrationTest {

    private static final String TEST_RECIPIENT_EMAIL = "test@serenityline.me";

    private static final String TEST_SUBJECT = "SerenityLine email outbox integration test";

    private static final String TEST_TEXT_BODY = """
            Test invio email SerenityLine tramite EmailOutboxProcessor.
            
            Questa email verifica il flow:
            outbox cifrata -> decrypt -> Resend -> markSent.
            """;

    private static final String TEST_HTML_BODY = """
            <p>Test invio email <strong>SerenityLine</strong> tramite EmailOutboxProcessor.</p>
            <p>Flow verificato: outbox cifrata -&gt; decrypt -&gt; Resend -&gt; markSent.</p>
            """;

    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 10;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @Autowired
    private EmailSender emailSender;

    @Test
    @Transactional
    void shouldSendDueEncryptedOutboxEmailWithResendAndMarkItAsSent() {
        EmailOutbox emailOutbox = createDueEmailOutbox();

        EmailOutbox savedEmailOutbox = emailOutboxRepository.saveAndFlush(emailOutbox);
        UUID emailOutboxId = savedEmailOutbox.getEmailOutboxId();

        EmailOutboxProcessor processor = new EmailOutboxProcessor(
                emailOutboxRepository,
                emailOutboxEncryptionService,
                emailSender,
                BATCH_SIZE,
                RETRY_DELAY
        );

        int processedEmails = processor.processDueEmails();

        EmailOutbox processedEmailOutbox = emailOutboxRepository
                .findById(emailOutboxId)
                .orElseThrow();

        assertThat(processedEmails).isEqualTo(1);
        assertThat(processedEmailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(processedEmailOutbox.getAttempts()).isEqualTo(1);
        assertThat(processedEmailOutbox.getEmailSentAt()).isNotNull();
        assertThat(processedEmailOutbox.getProvider()).isEqualTo("resend");
        assertThat(processedEmailOutbox.getProviderMessageId()).isNotBlank();
        assertThat(processedEmailOutbox.getLastError()).isNull();

        assertThat(processedEmailOutbox.getBodyTextEncrypted()).isNull();
        assertThat(processedEmailOutbox.getBodyTextIv()).isNull();
        assertThat(processedEmailOutbox.getBodyTextTag()).isNull();
        assertThat(processedEmailOutbox.getBodyHtmlEncrypted()).isNull();
        assertThat(processedEmailOutbox.getBodyHtmlIv()).isNull();
        assertThat(processedEmailOutbox.getBodyHtmlTag()).isNull();
        assertThat(processedEmailOutbox.getEmailBodyDeletedAt()).isNotNull();
    }

    private EmailOutbox createDueEmailOutbox() {
        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(TEST_SUBJECT);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(TEST_TEXT_BODY);
        EncryptedValue encryptedHtmlBody = emailOutboxEncryptionService.encrypt(TEST_HTML_BODY);

        return new EmailOutbox(
                null,
                TEST_RECIPIENT_EMAIL,
                EmailOutboxType.GENERIC,
                emailOutboxEncryptionService.getEncryptionKeyId(),
                encryptedSubject.encrypted(),
                encryptedSubject.iv(),
                encryptedSubject.tag(),
                encryptedHtmlBody.encrypted(),
                encryptedHtmlBody.iv(),
                encryptedHtmlBody.tag(),
                encryptedTextBody.encrypted(),
                encryptedTextBody.iv(),
                encryptedTextBody.tag(),
                true,
                OffsetDateTime.now().minusSeconds(1)
        );
    }
}