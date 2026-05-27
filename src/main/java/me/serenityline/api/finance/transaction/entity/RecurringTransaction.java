package me.serenityline.api.finance.transaction.entity;

import jakarta.persistence.*;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.user.entity.UserGroup;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recurring_transactions")
public class RecurringTransaction {

    private static final boolean DEFAULT_REMINDER_ENABLED = true;
    private static final short DEFAULT_REMINDER_DAYS_BEFORE = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recurring_transaction_id", nullable = false)
    private UUID recurringTransactionId;

    @Column(name = "recurring_transaction_amount_is_adjustable", nullable = false)
    private boolean recurringTransactionAmountIsAdjustable;

    @Column(name = "recurring_transaction_first_payment_date", nullable = false)
    private LocalDate recurringTransactionFirstPaymentDate;

    @Column(name = "recurring_transaction_is_simulated", nullable = false)
    private boolean recurringTransactionIsSimulated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_group_id")
    private SimulationGroup simulationGroup;

    @Column(name = "recurring_transaction_reminder_enabled", nullable = false)
    private boolean recurringTransactionReminderEnabled;

    @Column(name = "recurring_transaction_reminder_days_before", nullable = false)
    private short recurringTransactionReminderDaysBefore;

    @Column(name = "recurring_transaction_created_at", nullable = false)
    private OffsetDateTime recurringTransactionCreatedAt;

    @Column(name = "recurring_transaction_updated_at", nullable = false)
    private OffsetDateTime recurringTransactionUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false)
    private UserGroup userGroup;

    protected RecurringTransaction() {
    }

    private RecurringTransaction(
            boolean recurringTransactionAmountIsAdjustable,
            LocalDate recurringTransactionFirstPaymentDate,
            boolean recurringTransactionIsSimulated,
            SimulationGroup simulationGroup,
            Boolean recurringTransactionReminderEnabled,
            Short recurringTransactionReminderDaysBefore,
            UserGroup userGroup
    ) {
        this.recurringTransactionAmountIsAdjustable = recurringTransactionAmountIsAdjustable;
        this.recurringTransactionFirstPaymentDate = Objects.requireNonNull(
                recurringTransactionFirstPaymentDate,
                "recurringTransactionFirstPaymentDate"
        );
        this.recurringTransactionIsSimulated = recurringTransactionIsSimulated;
        this.simulationGroup = simulationGroup;
        this.recurringTransactionReminderEnabled = recurringTransactionReminderEnabled == null
                ? DEFAULT_REMINDER_ENABLED
                : recurringTransactionReminderEnabled;
        this.recurringTransactionReminderDaysBefore = recurringTransactionReminderDaysBefore == null
                ? DEFAULT_REMINDER_DAYS_BEFORE
                : recurringTransactionReminderDaysBefore;
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");

        validateSimulationConsistency();
        validateReminderDaysBefore();
        validateSimulationGroupBelongsToUserGroup();
    }

    public static RecurringTransaction create(
            boolean recurringTransactionAmountIsAdjustable,
            LocalDate recurringTransactionFirstPaymentDate,
            boolean recurringTransactionIsSimulated,
            SimulationGroup simulationGroup,
            Boolean recurringTransactionReminderEnabled,
            Short recurringTransactionReminderDaysBefore,
            UserGroup userGroup
    ) {
        return new RecurringTransaction(
                recurringTransactionAmountIsAdjustable,
                recurringTransactionFirstPaymentDate,
                recurringTransactionIsSimulated,
                simulationGroup,
                recurringTransactionReminderEnabled,
                recurringTransactionReminderDaysBefore,
                userGroup
        );
    }

    public void updateMutableSettings(
            LocalDate firstPaymentDate,
            boolean amountIsAdjustable,
            boolean simulated,
            SimulationGroup simulationGroup,
            boolean reminderEnabled,
            short reminderDaysBefore
    ) {
        this.recurringTransactionFirstPaymentDate = Objects.requireNonNull(
                firstPaymentDate,
                "firstPaymentDate"
        );
        this.recurringTransactionAmountIsAdjustable = amountIsAdjustable;
        this.recurringTransactionIsSimulated = simulated;
        this.simulationGroup = simulationGroup;
        this.recurringTransactionReminderEnabled = reminderEnabled;
        this.recurringTransactionReminderDaysBefore = reminderDaysBefore;

        validateSimulationConsistency();
        validateReminderDaysBefore();
        validateSimulationGroupBelongsToUserGroup();
        touch();
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();

        if (recurringTransactionCreatedAt == null) {
            recurringTransactionCreatedAt = now;
        }

        if (recurringTransactionUpdatedAt == null) {
            recurringTransactionUpdatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        touch();
    }

    public void touch() {
        recurringTransactionUpdatedAt = OffsetDateTime.now();
    }

    private void validateSimulationConsistency() {
        if (recurringTransactionIsSimulated && simulationGroup == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.simulationGroupRequired");
        }

        if (!recurringTransactionIsSimulated && simulationGroup != null) {
            throw new IllegalArgumentException("finance.recurringTransaction.simulationGroupNotAllowed");
        }
    }

    private void validateReminderDaysBefore() {
        if (recurringTransactionReminderDaysBefore < 0 || recurringTransactionReminderDaysBefore > 366) {
            throw new IllegalArgumentException("finance.recurringTransaction.reminderDaysInvalid");
        }
    }

    private void validateSimulationGroupBelongsToUserGroup() {
        if (simulationGroup == null) {
            return;
        }

        if (userGroup == null || simulationGroup.getUserGroup() == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.simulationGroupMismatch");
        }

        if (!Objects.equals(
                userGroup.getUserGroupId(),
                simulationGroup.getUserGroup().getUserGroupId()
        )) {
            throw new IllegalArgumentException("finance.recurringTransaction.simulationGroupMismatch");
        }
    }

    public UUID getRecurringTransactionId() {
        return recurringTransactionId;
    }

    public boolean isRecurringTransactionAmountIsAdjustable() {
        return recurringTransactionAmountIsAdjustable;
    }

    public LocalDate getRecurringTransactionFirstPaymentDate() {
        return recurringTransactionFirstPaymentDate;
    }

    public boolean isRecurringTransactionIsSimulated() {
        return recurringTransactionIsSimulated;
    }

    public SimulationGroup getSimulationGroup() {
        return simulationGroup;
    }

    public boolean isRecurringTransactionReminderEnabled() {
        return recurringTransactionReminderEnabled;
    }

    public short getRecurringTransactionReminderDaysBefore() {
        return recurringTransactionReminderDaysBefore;
    }

    public OffsetDateTime getRecurringTransactionCreatedAt() {
        return recurringTransactionCreatedAt;
    }

    public OffsetDateTime getRecurringTransactionUpdatedAt() {
        return recurringTransactionUpdatedAt;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }
}