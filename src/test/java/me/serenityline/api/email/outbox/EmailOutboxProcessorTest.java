package me.serenityline.api.email.outbox;

import me.serenityline.api.auth.entity.EmailOutbox;
import me.serenityline.api.auth.entity.EmailOutboxStatus;
import me.serenityline.api.auth.entity.EmailOutboxType;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.email.service.EmailSendException;
import me.serenityline.api.email.service.EmailSender;
import me.serenityline.api.email.service.OutboundEmail;
import me.serenityline.api.email.service.SentEmail;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "serenityline.email.outbox-worker.enabled=false"
        }
)
@ActiveProfiles("test")
@Transactional
class EmailOutboxProcessorTest {

    private static final String TEST_RECIPIENT_EMAIL = "test@serenityline.me";

    private static final String TEST_SUBJECT = "SerenityLine email outbox processor test";

    private static final String TEST_TEXT_BODY = """
            Test invio email SerenityLine tramite EmailOutboxProcessor.
            """;

    private static final String TEST_HTML_BODY = """
            <p>Test invio email <strong>SerenityLine</strong> tramite EmailOutboxProcessor.</p>
            """;

    private static final String FAKE_PROVIDER = "fake-email-sender";
    private static final String FAKE_PROVIDER_MESSAGE_ID = "fake-provider-message-id";
    private static final String FAILURE_SAFE_MESSAGE = "email.test.failure";

    private static final int BATCH_SIZE = 10;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @BeforeEach
    void cleanEmailOutbox() {
        emailOutboxRepository.deleteAllInBatch();
    }

