package me.serenityline.api.support.contact.service;

import me.serenityline.api.email.outbox.EmailOutboxSentEvent;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.*;
import me.serenityline.api.user.repository.UserGroupRepository;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = {
        "serenityline.support.contact.recipient-email=test@serenityline.me"
})
class SupportContactConfirmationOutboxServiceIntegrationTest extends IntegrationTestSupport {

    private static final String SUPPORT_EMAIL = "test@serenityline.me";

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Test
    void shouldEnqueueConfirmationAfterCommitForAuthenticatedSupportContact() {
        User user = givenEnabledUser("confirmation");
        UUID supportNotificationId = UUID.randomUUID();

        publishInsideCommittedTransaction(new EmailOutboxSentEvent(
                supportNotificationId,
                user.getUserId(),
                SUPPORT_EMAIL,
                EmailOutboxType.SUPPORT_CONTACT
        ));

        EmailOutbox confirmation = singleOutbox(EmailOutboxType.SUPPORT_CONTACT_CONFIRMATION);

        assertThat(confirmation.getRecipientEmail()).isEqualTo(user.getEmail());
        assertThat(confirmation.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(confirmation.isDeleteBodyAfterSend()).isTrue();
        assertThat(confirmation.getBodyHtmlEncrypted()).isNull();

        String reference = supportNotificationId
                .toString()
                .substring(0, 8)
                .toUpperCase();

        assertThat(decryptSubject(confirmation))
                .contains("SerenityLine")
                .contains(reference);

        assertThat(decryptTextBody(confirmation))
                .contains(user.getUserName())
                .contains(reference)
                .contains("non inviare mai password");
    }

    @Test
    void shouldNotEnqueueConfirmationWhenMainTransactionRollsBack() {
        User user = givenEnabledUser("rollback");
        UUID supportNotificationId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new EmailOutboxSentEvent(
                    supportNotificationId,
                    user.getUserId(),
                    SUPPORT_EMAIL,
                    EmailOutboxType.SUPPORT_CONTACT
            ));

            throw new IllegalStateException("force rollback");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback");

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void shouldIgnoreAnonymousSupportContactSentEvent() {
        publishInsideCommittedTransaction(new EmailOutboxSentEvent(
                UUID.randomUUID(),
                null,
                SUPPORT_EMAIL,
                EmailOutboxType.SUPPORT_CONTACT
        ));

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void shouldIgnoreSupportContactSentToDifferentRecipient() {
        User user = givenEnabledUser("wrong-recipient");

        publishInsideCommittedTransaction(new EmailOutboxSentEvent(
                UUID.randomUUID(),
                user.getUserId(),
                "other-support@example.com",
                EmailOutboxType.SUPPORT_CONTACT
        ));

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void shouldIgnoreNonSupportContactEmailType() {
        User user = givenEnabledUser("generic");

        publishInsideCommittedTransaction(new EmailOutboxSentEvent(
                UUID.randomUUID(),
                user.getUserId(),
                SUPPORT_EMAIL,
                EmailOutboxType.GENERIC
        ));

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    private void publishInsideCommittedTransaction(EmailOutboxSentEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private EmailOutbox singleOutbox(EmailOutboxType type) {
        List<EmailOutbox> matches = emailOutboxRepository.findAll()
                .stream()
                .filter(emailOutbox -> emailOutbox.getEmailType() == type)
                .toList();

        assertThat(matches).hasSize(1);

        return matches.getFirst();
    }

    private String decryptSubject(EmailOutbox emailOutbox) {
        return emailOutboxEncryptionService.decrypt(new EncryptedValue(
                emailOutbox.getSubjectEncrypted(),
                emailOutbox.getSubjectIv(),
                emailOutbox.getSubjectTag()
        ));
    }

    private String decryptTextBody(EmailOutbox emailOutbox) {
        return emailOutboxEncryptionService.decrypt(new EncryptedValue(
                emailOutbox.getBodyTextEncrypted(),
                emailOutbox.getBodyTextIv(),
                emailOutbox.getBodyTextTag()
        ));
    }

    private User givenEnabledUser(String prefix) {
        String unique = prefix + "-" + UUID.randomUUID();

        UserGroup userGroup = userGroupRepository.saveAndFlush(
                new UserGroup("Group " + unique)
        );

        User user = new User(
                "User " + prefix,
                unique + "@example.com",
                userGroup,
                UserRole.OWNER,
                UserPlatformRole.USER,
                "it-IT",
                PreferredTheme.DEFAULT,
                false,
                true,
                "{noop}password",
                true,
                0L
        );

        return userRepository.saveAndFlush(user);
    }
}