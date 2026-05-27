package me.serenityline.api.finance.transaction.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recurring_transaction_history")
public class RecurringTransactionHistory {

    private static final PaymentDateAdjustmentPolicy DEFAULT_PAYMENT_DATE_ADJUSTMENT_POLICY =
            PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recurring_transaction_history_id", nullable = false)
    private UUID recurringTransactionHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_transaction_id", nullable = false)
    private RecurringTransaction recurringTransaction;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "day_of_unit", nullable = false)
    private short dayOfUnit;

    @Column(name = "recurrence_interval", nullable = false)
    private short recurrenceInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_unit", nullable = false, length = 50)
    private RecurrenceUnit recurrenceUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_date_adjustment_policy", nullable = false, length = 50)
    private PaymentDateAdjustmentPolicy paymentDateAdjustmentPolicy;

    @Column(name = "payment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "recurring_transaction_end_date")
    private LocalDate recurringTransactionEndDate;

    @Column(name = "final_payment_amount", precision = 19, scale = 2)
    private BigDecimal finalPaymentAmount;

    @Column(name = "recurring_transaction_history_created_at", nullable = false)
    private OffsetDateTime recurringTransactionHistoryCreatedAt;

    protected RecurringTransactionHistory() {
    }

    private RecurringTransactionHistory(
            RecurringTransaction recurringTransaction,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Short dayOfUnit,
            Short recurrenceInterval,
            RecurrenceUnit recurrenceUnit,
            PaymentDateAdjustmentPolicy paymentDateAdjustmentPolicy,
            BigDecimal paymentAmount,
            LocalDate recurringTransactionEndDate,
            BigDecimal finalPaymentAmount
    ) {
        this.recurringTransaction = Objects.requireNonNull(
                recurringTransaction,
                "recurringTransaction"
        );
        this.effectiveFrom = Objects.requireNonNull(
                effectiveFrom,
                "effectiveFrom"
        );
        this.effectiveTo = effectiveTo;
        this.recurrenceUnit = Objects.requireNonNull(
                recurrenceUnit,
                "recurrenceUnit"
        );
        this.dayOfUnit = requireDayOfUnit(dayOfUnit);
        this.recurrenceInterval = requireRecurrenceInterval(recurrenceInterval);
        this.paymentDateAdjustmentPolicy = paymentDateAdjustmentPolicy == null
                ? DEFAULT_PAYMENT_DATE_ADJUSTMENT_POLICY
                : paymentDateAdjustmentPolicy;
        this.paymentAmount = normalizeRequiredPaymentAmount(paymentAmount);
        this.recurringTransactionEndDate = recurringTransactionEndDate;
        this.finalPaymentAmount = normalizeOptionalFinalPaymentAmount(finalPaymentAmount);

        validateEffectiveDates();
        validateDayOfUnit();
        validateEndDate();
    }

    public static RecurringTransactionHistory create(
            RecurringTransaction recurringTransaction,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Short dayOfUnit,
            Short recurrenceInterval,
            RecurrenceUnit recurrenceUnit,
            PaymentDateAdjustmentPolicy paymentDateAdjustmentPolicy,
            BigDecimal paymentAmount,
            LocalDate recurringTransactionEndDate,
            BigDecimal finalPaymentAmount
    ) {
        return new RecurringTransactionHistory(
                recurringTransaction,
                effectiveFrom,
                effectiveTo,
                dayOfUnit,
                recurrenceInterval,
                recurrenceUnit,
                paymentDateAdjustmentPolicy,
                paymentAmount,
                recurringTransactionEndDate,
                finalPaymentAmount
        );
    }

    private static short requireDayOfUnit(Short value) {
        if (value == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.dayOfUnitRequired");
        }

        return value;
    }

    private static short requireRecurrenceInterval(Short value) {
        if (value == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.recurrenceIntervalRequired");
        }

        if (value <= 0) {
            throw new IllegalArgumentException("finance.recurringTransaction.recurrenceIntervalInvalid");
        }

        return value;
    }

    private static BigDecimal normalizeRequiredPaymentAmount(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.paymentAmountRequired");
        }

        BigDecimal normalized;

        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "finance.recurringTransaction.paymentAmountInvalid",
                    exception
            );
        }

        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("finance.recurringTransaction.paymentAmountNotZero");
        }

        return normalized;
    }

    private static BigDecimal normalizeOptionalFinalPaymentAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }

        BigDecimal normalized;

        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "finance.recurringTransaction.finalPaymentAmountInvalid",
                    exception
            );
        }

        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("finance.recurringTransaction.finalPaymentAmountNotZero");
        }

        return normalized;
    }

    @PrePersist
    void prePersist() {
        if (recurringTransactionHistoryCreatedAt == null) {
            recurringTransactionHistoryCreatedAt = OffsetDateTime.now();
        }
    }

    private void validateEffectiveDates() {
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("finance.recurringTransaction.effectiveDatesInvalid");
        }
    }

    private void validateDayOfUnit() {
        boolean valid = switch (recurrenceUnit) {
            case DAY -> dayOfUnit == 1;
            case WEEK -> dayOfUnit >= 1 && dayOfUnit <= 7;
            case MONTH -> dayOfUnit >= 1 && dayOfUnit <= 31;
            case YEAR -> dayOfUnit >= 1 && dayOfUnit <= 366;
        };

        if (!valid) {
            throw new IllegalArgumentException("finance.recurringTransaction.dayOfUnitInvalid");
        }
    }

    private void validateEndDate() {
        if (recurringTransactionEndDate != null
                && recurringTransactionEndDate.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("finance.recurringTransaction.endDateInvalid");
        }
    }

    public void closeAt(LocalDate effectiveTo) {
        Objects.requireNonNull(effectiveTo, "effectiveTo");

        if (!effectiveTo.isAfter(this.effectiveFrom)) {
            throw new IllegalArgumentException("finance.recurringTransaction.effectiveToInvalid");
        }

        this.effectiveTo = effectiveTo;
    }

    public UUID getRecurringTransactionHistoryId() {
        return recurringTransactionHistoryId;
    }

    public RecurringTransaction getRecurringTransaction() {
        return recurringTransaction;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public short getDayOfUnit() {
        return dayOfUnit;
    }

    public short getRecurrenceInterval() {
        return recurrenceInterval;
    }

    public RecurrenceUnit getRecurrenceUnit() {
        return recurrenceUnit;
    }

    public PaymentDateAdjustmentPolicy getPaymentDateAdjustmentPolicy() {
        return paymentDateAdjustmentPolicy;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public LocalDate getRecurringTransactionEndDate() {
        return recurringTransactionEndDate;
    }

    public BigDecimal getFinalPaymentAmount() {
        return finalPaymentAmount;
    }

    public OffsetDateTime getRecurringTransactionHistoryCreatedAt() {
        return recurringTransactionHistoryCreatedAt;
    }
}