package me.serenityline.api.finance.reminder.email;

import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotification;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotificationCreationService;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotificationRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class FinanceReminderEmailOutboxServiceIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY = "EUR";
    private static final String DESCRIPTION = "Pagamento affitto";

    private static final LocalDate OPENING_BALANCE_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate CHARGE_DATE = LocalDate.of(2026, 6, 10);
    private static final LocalDate REMINDER_DATE = LocalDate.of(2026, 6, 3);
    private static final LocalDate RECURRING_FIRST_PAYMENT_DATE = LocalDate.of(2026, 1, 10);
    private static final LocalDate RECURRING_LOGICAL_DATE = LocalDate.of(2026, 6, 10);

    private static final byte[] GCM_IV = new byte[12];
    private static final byte[] GCM_TAG = new byte[16];

    @Autowired
    private FinanceReminderEmailOutboxService service;

    @Autowired
    private FinanceReminderNotificationCreationService notificationCreationService;

    @Autowired
    private FinanceReminderNotificationRepository notificationRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    private UUID userGroupId;
    private UUID ownerUserId;
    private UUID categoryId;
    private UUID accountId;
    private UUID transactionId;
    private UUID recurringTransactionId;
    private String ownerEmail;

    private static EncryptedValue encryptedValue(String plainText) {
        return new EncryptedValue(
                ("encrypted:" + plainText).getBytes(StandardCharsets.UTF_8),
                Arrays.copyOf(GCM_IV, GCM_IV.length),
                Arrays.copyOf(GCM_TAG, GCM_TAG.length)
        );
    }

    private static String decodeEncryptedValue(byte[] encryptedValue) {
        return new String(encryptedValue, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        userGroupId = UUID.randomUUID();
        ownerUserId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        recurringTransactionId = UUID.randomUUID();

        ownerEmail = "owner-" + ownerUserId + "@example.com";

        when(emailOutboxEncryptionService.getEncryptionKeyId())
                .thenReturn("test-email-key");

        when(emailOutboxEncryptionService.encrypt(anyString()))
                .thenAnswer(invocation -> encryptedValue(invocation.getArgument(0, String.class)));

        insertBaseData();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    void shouldCreateTransactionReminderEmailOutboxAndAttachItToNotification() {
        FinanceReminderNotification notification = createTransactionNotification();

        UUID emailOutboxId = service.ensureEmailOutboxForNotification(
                notification.getFinanceReminderNotificationId()
        );

        EmailOutbox emailOutbox = emailOutboxRepository.findById(emailOutboxId).orElseThrow();

        assertThat(emailOutbox.getRecipientEmail()).isEqualTo(ownerEmail);
        assertThat(emailOutbox.getEmailType()).isEqualTo(EmailOutboxType.TRANSACTION_REMINDER);
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.getEncryptionKeyId()).isEqualTo("test-email-key");
        assertThat(emailOutbox.isDeleteBodyAfterSend()).isTrue();

        assertThat(emailOutbox.getSubjectEncrypted()).isNotEmpty();
        assertThat(emailOutbox.getSubjectIv()).hasSize(12);
        assertThat(emailOutbox.getSubjectTag()).hasSize(16);

        assertThat(emailOutbox.getBodyHtmlEncrypted()).isNull();
        assertThat(emailOutbox.getBodyHtmlIv()).isNull();
        assertThat(emailOutbox.getBodyHtmlTag()).isNull();

        assertThat(emailOutbox.getBodyTextEncrypted()).isNotEmpty();
        assertThat(emailOutbox.getBodyTextIv()).hasSize(12);
        assertThat(emailOutbox.getBodyTextTag()).hasSize(16);

        String storedTextBody = decodeEncryptedValue(emailOutbox.getBodyTextEncrypted());

        assertThat(storedTextBody)
                .contains(DESCRIPTION)
                .contains("123");

        FinanceReminderNotification reloadedNotification = notificationRepository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloadedNotification.getEmailOutboxId()).isEqualTo(emailOutboxId);
    }

    @Test
    void shouldCreateRecurringReminderEmailOutboxAndAttachItToNotification() {
        FinanceReminderNotification notification = createRecurringNotification();

        UUID emailOutboxId = service.ensureEmailOutboxForNotification(
                notification.getFinanceReminderNotificationId()
        );

        EmailOutbox emailOutbox = emailOutboxRepository.findById(emailOutboxId).orElseThrow();

        assertThat(emailOutbox.getRecipientEmail()).isEqualTo(ownerEmail);
        assertThat(emailOutbox.getEmailType()).isEqualTo(EmailOutboxType.RECURRING_TRANSACTION_REMINDER);
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.getEncryptionKeyId()).isEqualTo("test-email-key");
        assertThat(emailOutbox.isDeleteBodyAfterSend()).isTrue();

        String storedTextBody = decodeEncryptedValue(emailOutbox.getBodyTextEncrypted());

        assertThat(storedTextBody)
                .contains(DESCRIPTION)
                .contains("123");

        FinanceReminderNotification reloadedNotification = notificationRepository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloadedNotification.getEmailOutboxId()).isEqualTo(emailOutboxId);
    }

    @Test
    void shouldReturnExistingEmailOutboxIdWithoutCreatingAnotherEmail() {
        FinanceReminderNotification notification = createTransactionNotification();

        UUID firstEmailOutboxId = service.ensureEmailOutboxForNotification(
                notification.getFinanceReminderNotificationId()
        );

        clearInvocations(emailOutboxEncryptionService);

        UUID secondEmailOutboxId = service.ensureEmailOutboxForNotification(
                notification.getFinanceReminderNotificationId()
        );

        assertThat(secondEmailOutboxId).isEqualTo(firstEmailOutboxId);
        assertThat(countEmailOutboxRowsForUser()).isEqualTo(1L);

        verifyNoInteractions(emailOutboxEncryptionService);
    }

    @Test
    void shouldRejectMissingNotification() {
        assertThatThrownBy(() -> service.ensureEmailOutboxForNotification(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.reminderNotification.notFound");
    }

    @Test
    void shouldRejectNullNotificationId() {
        assertThatThrownBy(() -> service.ensureEmailOutboxForNotification(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.reminderNotification.id.required");
    }

    @Test
    void shouldRejectWhenPaymentEmailRemindersAreDisabledAfterNotificationCreation() {
        FinanceReminderNotification notification = createTransactionNotification();

        jdbcTemplate.update("""
                UPDATE users
                SET payment_email_reminders_enabled = FALSE
                WHERE user_id = ?
                """, ownerUserId);

        assertThatThrownBy(() -> service.ensureEmailOutboxForNotification(
                notification.getFinanceReminderNotificationId()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.reminderEmail.paymentEmailRemindersDisabled");

        assertThat(countEmailOutboxRowsForUser()).isZero();
    }

    @Test
    void shouldRejectNotificationWithFinalEmailStatusAlreadyRecorded() {
        FinanceReminderNotification notification = createTransactionNotification();

        jdbcTemplate.update("""
                UPDATE finance_reminder_notifications
                SET email_final_status = 'FAILED',
                    email_final_status_recorded_at = now()
                WHERE finance_reminder_notification_id = ?
                """, notification.getFinanceReminderNotificationId());

        assertThatThrownBy(() -> service.ensureEmailOutboxForNotification(
                notification.getFinanceReminderNotificationId()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.reminderNotification.emailFinalStatus.alreadyRecorded");

        assertThat(countEmailOutboxRowsForUser()).isZero();
    }

    private FinanceReminderNotification createTransactionNotification() {
        return notificationCreationService.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();
    }

    private FinanceReminderNotification createRecurringNotification() {
        return notificationCreationService.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();
    }

    private void insertBaseData() {
        jdbcTemplate.update("""
                        INSERT INTO user_groups (
                            user_group_id,
                            user_group_name
                        )
                        VALUES (?, ?)
                        """,
                userGroupId,
                "Reminder email outbox test group " + userGroupId
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
                "Samuel Test",
                ownerEmail,
                userGroupId,
                "OWNER",
                "USER",
                "it-IT",
                "DEFAULT",
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
                "Reminder email category " + categoryId
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
                "Reminder email account " + accountId,
                CURRENCY,
                BigDecimal.ZERO,
                OPENING_BALANCE_DATE,
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

        jdbcTemplate.update("""
                        INSERT INTO transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            category_id,
                            transaction_charge_date,
                            transaction_is_confirmed,
                            account_id,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                transactionId,
                DESCRIPTION,
                AMOUNT,
                categoryId,
                CHARGE_DATE,
                false,
                accountId,
                false,
                null,
                true,
                true,
                7,
                userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO transactions_users (
                            transaction_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                transactionId,
                ownerUserId,
                userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            simulation_group_id,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                recurringTransactionId,
                RECURRING_FIRST_PAYMENT_DATE,
                false,
                null,
                true,
                7,
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
    }

    private void deleteTestData() {
        if (userGroupId == null) {
            return;
        }

        jdbcTemplate.update("""
                DELETE FROM finance_reminder_notifications
                WHERE user_group_id = ?
                """, userGroupId);

        if (ownerUserId != null) {
            jdbcTemplate.update("""
                    DELETE FROM email_outbox
                    WHERE user_id = ?
                    """, ownerUserId);
        }

        jdbcTemplate.update("""
                DELETE FROM transactions_users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM transactions
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transactions_users
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transaction_details_history
                WHERE user_group_id = ?
                """, userGroupId);

        if (recurringTransactionId != null) {
            jdbcTemplate.update("""
                    DELETE FROM recurring_transaction_history
                    WHERE recurring_transaction_id = ?
                    """, recurringTransactionId);
        }

        jdbcTemplate.update("""
                DELETE FROM recurring_transactions
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

        if (categoryId != null) {
            jdbcTemplate.update("""
                    DELETE FROM category_details_history
                    WHERE category_id = ?
                    """, categoryId);

            jdbcTemplate.update("""
                    DELETE FROM category_status_history
                    WHERE category_id = ?
                    """, categoryId);
        }

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

    private Long countEmailOutboxRowsForUser() {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM email_outbox
                        WHERE user_id = ?
                        """,
                Long.class,
                ownerUserId
        );
    }
}