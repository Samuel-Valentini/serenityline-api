package me.serenityline.api.email.outbox;

import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "serenityline.email.outbox-worker.enabled=false"
})
@ActiveProfiles("test")
@Transactional
class EmailOutboxProcessorSentEventTest {

    private static final String RECIPIENT_EMAIL = "recipient@example.com";
    private static final String SUBJECT = "Processor event test";
    private static final String TEXT_BODY = "Processor event body";
    private static final String PROVIDER = "fake-provider";
    private static final String PROVIDER_MESSAGE_ID = "provider-message-id";

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @BeforeEach
    void cleanEmailOutbox() {
        emailOutboxRepository.deleteAllInBatch();
    }

    @Test
    void shouldPublishSentEventAfterSuccessfulSend() {
        EmailOutbox saved = emailOutboxRepository.saveAndFlush(dueEmailOutbox());
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();

        EmailOutboxProcessor processor = processor(
                new FakeEmailSender(),
                eventPublisher
        );

        int processed = processor.processDueEmails();

        assertThat(processed).isEqualTo(1);

        EmailOutbox reloaded = emailOutboxRepository.findById(saved.getEmailOutboxId())
                .orElseThrow();

        assertThat(reloaded.getEmailStatus()).isEqualTo(EmailOutboxStatus.SENT);

        assertThat(eventPublisher.events()).hasSize(1);

        EmailOutboxSentEvent event = eventPublisher.events().getFirst();

        assertThat(event.emailOutboxId()).isEqualTo(saved.getEmailOutboxId());
        assertThat(event.userId()).isNull();
        assertThat(event.recipientEmail()).isEqualTo(RECIPIENT_EMAIL);
        assertThat(event.emailType()).isEqualTo(EmailOutboxType.GENERIC);
    }

    @Test
    void shouldNotPublishSentEventWhenSenderFails() {
        EmailOutbox saved = emailOutboxRepository.saveAndFlush(dueEmailOutbox());
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();

        FakeEmailSender emailSender = new FakeEmailSender();
        emailSender.failWith("email.test.failure");

        EmailOutboxProcessor processor = processor(emailSender, eventPublisher);

        int processed = processor.processDueEmails();

        assertThat(processed).isEqualTo(1);
        assertThat(eventPublisher.events()).isEmpty();

        EmailOutbox reloaded = emailOutboxRepository.findById(saved.getEmailOutboxId())
                .orElseThrow();

        assertThat(reloaded.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getLastError()).isEqualTo("email.test.failure");
    }

    @Test
    void shouldKeepEmailSentWhenEventPublisherFails() {
        EmailOutbox saved = emailOutboxRepository.saveAndFlush(dueEmailOutbox());

        EmailOutboxProcessor processor = processor(
                new FakeEmailSender(),
                new ThrowingEventPublisher()
        );

        int processed = processor.processDueEmails();

        assertThat(processed).isEqualTo(1);

        EmailOutbox reloaded = emailOutboxRepository.findById(saved.getEmailOutboxId())
                .orElseThrow();

        assertThat(reloaded.getEmailStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getProvider()).isEqualTo(PROVIDER);
        assertThat(reloaded.getProviderMessageId()).isEqualTo(PROVIDER_MESSAGE_ID);
        assertThat(reloaded.getLastError()).isNull();
    }

    private EmailOutboxProcessor processor(
            EmailSender emailSender,
            ApplicationEventPublisher eventPublisher
    ) {
        return new EmailOutboxProcessor(
                emailOutboxRepository,
                emailOutboxEncryptionService,
                emailSender,
                10,
                Duration.ofMinutes(5),
                eventPublisher
        );
    }

    private EmailOutbox dueEmailOutbox() {
        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(SUBJECT);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(TEXT_BODY);

        return new EmailOutbox(
                null,
                RECIPIENT_EMAIL,
                EmailOutboxType.GENERIC,
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
                false,
                OffsetDateTime.now().minusSeconds(1)
        );
    }

    private static final class CapturingEventPublisher implements ApplicationEventPublisher {

        private final List<EmailOutboxSentEvent> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof EmailOutboxSentEvent sentEvent) {
                events.add(sentEvent);
            }
        }

        private List<EmailOutboxSentEvent> events() {
            return events;
        }
    }

    private static final class ThrowingEventPublisher implements ApplicationEventPublisher {

        @Override
        public void publishEvent(Object event) {
            throw new IllegalStateException("event publisher unavailable");
        }
    }

    private static final class FakeEmailSender implements EmailSender {

        private boolean shouldFail;
        private String failureSafeMessage;

        @Override
        public String provider() {
            return PROVIDER;
        }

        @Override
        public SentEmail send(OutboundEmail email) {
            if (shouldFail) {
                throw new EmailSendException(failureSafeMessage);
            }

            assertThat(email.to()).isEqualTo(RECIPIENT_EMAIL);
            assertThat(email.subject()).isEqualTo(SUBJECT);
            assertThat(email.text()).isEqualTo(TEXT_BODY);
            assertThat(email.html()).isNull();

            return new SentEmail(PROVIDER_MESSAGE_ID);
        }

        private void failWith(String failureSafeMessage) {
            this.shouldFail = true;
            this.failureSafeMessage = failureSafeMessage;
        }
    }
}