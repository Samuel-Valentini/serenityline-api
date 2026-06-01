package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.notification.FinanceReminderEmailFinalStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceReminderEmailFinalStatusSyncCandidateTest {

    @Test
    void constructorShouldTrimProviderAndProviderMessageId() {
        UUID notificationId = UUID.randomUUID();
        UUID firstEmailOutboxId = UUID.randomUUID();

        FinanceReminderEmailFinalStatusSyncCandidate candidate =
                new FinanceReminderEmailFinalStatusSyncCandidate(
                        notificationId,
                        firstEmailOutboxId,
                        FinanceReminderEmailFinalStatus.SENT,
                        "  RESEND  ",
                        "  msg-123  "
                );

        assertThat(candidate.financeReminderNotificationId()).isEqualTo(notificationId);
        assertThat(candidate.emailFinalStatus()).isEqualTo(FinanceReminderEmailFinalStatus.SENT);
        assertThat(candidate.emailProvider()).isEqualTo("RESEND");
        assertThat(candidate.emailProviderMessageId()).isEqualTo("msg-123");
    }

    @Test
    void constructorShouldNormalizeBlankProviderAndMessageToNull() {
        UUID firstEmailOutboxId = UUID.randomUUID();
        FinanceReminderEmailFinalStatusSyncCandidate candidate =
                new FinanceReminderEmailFinalStatusSyncCandidate(
                        UUID.randomUUID(),
                        firstEmailOutboxId,
                        FinanceReminderEmailFinalStatus.FAILED,
                        "   ",
                        "   "
                );

        assertThat(candidate.emailProvider()).isNull();
        assertThat(candidate.emailProviderMessageId()).isNull();
    }

    @Test
    void constructorShouldClearProviderMessageIdWhenProviderIsNull() {
        UUID firstEmailOutboxId = UUID.randomUUID();
        FinanceReminderEmailFinalStatusSyncCandidate candidate =
                new FinanceReminderEmailFinalStatusSyncCandidate(
                        UUID.randomUUID(),
                        firstEmailOutboxId,
                        FinanceReminderEmailFinalStatus.SENT,
                        null,
                        "msg-123"
                );

        assertThat(candidate.emailProvider()).isNull();
        assertThat(candidate.emailProviderMessageId()).isNull();
    }

    @Test
    void constructorShouldRejectNullNotificationId() {
        UUID firstEmailOutboxId = UUID.randomUUID();
        assertThatThrownBy(() -> new FinanceReminderEmailFinalStatusSyncCandidate(
                null,
                firstEmailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "RESEND",
                "msg-123"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("financeReminderNotificationId");
    }

    @Test
    void constructorShouldRejectNullFinalStatus() {
        UUID firstEmailOutboxId = UUID.randomUUID();
        assertThatThrownBy(() -> new FinanceReminderEmailFinalStatusSyncCandidate(
                UUID.randomUUID(),
                firstEmailOutboxId,
                null,
                "RESEND",
                "msg-123"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("emailFinalStatus");
    }

    @Test
    void constructorShouldRejectTooLongProvider() {
        UUID firstEmailOutboxId = UUID.randomUUID();
        assertThatThrownBy(() -> new FinanceReminderEmailFinalStatusSyncCandidate(
                UUID.randomUUID(),
                firstEmailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "A".repeat(101),
                "msg-123"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.reminderFinalStatus.emailProvider.tooLong");
    }

    @Test
    void constructorShouldRejectTooLongProviderMessageId() {
        UUID firstEmailOutboxId = UUID.randomUUID();
        assertThatThrownBy(() -> new FinanceReminderEmailFinalStatusSyncCandidate(
                UUID.randomUUID(),
                firstEmailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "RESEND",
                "A".repeat(256)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.reminderFinalStatus.emailProviderMessageId.tooLong");
    }
}