package me.serenityline.api.finance.reminder.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class FinanceReminderNotificationCreationServiceIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY = "EUR";

    private static final LocalDate OPENING_BALANCE_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate CHARGE_DATE = LocalDate.of(2026, 6, 10);
    private static final LocalDate REMINDER_DATE = LocalDate.of(2026, 6, 3);
    private static final LocalDate RECURRING_FIRST_PAYMENT_DATE = LocalDate.of(2026, 1, 10);
    private static final LocalDate RECURRING_LOGICAL_DATE = LocalDate.of(2026, 6, 10);
    private static final String DESCRIPTION = "Reminder test movement";

    private static final byte[] ENCRYPTED_VALUE = new byte[]{1};
    private static final byte[] GCM_IV = new byte[12];
    private static final byte[] GCM_TAG = new byte[16];

    @Autowired
    private FinanceReminderNotificationCreationService service;

    @Autowired
    private FinanceReminderNotificationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userGroupId;

    private UUID ownerUserId;
    private UUID remindersDisabledUserId;

    private UUID categoryId;
    private UUID accountId;
    private UUID simulationGroupId;

    private UUID transactionId;
    private UUID simulatedTransactionId;
    private UUID reminderDisabledTransactionId;
    private UUID unlinkedTransactionId;

    private UUID recurringTransactionId;
    private UUID simulatedRecurringTransactionId;
    private UUID reminderDisabledRecurringTransactionId;
    private UUID unlinkedRecurringTransactionId;

    private UUID secondTransactionId;
    private UUID systemGeneratedTransactionId;

    private static String emailFor(String prefix, UUID id) {
        return prefix + "-" + id + "@example.com";
    }

    @BeforeEach
    void setUp() {
        userGroupId = UUID.randomUUID();

        ownerUserId = UUID.randomUUID();
        remindersDisabledUserId = UUID.randomUUID();

        categoryId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        simulationGroupId = UUID.randomUUID();

        transactionId = UUID.randomUUID();
        simulatedTransactionId = UUID.randomUUID();
        reminderDisabledTransactionId = UUID.randomUUID();
        unlinkedTransactionId = UUID.randomUUID();

        recurringTransactionId = UUID.randomUUID();
        simulatedRecurringTransactionId = UUID.randomUUID();
        reminderDisabledRecurringTransactionId = UUID.randomUUID();
        unlinkedRecurringTransactionId = UUID.randomUUID();

        secondTransactionId = UUID.randomUUID();
        systemGeneratedTransactionId = UUID.randomUUID();

        insertBaseData();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    void shouldCreateTransactionReminderNotification() {
        Optional<FinanceReminderNotification> created = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        );

        assertThat(created).isPresent();

        FinanceReminderNotification notification = created.orElseThrow();

        assertThat(notification.getFinanceReminderNotificationId()).isNotNull();
        assertThat(notification.getUserId()).isEqualTo(ownerUserId);
        assertThat(notification.getUserGroupId()).isEqualTo(userGroupId);

        assertThat(notification.getTransactionId()).isEqualTo(transactionId);
        assertThat(notification.getRecurringTransactionId()).isNull();
        assertThat(notification.getRecurringTransactionLogicalDate()).isNull();

        assertThat(notification.getChargeDate()).isEqualTo(CHARGE_DATE);
        assertThat(notification.getNotifiedDescription()).isEqualTo(DESCRIPTION);
        assertThat(notification.getNotifiedAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(notification.getNotifiedCurrency()).isEqualTo(CURRENCY);
        assertThat(notification.getReminderDate()).isEqualTo(REMINDER_DATE);

        assertThat(notification.getEmailOutboxId()).isNull();
        assertThat(notification.getEmailFinalStatus()).isNull();
        assertThat(notification.getEmailFinalStatusRecordedAt()).isNull();
        assertThat(notification.getEmailProvider()).isNull();
        assertThat(notification.getProviderMessageId()).isNull();
        assertThat(notification.getReminderNotificationCreatedAt()).isNotNull();

        assertThat(countTransactionNotifications(ownerUserId, transactionId)).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyWhenTransactionReminderNotificationAlreadyExists() {
        Optional<FinanceReminderNotification> first = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        );

        Optional<FinanceReminderNotification> second = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        );

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(countTransactionNotifications(ownerUserId, transactionId)).isEqualTo(1L);
    }

    @Test
    void shouldRejectTransactionReminderForMissingTransaction() {
        UUID missingTransactionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                missingTransactionId,
                CHARGE_DATE,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionNotifications(ownerUserId, missingTransactionId)).isZero();
    }

    @Test
    void shouldRejectTransactionReminderForSimulatedTransaction() {
        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                simulatedTransactionId,
                CHARGE_DATE,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionNotifications(ownerUserId, simulatedTransactionId)).isZero();
    }

    @Test
    void shouldRejectTransactionReminderForTransactionWithReminderDisabled() {
        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                reminderDisabledTransactionId,
                CHARGE_DATE,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionNotifications(ownerUserId, reminderDisabledTransactionId)).isZero();
    }

    @Test
    void shouldRejectTransactionReminderWhenUserIsNotLinkedToTransaction() {
        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                unlinkedTransactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionNotifications(ownerUserId, unlinkedTransactionId)).isZero();
    }

    @Test
    void shouldRejectTransactionReminderWhenUserHasPaymentEmailRemindersDisabled() {
        linkTransactionToUser(transactionId, remindersDisabledUserId);

        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                remindersDisabledUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionNotifications(remindersDisabledUserId, transactionId)).isZero();
    }

    @Test
    void shouldCreateRecurringReminderNotification() {
        Optional<FinanceReminderNotification> created = service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        );

        assertThat(created).isPresent();

        FinanceReminderNotification notification = created.orElseThrow();

        assertThat(notification.getFinanceReminderNotificationId()).isNotNull();
        assertThat(notification.getUserId()).isEqualTo(ownerUserId);
        assertThat(notification.getUserGroupId()).isEqualTo(userGroupId);

        assertThat(notification.getTransactionId()).isNull();
        assertThat(notification.getRecurringTransactionId()).isEqualTo(recurringTransactionId);
        assertThat(notification.getRecurringTransactionLogicalDate()).isEqualTo(RECURRING_LOGICAL_DATE);

        assertThat(notification.getChargeDate()).isEqualTo(CHARGE_DATE);
        assertThat(notification.getNotifiedDescription()).isEqualTo(DESCRIPTION);
        assertThat(notification.getNotifiedAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(notification.getNotifiedCurrency()).isEqualTo(CURRENCY);
        assertThat(notification.getReminderDate()).isEqualTo(REMINDER_DATE);

        assertThat(notification.getEmailOutboxId()).isNull();
        assertThat(notification.getEmailFinalStatus()).isNull();
        assertThat(notification.getReminderNotificationCreatedAt()).isNotNull();

        assertThat(countRecurringNotifications(
                ownerUserId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyWhenRecurringReminderNotificationAlreadyExists() {
        Optional<FinanceReminderNotification> first = service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        );

        Optional<FinanceReminderNotification> second = service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        );

        assertThat(first).isPresent();
        assertThat(second).isEmpty();

        assertThat(countRecurringNotifications(
                ownerUserId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isEqualTo(1L);
    }

    @Test
    void shouldRejectRecurringReminderForMissingRecurringTransaction() {
        UUID missingRecurringTransactionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                missingRecurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countRecurringNotifications(
                ownerUserId,
                missingRecurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isZero();
    }

    @Test
    void shouldRejectRecurringReminderForSimulatedRecurringTransaction() {
        assertThatThrownBy(() -> service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                simulatedRecurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countRecurringNotifications(
                ownerUserId,
                simulatedRecurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isZero();
    }

    @Test
    void shouldRejectRecurringReminderForRecurringTransactionWithReminderDisabled() {
        assertThatThrownBy(() -> service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                reminderDisabledRecurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countRecurringNotifications(
                ownerUserId,
                reminderDisabledRecurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isZero();
    }

    @Test
    void shouldRejectRecurringReminderWhenUserIsNotLinkedToRecurringTransaction() {
        assertThatThrownBy(() -> service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                unlinkedRecurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countRecurringNotifications(
                ownerUserId,
                unlinkedRecurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isZero();
    }

    @Test
    void shouldRejectRecurringReminderWhenUserHasPaymentEmailRemindersDisabled() {
        linkRecurringTransactionToUser(recurringTransactionId, remindersDisabledUserId);

        assertThatThrownBy(() -> service.createForRecurringOccurrenceIfAbsent(
                remindersDisabledUserId,
                userGroupId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countRecurringNotifications(
                remindersDisabledUserId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isZero();
    }

    @Test
    void shouldAttachEmailOutboxId() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        FinanceReminderNotification reloaded = repository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloaded.getEmailOutboxId()).isEqualTo(emailOutboxId);
        assertThat(reloaded.getEmailFinalStatus()).isNull();
    }

    @Test
    void shouldRecordFinalEmailStatus() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        service.recordFinalEmailStatus(
                emailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "resend",
                "resend-message-id-123"
        );

        FinanceReminderNotification reloaded = repository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloaded.getEmailFinalStatus()).isEqualTo(FinanceReminderEmailFinalStatus.SENT);
        assertThat(reloaded.getEmailFinalStatusRecordedAt()).isNotNull();
        assertThat(reloaded.getEmailProvider()).isEqualTo("resend");
        assertThat(reloaded.getProviderMessageId()).isEqualTo("resend-message-id-123");
    }

    @Test
    void shouldIgnoreRecordingSameFinalEmailStatusTwice() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        service.recordFinalEmailStatus(
                emailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "resend",
                "resend-message-id-123"
        );

        service.recordFinalEmailStatus(
                emailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "resend",
                "resend-message-id-123"
        );

        FinanceReminderNotification reloaded = repository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloaded.getEmailFinalStatus()).isEqualTo(FinanceReminderEmailFinalStatus.SENT);
        assertThat(reloaded.getEmailProvider()).isEqualTo("resend");
        assertThat(reloaded.getProviderMessageId()).isEqualTo("resend-message-id-123");
    }

    @Test
    void shouldRejectDifferentFinalEmailStatusWhenAlreadyRecorded() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        service.recordFinalEmailStatus(
                emailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "resend",
                "resend-message-id-123"
        );

        assertThatThrownBy(() -> service.recordFinalEmailStatus(
                emailOutboxId,
                FinanceReminderEmailFinalStatus.FAILED,
                "resend",
                "resend-message-id-123"
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldAllowDeletingTransactionAfterReminderNotificationWasCreated() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        jdbcTemplate.update("""
                DELETE FROM transactions
                WHERE transaction_id = ?
                  AND user_group_id = ?
                """, transactionId, userGroupId);

        FinanceReminderNotification reloaded = repository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloaded.getTransactionId()).isEqualTo(transactionId);
        assertThat(countTransactionNotifications(ownerUserId, transactionId)).isEqualTo(1L);
    }

    @Test
    void shouldRejectDeletingRecurringTransactionAfterReminderNotificationWasCreated() {
        service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                DELETE FROM recurring_transactions
                WHERE recurring_transaction_id = ?
                  AND user_group_id = ?
                """, recurringTransactionId, userGroupId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countRecurringNotifications(
                ownerUserId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isEqualTo(1L);
    }

    @Test
    void shouldRejectSameEmailOutboxIdForDifferentNotifications() {
        FinanceReminderNotification first = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        FinanceReminderNotification second = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                secondTransactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(first.getFinanceReminderNotificationId(), emailOutboxId);

        assertThatThrownBy(() -> service.attachEmailOutboxId(
                second.getFinanceReminderNotificationId(),
                emailOutboxId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectReplacingExistingEmailOutboxId() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID firstEmailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");
        UUID secondEmailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                firstEmailOutboxId
        );

        assertThatThrownBy(() -> service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                secondEmailOutboxId
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRecordFailedFinalEmailStatusWithoutProvider() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        service.recordFinalEmailStatus(
                emailOutboxId,
                FinanceReminderEmailFinalStatus.FAILED,
                null,
                null
        );

        FinanceReminderNotification reloaded = repository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloaded.getEmailFinalStatus()).isEqualTo(FinanceReminderEmailFinalStatus.FAILED);
        assertThat(reloaded.getEmailFinalStatusRecordedAt()).isNotNull();
        assertThat(reloaded.getEmailProvider()).isNull();
        assertThat(reloaded.getProviderMessageId()).isNull();
    }

    @Test
    void shouldRejectProviderMessageIdWithoutProvider() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        assertThatThrownBy(() -> service.recordFinalEmailStatus(
                emailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                null,
                "provider-message-id"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldIgnoreAttachingSameEmailOutboxIdTwice() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        FinanceReminderNotification reloaded = repository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloaded.getEmailOutboxId()).isEqualTo(emailOutboxId);
    }

    @Test
    void shouldCreateTwoRecurringNotificationsForDifferentLogicalDates() {
        LocalDate secondLogicalDate = RECURRING_LOGICAL_DATE.plusMonths(1);
        LocalDate secondChargeDate = CHARGE_DATE.plusMonths(1);
        LocalDate secondReminderDate = REMINDER_DATE.plusMonths(1);

        Optional<FinanceReminderNotification> first = service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        );

        Optional<FinanceReminderNotification> second = service.createForRecurringOccurrenceIfAbsent(
                ownerUserId,
                userGroupId,
                recurringTransactionId,
                secondLogicalDate,
                secondChargeDate,
                DESCRIPTION,
                AMOUNT,
                CURRENCY,
                secondReminderDate
        );

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.orElseThrow().getFinanceReminderNotificationId())
                .isNotEqualTo(second.orElseThrow().getFinanceReminderNotificationId());

        assertThat(countRecurringNotifications(
                ownerUserId,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE
        )).isEqualTo(1L);

        assertThat(countRecurringNotifications(
                ownerUserId,
                recurringTransactionId,
                secondLogicalDate
        )).isEqualTo(1L);
    }

    @Test
    void shouldRejectTransactionReminderForSystemGeneratedTransaction() {
        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                systemGeneratedTransactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionNotifications(ownerUserId, systemGeneratedTransactionId)).isZero();
    }

    @Test
    void shouldRejectAttachingMissingEmailOutboxId() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID missingEmailOutboxId = UUID.randomUUID();

        assertThatThrownBy(() -> service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                missingEmailOutboxId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldKeepNotificationWhenEmailOutboxIsDeleted() {
        FinanceReminderNotification notification = service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        ).orElseThrow();

        UUID emailOutboxId = insertEmailOutbox(ownerUserId, "TRANSACTION_REMINDER");

        service.attachEmailOutboxId(
                notification.getFinanceReminderNotificationId(),
                emailOutboxId
        );

        jdbcTemplate.update("""
                DELETE FROM email_outbox
                WHERE email_outbox_id = ?
                """, emailOutboxId);

        FinanceReminderNotification reloaded = repository.findById(
                notification.getFinanceReminderNotificationId()
        ).orElseThrow();

        assertThat(reloaded.getEmailOutboxId()).isNull();
        assertThat(reloaded.getTransactionId()).isEqualTo(transactionId);
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE,
                DESCRIPTION,
                BigDecimal.ZERO,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidCurrency() {
        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                "EURO",
                REMINDER_DATE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectReminderDateAfterChargeDate() {
        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE, DESCRIPTION,
                AMOUNT,
                CURRENCY,
                CHARGE_DATE.plusDays(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankDescription() {
        assertThatThrownBy(() -> service.createForTransactionIfAbsent(
                ownerUserId,
                userGroupId,
                transactionId,
                CHARGE_DATE,
                " ",
                AMOUNT,
                CURRENCY,
                REMINDER_DATE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private void insertBaseData() {
        insertUserGroup();
        insertUsers();

        insertCategory();
        insertAccount();
        insertSimulationGroup();

        insertTransaction(transactionId, false, null, true);
        insertTransaction(secondTransactionId, false, null, true);
        insertTransaction(simulatedTransactionId, true, simulationGroupId, true);
        insertTransaction(reminderDisabledTransactionId, false, null, false);
        insertTransaction(unlinkedTransactionId, false, null, true);


        linkTransactionToUser(transactionId, ownerUserId);
        linkTransactionToUser(secondTransactionId, ownerUserId);
        linkTransactionToUser(simulatedTransactionId, ownerUserId);
        linkTransactionToUser(reminderDisabledTransactionId, ownerUserId);

        insertRecurringTransaction(recurringTransactionId, false, null, true);
        insertRecurringTransaction(simulatedRecurringTransactionId, true, simulationGroupId, true);
        insertRecurringTransaction(reminderDisabledRecurringTransactionId, false, null, false);
        insertRecurringTransaction(unlinkedRecurringTransactionId, false, null, true);

        linkRecurringTransactionToUser(recurringTransactionId, ownerUserId);
        linkRecurringTransactionToUser(simulatedRecurringTransactionId, ownerUserId);
        linkRecurringTransactionToUser(reminderDisabledRecurringTransactionId, ownerUserId);

        insertConfirmedRecurringOccurrenceTransaction(systemGeneratedTransactionId, recurringTransactionId);
        linkTransactionToUser(systemGeneratedTransactionId, ownerUserId);
    }

    private void insertUserGroup() {
        jdbcTemplate.update("""
                        INSERT INTO user_groups (
                            user_group_id,
                            user_group_name
                        )
                        VALUES (?, ?)
                        """,
                userGroupId,
                "Reminder Test Group " + userGroupId
        );
    }

    private void insertUsers() {
        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_password_hash,
                            user_is_enabled,
                            payment_email_reminders_enabled
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                ownerUserId,
                "Reminder Owner",
                emailFor("reminder-owner", ownerUserId),
                userGroupId,
                "OWNER",
                "{noop}password",
                true,
                true
        );

        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_password_hash,
                            user_is_enabled,
                            payment_email_reminders_enabled
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                remindersDisabledUserId,
                "Reminder Disabled User",
                emailFor("reminder-disabled", remindersDisabledUserId),
                userGroupId,
                "COLLABORATOR",
                "{noop}password",
                true,
                false
        );
    }

    private void insertCategory() {
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
                "Reminder Test Category " + categoryId
        );
    }

    private void insertAccount() {
        jdbcTemplate.update("""
                        INSERT INTO accounts (
                            account_id,
                            account_name,
                            currency,
                            opening_balance_date,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                accountId,
                "Reminder Test Account " + accountId,
                CURRENCY,
                OPENING_BALANCE_DATE,
                userGroupId
        );
    }

    private void insertSimulationGroup() {
        jdbcTemplate.update("""
                        INSERT INTO simulation_groups (
                            simulation_group_id,
                            user_group_id,
                            simulation_group_name
                        )
                        VALUES (?, ?, ?)
                        """,
                simulationGroupId,
                userGroupId,
                "Reminder Test Simulation " + simulationGroupId
        );
    }

    private void insertTransaction(
            UUID id,
            boolean simulated,
            UUID simulationGroupId,
            boolean reminderEnabled
    ) {
        insertTransaction(id, simulated, simulationGroupId, reminderEnabled, true);
    }

    private void insertTransaction(
            UUID id,
            boolean simulated,
            UUID simulationGroupId,
            boolean reminderEnabled,
            boolean userEntered
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
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                "Reminder test transaction " + id,
                AMOUNT,
                categoryId,
                CHARGE_DATE,
                accountId,
                false,
                simulated,
                simulationGroupId,
                userEntered,
                reminderEnabled,
                7,
                userGroupId
        );
    }

    private void linkTransactionToUser(UUID transactionId, UUID userId) {
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

    private void insertRecurringTransaction(
            UUID id,
            boolean simulated,
            UUID simulationGroupId,
            boolean reminderEnabled
    ) {
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
                id,
                RECURRING_FIRST_PAYMENT_DATE,
                simulated,
                simulationGroupId,
                reminderEnabled,
                7,
                userGroupId
        );
    }

    private void linkRecurringTransactionToUser(UUID recurringTransactionId, UUID userId) {
        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions_users (
                            recurring_transaction_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                recurringTransactionId,
                userId,
                userGroupId
        );
    }

    private UUID insertEmailOutbox(UUID userId, String emailType) {
        UUID emailOutboxId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

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
                            email_status,
                            attempts,
                            max_attempts,
                            email_scheduled_at,
                            email_created_at,
                            email_updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                emailOutboxId,
                userId,
                emailFor("reminder-recipient", userId),
                emailType,
                "test-key",
                ENCRYPTED_VALUE,
                GCM_IV,
                GCM_TAG,
                ENCRYPTED_VALUE,
                GCM_IV,
                GCM_TAG,
                true,
                "PENDING",
                0,
                6,
                now,
                now,
                now
        );

        return emailOutboxId;
    }

    private long countTransactionNotifications(UUID userId, UUID transactionId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM finance_reminder_notifications
                        WHERE user_group_id = ?
                          AND user_id = ?
                          AND transaction_id = ?
                        """,
                Long.class,
                userGroupId,
                userId,
                transactionId
        );

        return count == null ? 0L : count;
    }

    private long countRecurringNotifications(
            UUID userId,
            UUID recurringTransactionId,
            LocalDate logicalDate
    ) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM finance_reminder_notifications
                        WHERE user_group_id = ?
                          AND user_id = ?
                          AND recurring_transaction_id = ?
                          AND recurring_transaction_logical_date = ?
                        """,
                Long.class,
                userGroupId,
                userId,
                recurringTransactionId,
                logicalDate
        );

        return count == null ? 0L : count;
    }

    private void deleteTestData() {
        if (userGroupId == null) {
            return;
        }

        jdbcTemplate.update("""
                DELETE FROM finance_reminder_notifications
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM email_outbox
                WHERE user_id IN (?, ?)
                """, ownerUserId, remindersDisabledUserId);

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

        jdbcTemplate.update("""
                DELETE FROM recurring_transaction_history
                WHERE recurring_transaction_id IN (
                    SELECT recurring_transaction_id
                    FROM recurring_transactions
                    WHERE user_group_id = ?
                )
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM recurring_transactions
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM simulation_groups_accounts
                WHERE user_group_id = ?
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM simulation_groups
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
                DELETE FROM category_details_history
                WHERE category_id IN (
                    SELECT category_id
                    FROM categories
                    WHERE user_group_id = ?
                )
                """, userGroupId);

        jdbcTemplate.update("""
                DELETE FROM category_status_history
                WHERE category_id IN (
                    SELECT category_id
                    FROM categories
                    WHERE user_group_id = ?
                )
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

    private void insertConfirmedRecurringOccurrenceTransaction(
            UUID id,
            UUID recurringTransactionId
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
                id,
                "Confirmed recurring occurrence transaction " + id,
                AMOUNT,
                categoryId,
                CHARGE_DATE,
                accountId,
                true,
                false,
                null,
                false,
                recurringTransactionId,
                RECURRING_LOGICAL_DATE,
                OffsetDateTime.now(),
                true,
                7,
                userGroupId
        );
    }


}