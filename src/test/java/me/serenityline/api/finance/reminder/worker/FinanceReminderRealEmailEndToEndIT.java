package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.email.outbox.EmailOutboxProcessor;
import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "serenityline.email.provider=resend",
        "serenityline.email.resend.api-key=${RESEND_API_KEY}",
        "serenityline.email.from=${SERENITYLINE_EMAIL_FROM:SerenityLine Test <test@serenityline.me>}",

        "serenityline.email.outbox-worker.enabled=true",
        "serenityline.email.outbox-worker.batch-size=1",
        "serenityline.email.outbox-worker.retry-delay=1s",

        "serenityline.finance.reminder-worker.enabled=false",
        "serenityline.finance.reminder-final-status-worker.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")
@EnabledIfSystemProperty(
        named = "serenityline.tests.real-email.enabled",
        matches = "true"
)
class FinanceReminderRealEmailEndToEndIT extends IntegrationTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);
    private static final String REAL_RECIPIENT = "test@serenityline.me";
    private final List<UUID> createdUserGroupIds = new ArrayList<>();
    private final List<UUID> createdEmailOutboxIds = new ArrayList<>();
    @Autowired
    private FinanceReminderNotificationWorkerService reminderWorkerService;
    @Autowired
    private FinanceReminderEmailFinalStatusSyncService finalStatusSyncService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EmailOutboxProcessor emailOutboxProcessor;

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
    void shouldCreateReminderSendRealEmailWithResendAndRecordFinalStatus() {
        TestContext context = createContext();

        UUID transactionId = UUID.randomUUID();

        insertDueTransaction(
                context,
                transactionId,
                "Test reale reminder SerenityLine " + transactionId,
                new BigDecimal("-12.34"),
                LocalDate.of(2026, 6, 17),
                (short) 7
        );

        insertTransactionUser(
                transactionId,
                context.ownerUserId(),
                context.userGroupId()
        );

        FinanceReminderWorkerResult reminderResult =
                reminderWorkerService.processDueReminders(TODAY);

        assertThat(reminderResult.candidatesFound()).isEqualTo(1);
        assertThat(reminderResult.notificationsCreated()).isEqualTo(1);
        assertThat(reminderResult.emailOutboxesEnsured()).isEqualTo(1);
        assertThat(reminderResult.alreadyNotified()).isZero();
        assertThat(reminderResult.failures()).isZero();

        Map<String, Object> notificationRow = jdbcTemplate.queryForMap("""
                        SELECT
                            finance_reminder_notification_id,
                            email_outbox_id
                        FROM finance_reminder_notifications
                        WHERE user_group_id = ?
                          AND user_id = ?
                          AND transaction_id = ?
                        """,
                context.userGroupId(),
                context.ownerUserId(),
                transactionId
        );

        UUID notificationId = (UUID) notificationRow.get("finance_reminder_notification_id");
        UUID emailOutboxId = (UUID) notificationRow.get("email_outbox_id");

        assertThat(notificationId).isNotNull();
        assertThat(emailOutboxId).isNotNull();

        createdEmailOutboxIds.add(emailOutboxId);

        Long otherDuePendingEmails = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM email_outbox
                        WHERE email_status = 'PENDING'
                          AND email_scheduled_at <= now()
                          AND email_outbox_id <> ?
                        """,
                Long.class,
                emailOutboxId
        );

        assertThat(otherDuePendingEmails).isZero();
        
        jdbcTemplate.update("""
                        UPDATE email_outbox
                        SET email_scheduled_at = now() - interval '1 second',
                            email_updated_at = now()
                        WHERE email_outbox_id = ?
                        """,
                emailOutboxId
        );

        int processedEmails = emailOutboxProcessor.processDueEmails();

        assertThat(processedEmails).isEqualTo(1);

        Map<String, Object> outboxRow = jdbcTemplate.queryForMap("""
                        SELECT
                            email_status,
                            provider,
                            provider_message_id,
                            attempts,
                            last_error,
                            email_scheduled_at,
                            email_sent_at,
                            email_last_failed_at
                        FROM email_outbox
                        WHERE email_outbox_id = ?
                        """,
                emailOutboxId
        );

        assertThat(outboxRow.get("email_status"))
                .as("Outbox non inviata. attempts=%s, last_error=%s, nextScheduledAt=%s",
                        outboxRow.get("attempts"),
                        outboxRow.get("last_error"),
                        outboxRow.get("email_scheduled_at"))
                .isEqualTo("SENT");
        assertThat(outboxRow.get("provider")).isEqualTo("resend");
        assertThat((String) outboxRow.get("provider_message_id"))
                .isNotBlank();

        assertThat(outboxRow.get("last_error"))
                .as("L'email reale deve essere stata inviata senza errore provider")
                .isNull();

        int syncedFinalStatuses = finalStatusSyncService.syncFinalStatuses();

        assertThat(syncedFinalStatuses).isEqualTo(1);

        Map<String, Object> finalNotificationRow = jdbcTemplate.queryForMap("""
                        SELECT
                            email_final_status,
                            email_provider,
                            provider_message_id,
                            email_final_status_recorded_at
                        FROM finance_reminder_notifications
                        WHERE finance_reminder_notification_id = ?
                        """,
                notificationId
        );

        assertThat(finalNotificationRow.get("email_final_status")).isEqualTo("SENT");
        assertThat(finalNotificationRow.get("email_provider")).isEqualTo("resend");
        assertThat((String) finalNotificationRow.get("provider_message_id"))
                .isNotBlank();
        assertThat(finalNotificationRow.get("email_final_status_recorded_at"))
                .isNotNull();
    }

    private TestContext createContext() {
        UUID userGroupId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        createdUserGroupIds.add(userGroupId);

        jdbcTemplate.update("""
                        INSERT INTO user_groups (
                            user_group_id,
                            user_group_name
                        )
                        VALUES (?, ?)
                        """,
                userGroupId,
                "Real Email Reminder Test Group " + userGroupId
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
                "Real Email Reminder Test User",
                REAL_RECIPIENT,
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
                        INSERT INTO categories (
                            category_id,
                            user_group_id,
                            category_created_by_user_id,
                            category_current_name
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                categoryId,
                userGroupId,
                ownerUserId,
                "Real Email Reminder Category " + categoryId
        );

        jdbcTemplate.update("""
                        INSERT INTO accounts (
                            account_id,
                            account_name,
                            currency,
                            opening_balance,
                            opening_balance_date,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                accountId,
                "Real Email Reminder Account " + accountId,
                "EUR",
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO accounts_users (
                            account_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                accountId,
                ownerUserId,
                userGroupId
        );

        return new TestContext(
                userGroupId,
                ownerUserId,
                accountId,
                categoryId
        );
    }

    private void insertDueTransaction(
            TestContext context,
            UUID transactionId,
            String description,
            BigDecimal amount,
            LocalDate chargeDate,
            short reminderDaysBefore
    ) {
        jdbcTemplate.update("""
                        INSERT INTO transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            category_id,
                            transaction_charge_date,
                            account_id,
                            transaction_is_confirmed,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                transactionId,
                description,
                amount,
                context.categoryId(),
                chargeDate,
                context.accountId(),
                false,
                false,
                null,
                true,
                null,
                null,
                null,
                true,
                reminderDaysBefore,
                context.userGroupId()
        );
    }

    private void insertTransactionUser(
            UUID transactionId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update("""
                        INSERT INTO transactions_users (
                            transaction_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                transactionId,
                userId,
                userGroupId
        );
    }

    private void deleteGroupData(UUID userGroupId) {
        jdbcTemplate.update("""
                DELETE FROM finance_reminder_notifications
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM transactions_users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM transactions
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM accounts_users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM accounts
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM category_details_history details
                USING categories category
                WHERE details.category_id = category.category_id
                  AND category.user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM category_status_history status
                USING categories category
                WHERE status.category_id = category.category_id
                  AND category.user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM categories
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
            UUID accountId,
            UUID categoryId
    ) {
    }
}