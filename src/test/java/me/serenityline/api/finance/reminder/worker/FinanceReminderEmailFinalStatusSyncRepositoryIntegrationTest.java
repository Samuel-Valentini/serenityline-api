package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.notification.FinanceReminderEmailFinalStatus;
import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class FinanceReminderEmailFinalStatusSyncRepositoryIntegrationTest extends IntegrationTestSupport {

    private final List<UUID> createdUserGroupIds = new ArrayList<>();
    private final List<UUID> createdEmailOutboxIds = new ArrayList<>();
    @Autowired
    private FinanceReminderEmailFinalStatusSyncRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 1);
        return bytes;
    }

    private static String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@example.com";
    }

    @AfterEach
    void tearDown() {
        for (UUID userGroupId : createdUserGroupIds) {
            deleteGroupData(userGroupId);
        }

        for (UUID emailOutboxId : createdEmailOutboxIds) {
            jdbcTemplate.update("""
                    DELETE FROM email_outbox
                    WHERE email_outbox_id = ?
                    """, emailOutboxId);
        }

        createdUserGroupIds.clear();
        createdEmailOutboxIds.clear();
    }

    @Test
    void findFinalStatusCandidatesShouldReturnOnlyNotificationsLinkedToTerminalOutboxesWithoutFinalStatus() {
        TestContext context = createContext();

        OffsetDateTime baseTime = OffsetDateTime.parse("2026-06-10T08:00:00Z");

        UUID sentOutboxId = insertEmailOutbox(
                context.ownerUserId(),
                "SENT",
                "RESEND",
                "msg-sent",
                baseTime.plusSeconds(1)
        );

        UUID failedOutboxId = insertEmailOutbox(
                context.ownerUserId(),
                "FAILED",
                null,
                null,
                baseTime.plusSeconds(2)
        );

        UUID cancelledOutboxId = insertEmailOutbox(
                context.ownerUserId(),
                "CANCELLED",
                null,
                null,
                baseTime.plusSeconds(3)
        );

        UUID pendingOutboxId = insertEmailOutbox(
                context.ownerUserId(),
                "PENDING",
                null,
                null,
                baseTime.plusSeconds(4)
        );

        UUID alreadySyncedOutboxId = insertEmailOutbox(
                context.ownerUserId(),
                "SENT",
                "RESEND",
                "msg-already-synced",
                baseTime.plusSeconds(5)
        );

        UUID sentNotificationId = insertRecurringReminderNotification(
                context,
                sentOutboxId,
                LocalDate.of(2026, 6, 17),
                null
        );

        UUID failedNotificationId = insertRecurringReminderNotification(
                context,
                failedOutboxId,
                LocalDate.of(2026, 6, 18),
                null
        );

        UUID cancelledNotificationId = insertRecurringReminderNotification(
                context,
                cancelledOutboxId,
                LocalDate.of(2026, 6, 19),
                null
        );

        insertRecurringReminderNotification(
                context,
                pendingOutboxId,
                LocalDate.of(2026, 6, 20),
                null
        );

        insertRecurringReminderNotification(
                context,
                alreadySyncedOutboxId,
                LocalDate.of(2026, 6, 21),
                FinanceReminderEmailFinalStatus.SENT
        );

        List<FinanceReminderEmailFinalStatusSyncCandidate> result =
                repository.findFinalStatusCandidates(10);

        assertThat(result)
                .extracting(
                        FinanceReminderEmailFinalStatusSyncCandidate::financeReminderNotificationId,
                        FinanceReminderEmailFinalStatusSyncCandidate::emailFinalStatus,
                        FinanceReminderEmailFinalStatusSyncCandidate::emailProvider,
                        FinanceReminderEmailFinalStatusSyncCandidate::emailProviderMessageId
                )
                .containsExactly(
                        tuple(
                                sentNotificationId,
                                FinanceReminderEmailFinalStatus.SENT,
                                "RESEND",
                                "msg-sent"
                        ),
                        tuple(
                                failedNotificationId,
                                FinanceReminderEmailFinalStatus.FAILED,
                                null,
                                null
                        ),
                        tuple(
                                cancelledNotificationId,
                                FinanceReminderEmailFinalStatus.CANCELLED,
                                null,
                                null
                        )
                );
    }

    @Test
    void findFinalStatusCandidatesShouldRespectLimit() {
        TestContext context = createContext();

        OffsetDateTime baseTime = OffsetDateTime.parse("2026-06-10T08:00:00Z");

        UUID firstOutboxId = insertEmailOutbox(
                context.ownerUserId(),
                "SENT",
                "RESEND",
                "msg-1",
                baseTime.plusSeconds(1)
        );

        UUID secondOutboxId = insertEmailOutbox(
                context.ownerUserId(),
                "SENT",
                "RESEND",
                "msg-2",
                baseTime.plusSeconds(2)
        );

        UUID firstNotificationId = insertRecurringReminderNotification(
                context,
                firstOutboxId,
                LocalDate.of(2026, 6, 17),
                null
        );

        insertRecurringReminderNotification(
                context,
                secondOutboxId,
                LocalDate.of(2026, 6, 18),
                null
        );

        List<FinanceReminderEmailFinalStatusSyncCandidate> result =
                repository.findFinalStatusCandidates(1);

        assertThat(result)
                .extracting(FinanceReminderEmailFinalStatusSyncCandidate::financeReminderNotificationId)
                .containsExactly(firstNotificationId);
    }

    @Test
    void findFinalStatusCandidatesShouldRejectInvalidLimit() {
        assertThatThrownBy(() -> repository.findFinalStatusCandidates(0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("finance.reminderFinalStatus.limit.invalid")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private TestContext createContext() {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        createdUserGroupIds.add(userGroupId);

        jdbcTemplate.update("""
                        INSERT INTO user_groups (
                            user_group_id,
                            user_group_name
                        )
                        VALUES (?, ?)
                        """,
                userGroupId,
                "Final Status Sync Group " + userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_platform_role,
                            preferred_locale,
                            preferred_theme,
                            wants_invoice,
                            user_password_hash,
                            user_is_enabled,
                            payment_email_reminders_enabled
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                ownerUserId,
                "Final Status Sync User " + ownerUserId,
                uniqueEmail("final-status-sync"),
                userGroupId,
                "OWNER",
                "USER",
                "it-IT",
                "LIGHT",
                false,
                "{noop}password",
                true,
                true
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            simulation_group_id,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                recurringTransactionId,
                true,
                LocalDate.of(2026, 1, 10),
                false,
                null,
                true,
                (short) 7,
                userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions_users (
                            recurring_transaction_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                recurringTransactionId,
                ownerUserId,
                userGroupId
        );

        return new TestContext(
                userGroupId,
                ownerUserId,
                recurringTransactionId
        );
    }

    private UUID insertEmailOutbox(
            UUID userId,
            String status,
            String provider,
            String providerMessageId,
            OffsetDateTime updatedAt
    ) {
        UUID emailOutboxId = UUID.randomUUID();

        createdEmailOutboxIds.add(emailOutboxId);

        OffsetDateTime sentAt = "SENT".equals(status) ? updatedAt : null;
        OffsetDateTime failedAt = "FAILED".equals(status) ? updatedAt : null;
        OffsetDateTime cancelledAt = "CANCELLED".equals(status) ? updatedAt : null;

        int attempts = "FAILED".equals(status) ? 6 : 0;
        int maxAttempts = 6;

        jdbcTemplate.update("""
                        INSERT INTO email_outbox (
                            email_outbox_id,
                            user_id,
                            recipient_email,
                            email_type,
                            encryption_key_id,
                            subject_encrypted,
                            subject_iv,
                            subject_tag,
                            body_text_encrypted,
                            body_text_iv,
                            body_text_tag,
                            delete_body_after_send,
                            provider,
                            provider_message_id,
                            email_status,
                            attempts,
                            max_attempts,
                            email_scheduled_at,
                            email_sent_at,
                            email_last_failed_at,
                            email_created_at,
                            email_updated_at,
                            email_cancelled_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                emailOutboxId,
                userId,
                uniqueEmail("outbox-recipient"),
                "GENERIC",
                "test-key",
                bytes(8),
                bytes(12),
                bytes(16),
                bytes(8),
                bytes(12),
                bytes(16),
                false,
                provider,
                providerMessageId,
                status,
                attempts,
                maxAttempts,
                updatedAt,
                sentAt,
                failedAt,
                updatedAt,
                updatedAt,
                cancelledAt
        );

        return emailOutboxId;
    }

    private UUID insertRecurringReminderNotification(
            TestContext context,
            UUID emailOutboxId,
            LocalDate logicalDate,
            FinanceReminderEmailFinalStatus existingFinalStatus
    ) {
        UUID notificationId = UUID.randomUUID();

        OffsetDateTime finalStatusRecordedAt = existingFinalStatus == null
                ? null
                : OffsetDateTime.parse("2026-06-10T09:00:00Z");

        String emailProvider = existingFinalStatus == null
                ? null
                : "RESEND";

        String providerMessageId = existingFinalStatus == null
                ? null
                : "already-synced";

        jdbcTemplate.update("""
                        INSERT INTO finance_reminder_notifications (
                            finance_reminder_notification_id,
                            user_id,
                            user_group_id,
                            transaction_id,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            charge_date,
                            notified_description,
                            notified_amount,
                            notified_currency,
                            reminder_date,
                            email_outbox_id,
                            email_final_status,
                            email_final_status_recorded_at,
                            email_provider,
                            provider_message_id
                        )
                        VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                notificationId,
                context.ownerUserId(),
                context.userGroupId(),
                context.recurringTransactionId(),
                logicalDate,
                logicalDate,
                "Final status sync notification " + notificationId,
                new BigDecimal("-100.00"),
                "EUR",
                logicalDate.minusDays(7),
                emailOutboxId,
                existingFinalStatus == null ? null : existingFinalStatus.name(),
                finalStatusRecordedAt,
                emailProvider,
                providerMessageId
        );

        return notificationId;
    }

    private void deleteGroupData(UUID userGroupId) {
        jdbcTemplate.update("""
                DELETE FROM finance_reminder_notifications
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transactions_users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transactions
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM user_groups
                WHERE user_group_id = ?
                """, userGroupId);
    }

    private record TestContext(
            UUID userGroupId,
            UUID ownerUserId,
            UUID recurringTransactionId
    ) {
    }
}