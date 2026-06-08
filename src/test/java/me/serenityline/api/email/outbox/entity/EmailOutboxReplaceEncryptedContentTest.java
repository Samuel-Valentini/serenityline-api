package me.serenityline.api.email.outbox.entity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailOutboxReplaceEncryptedContentTest {

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fixedBytes(int length, int seed) {
        byte[] value = new byte[length];

        for (int index = 0; index < length; index++) {
            value[index] = (byte) (seed + index);
        }

        return value;
    }

    @Test
    void shouldReplaceEncryptedContentWhenEmailIsPendingAndHasNoAttempts() {
        EmailOutbox emailOutbox = emailOutbox();

        byte[] newSubject = bytes("new-subject");
        byte[] newSubjectIv = fixedBytes(12, 10);
        byte[] newSubjectTag = fixedBytes(16, 20);
        byte[] newText = bytes("new-text");
        byte[] newTextIv = fixedBytes(12, 30);
        byte[] newTextTag = fixedBytes(16, 40);

        emailOutbox.replaceEncryptedContent(
                newSubject,
                newSubjectIv,
                newSubjectTag,
                null,
                null,
                null,
                newText,
                newTextIv,
                newTextTag
        );

        assertThat(emailOutbox.getSubjectEncrypted()).containsExactly(newSubject);
        assertThat(emailOutbox.getSubjectIv()).containsExactly(newSubjectIv);
        assertThat(emailOutbox.getSubjectTag()).containsExactly(newSubjectTag);

        assertThat(emailOutbox.getBodyHtmlEncrypted()).isNull();
        assertThat(emailOutbox.getBodyHtmlIv()).isNull();
        assertThat(emailOutbox.getBodyHtmlTag()).isNull();

        assertThat(emailOutbox.getBodyTextEncrypted()).containsExactly(newText);
        assertThat(emailOutbox.getBodyTextIv()).containsExactly(newTextIv);
        assertThat(emailOutbox.getBodyTextTag()).containsExactly(newTextTag);

        assertThat(emailOutbox.getEmailBodyDeletedAt()).isNull();
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.getAttempts()).isZero();
    }

    @Test
    void shouldRejectReplacementAfterFailedAttempt() {
        EmailOutbox emailOutbox = emailOutbox();

        emailOutbox.markFailed(
                "temporary failure",
                OffsetDateTime.now().plusMinutes(5)
        );

        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.getAttempts()).isEqualTo(1);

        assertThatThrownBy(() -> emailOutbox.replaceEncryptedContent(
                bytes("new-subject"),
                fixedBytes(12, 10),
                fixedBytes(16, 20),
                null,
                null,
                null,
                bytes("new-text"),
                fixedBytes(12, 30),
                fixedBytes(16, 40)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("emailOutbox.contentCannotBeChangedAfterAttempt");
    }

    @Test
    void shouldRejectReplacementAfterSent() {
        EmailOutbox emailOutbox = emailOutbox();

        emailOutbox.markSent("test-provider", "provider-message-id");

        assertThatThrownBy(() -> emailOutbox.replaceEncryptedContent(
                bytes("new-subject"),
                fixedBytes(12, 10),
                fixedBytes(16, 20),
                null,
                null,
                null,
                bytes("new-text"),
                fixedBytes(12, 30),
                fixedBytes(16, 40)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("emailOutbox.notPending");
    }

    @Test
    void shouldRejectIncompleteOptionalBodyTriplet() {
        EmailOutbox emailOutbox = emailOutbox();

        assertThatThrownBy(() -> emailOutbox.replaceEncryptedContent(
                bytes("new-subject"),
                fixedBytes(12, 10),
                fixedBytes(16, 20),
                bytes("html"),
                null,
                fixedBytes(16, 40),
                bytes("new-text"),
                fixedBytes(12, 50),
                fixedBytes(16, 60)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("emailOutbox.htmlBody.incomplete");
    }

    private EmailOutbox emailOutbox() {
        return new EmailOutbox(
                null,
                "test@example.com",
                EmailOutboxType.GENERIC,
                "test-key",
                bytes("old-subject"),
                fixedBytes(12, 1),
                fixedBytes(16, 2),
                null,
                null,
                null,
                bytes("old-text"),
                fixedBytes(12, 3),
                fixedBytes(16, 4),
                false,
                OffsetDateTime.now().minusSeconds(1)
        );
    }
}