    @Test
    void shouldSendDuePendingEmailAndMarkItAsSent() {
        FakeEmailSender fakeEmailSender = new FakeEmailSender();

        EmailOutbox savedEmailOutbox = saveEmailOutbox(
                createDueEmailOutbox(true)
        );

        UUID emailOutboxId = savedEmailOutbox.getEmailOutboxId();

        EmailOutboxProcessor processor = processor(fakeEmailSender);

        int processedEmails = processor.processDueEmails();

        EmailOutbox processedEmailOutbox = findEmailOutbox(emailOutboxId);

        assertThat(processedEmails).isEqualTo(1);

        assertThat(fakeEmailSender.sentEmails()).hasSize(1);

        OutboundEmail sentEmail = fakeEmailSender.lastSentEmail();

        assertThat(sentEmail.to()).isEqualTo(TEST_RECIPIENT_EMAIL);
        assertThat(sentEmail.subject()).isEqualTo(TEST_SUBJECT);
        assertThat(sentEmail.text()).isEqualTo(TEST_TEXT_BODY);
        assertThat(sentEmail.html()).isEqualTo(TEST_HTML_BODY);

        assertThat(processedEmailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(processedEmailOutbox.getAttempts()).isEqualTo(1);
        assertThat(processedEmailOutbox.getEmailSentAt()).isNotNull();
        assertThat(processedEmailOutbox.getProvider()).isEqualTo(FAKE_PROVIDER);
        assertThat(processedEmailOutbox.getProviderMessageId()).isEqualTo(FAKE_PROVIDER_MESSAGE_ID);
        assertThat(processedEmailOutbox.getLastError()).isNull();

        assertThat(processedEmailOutbox.getBodyTextEncrypted()).isNull();
        assertThat(processedEmailOutbox.getBodyTextIv()).isNull();
        assertThat(processedEmailOutbox.getBodyTextTag()).isNull();
        assertThat(processedEmailOutbox.getBodyHtmlEncrypted()).isNull();
        assertThat(processedEmailOutbox.getBodyHtmlIv()).isNull();
        assertThat(processedEmailOutbox.getBodyHtmlTag()).isNull();
        assertThat(processedEmailOutbox.getEmailBodyDeletedAt()).isNotNull();
    }

    @Test
    void shouldNotProcessEmailScheduledInTheFuture() {
        FakeEmailSender fakeEmailSender = new FakeEmailSender();

        EmailOutbox savedEmailOutbox = saveEmailOutbox(
                createFutureEmailOutbox(true)
        );

        UUID emailOutboxId = savedEmailOutbox.getEmailOutboxId();

        EmailOutboxProcessor processor = processor(fakeEmailSender);

        int processedEmails = processor.processDueEmails();

        EmailOutbox processedEmailOutbox = findEmailOutbox(emailOutboxId);

        assertThat(processedEmails).isZero();
        assertThat(fakeEmailSender.sentEmails()).isEmpty();

        assertThat(processedEmailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(processedEmailOutbox.getAttempts()).isZero();
        assertThat(processedEmailOutbox.getEmailSentAt()).isNull();
        assertThat(processedEmailOutbox.getEmailLastFailedAt()).isNull();
        assertThat(processedEmailOutbox.getLastError()).isNull();
    }

    @Test
    void shouldMarkEmailAsFailedAndScheduleRetryWhenSenderFailsBeforeMaxAttempts() {
        FakeEmailSender fakeEmailSender = new FakeEmailSender();
        fakeEmailSender.failWith(FAILURE_SAFE_MESSAGE);

        EmailOutbox savedEmailOutbox = saveEmailOutbox(
                createDueEmailOutbox(true)
        );

        UUID emailOutboxId = savedEmailOutbox.getEmailOutboxId();

        EmailOutboxProcessor processor = processor(fakeEmailSender);

        int processedEmails = processor.processDueEmails();

        EmailOutbox processedEmailOutbox = findEmailOutbox(emailOutboxId);

        assertThat(processedEmails).isEqualTo(1);
        assertThat(fakeEmailSender.sentEmails()).isEmpty();

        assertThat(processedEmailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(processedEmailOutbox.getAttempts()).isEqualTo(1);
        assertThat(processedEmailOutbox.getEmailLastFailedAt()).isNotNull();
        assertThat(processedEmailOutbox.getLastError()).isEqualTo(FAILURE_SAFE_MESSAGE);
        assertThat(processedEmailOutbox.getEmailScheduledAt())
                .isAfter(processedEmailOutbox.getEmailLastFailedAt());

        assertThat(processedEmailOutbox.getEmailSentAt()).isNull();
        assertThat(processedEmailOutbox.getProvider()).isNull();
        assertThat(processedEmailOutbox.getProviderMessageId()).isNull();

        assertThat(processedEmailOutbox.getBodyTextEncrypted()).isNotNull();
        assertThat(processedEmailOutbox.getBodyHtmlEncrypted()).isNotNull();
        assertThat(processedEmailOutbox.getEmailBodyDeletedAt()).isNull();
    }

    @Test
    void shouldMarkEmailAsFailedTerminalWhenSenderFailsOnLastAttempt() {
        FakeEmailSender fakeEmailSender = new FakeEmailSender();
        fakeEmailSender.failWith(FAILURE_SAFE_MESSAGE);

        EmailOutbox emailOutbox = createDueEmailOutbox(true);

        ReflectionTestUtils.setField(
                emailOutbox,
                "attempts",
                emailOutbox.getMaxAttempts() - 1
        );

        EmailOutbox savedEmailOutbox = saveEmailOutbox(emailOutbox);

        UUID emailOutboxId = savedEmailOutbox.getEmailOutboxId();

        EmailOutboxProcessor processor = processor(fakeEmailSender);

        int processedEmails = processor.processDueEmails();

        EmailOutbox processedEmailOutbox = findEmailOutbox(emailOutboxId);

        assertThat(processedEmails).isEqualTo(1);
        assertThat(fakeEmailSender.sentEmails()).isEmpty();

        assertThat(processedEmailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(processedEmailOutbox.getAttempts()).isEqualTo(processedEmailOutbox.getMaxAttempts());
        assertThat(processedEmailOutbox.getEmailLastFailedAt()).isNotNull();
        assertThat(processedEmailOutbox.getLastError()).isEqualTo(FAILURE_SAFE_MESSAGE);

        assertThat(processedEmailOutbox.getEmailSentAt()).isNull();
        assertThat(processedEmailOutbox.getProvider()).isNull();
        assertThat(processedEmailOutbox.getProviderMessageId()).isNull();

        assertThat(processedEmailOutbox.getBodyTextEncrypted()).isNotNull();
        assertThat(processedEmailOutbox.getBodyHtmlEncrypted()).isNotNull();
        assertThat(processedEmailOutbox.getEmailBodyDeletedAt()).isNull();
    }

    @Test
    void shouldKeepBodyAfterSentWhenDeleteBodyAfterSendIsFalse() {
        FakeEmailSender fakeEmailSender = new FakeEmailSender();

        EmailOutbox savedEmailOutbox = saveEmailOutbox(
                createDueEmailOutbox(false)
        );

        UUID emailOutboxId = savedEmailOutbox.getEmailOutboxId();

        EmailOutboxProcessor processor = processor(fakeEmailSender);

        int processedEmails = processor.processDueEmails();

        EmailOutbox processedEmailOutbox = findEmailOutbox(emailOutboxId);

        assertThat(processedEmails).isEqualTo(1);

        assertThat(processedEmailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(processedEmailOutbox.getAttempts()).isEqualTo(1);
        assertThat(processedEmailOutbox.getEmailSentAt()).isNotNull();

        assertThat(processedEmailOutbox.getBodyTextEncrypted()).isNotNull();
        assertThat(processedEmailOutbox.getBodyTextIv()).isNotNull();
        assertThat(processedEmailOutbox.getBodyTextTag()).isNotNull();
        assertThat(processedEmailOutbox.getBodyHtmlEncrypted()).isNotNull();
        assertThat(processedEmailOutbox.getBodyHtmlIv()).isNotNull();
        assertThat(processedEmailOutbox.getBodyHtmlTag()).isNotNull();
        assertThat(processedEmailOutbox.getEmailBodyDeletedAt()).isNull();
    }

    private EmailOutboxProcessor processor(FakeEmailSender fakeEmailSender) {
        return new EmailOutboxProcessor(
                emailOutboxRepository,
                emailOutboxEncryptionService,
                fakeEmailSender,
                BATCH_SIZE,
                RETRY_DELAY
        );
    }

    private EmailOutbox saveEmailOutbox(EmailOutbox emailOutbox) {
        return emailOutboxRepository.saveAndFlush(emailOutbox);
    }

    private EmailOutbox findEmailOutbox(UUID emailOutboxId) {
        return emailOutboxRepository
                .findById(emailOutboxId)
                .orElseThrow();
    }

    private EmailOutbox createDueEmailOutbox(boolean deleteBodyAfterSend) {
        return createEmailOutbox(
                deleteBodyAfterSend,
                OffsetDateTime.now().minusSeconds(1)
        );
    }

    private EmailOutbox createFutureEmailOutbox(boolean deleteBodyAfterSend) {
        return createEmailOutbox(
                deleteBodyAfterSend,
                OffsetDateTime.now().plusHours(1)
        );
    }

    private EmailOutbox createEmailOutbox(
            boolean deleteBodyAfterSend,
            OffsetDateTime scheduledAt
    ) {
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
                deleteBodyAfterSend,
                scheduledAt
        );
    }

    private static final class FakeEmailSender implements EmailSender {

        private final List<OutboundEmail> sentEmails = new ArrayList<>();

        private boolean shouldFail;
        private String failureSafeMessage;

        @Override
        public String provider() {
            return FAKE_PROVIDER;
        }

        @Override
        public SentEmail send(OutboundEmail email) {
            if (shouldFail) {
                throw new EmailSendException(failureSafeMessage);
            }

            sentEmails.add(email);

            return new SentEmail(FAKE_PROVIDER_MESSAGE_ID);
        }

        private void failWith(String failureSafeMessage) {
            this.shouldFail = true;
            this.failureSafeMessage = failureSafeMessage;
        }

        private List<OutboundEmail> sentEmails() {
            return sentEmails;
        }

        private OutboundEmail lastSentEmail() {
            return sentEmails.getLast();
        }
    }
}