package me.serenityline.api.finance.transaction.entity;

import jakarta.persistence.*;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.user.entity.UserGroup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    private static final boolean DEFAULT_AFFECTS_ACCOUNT_BALANCE = true;
    private static final boolean DEFAULT_AFFECTS_LIQUIDITY = true;
    private static final boolean DEFAULT_IS_CONFIRMED = false;
    private static final boolean DEFAULT_REMINDER_ENABLED = true;
    private static final short DEFAULT_REMINDER_DAYS_BEFORE = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "transaction_description", nullable = false, length = 500)
    private String transactionDescription;

    @Column(name = "transaction_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "transaction_affects_account_balance", nullable = false)
    private boolean transactionAffectsAccountBalance;

    @Column(name = "transaction_affects_liquidity", nullable = false)
    private boolean transactionAffectsLiquidity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "transaction_charge_date", nullable = false)
    private LocalDate transactionChargeDate;

    @Column(name = "transaction_is_confirmed", nullable = false)
    private boolean transactionIsConfirmed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id")
    private CreditCard creditCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id")
    private Bucket bucket;

    @Column(name = "transaction_is_simulated", nullable = false)
    private boolean transactionIsSimulated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_group_id")
    private SimulationGroup simulationGroup;

    @Column(name = "transaction_is_user_entered", nullable = false)
    private boolean transactionIsUserEntered;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_transaction_id")
    private RecurringTransaction recurringTransaction;

    @Column(name = "transaction_reminder_enabled", nullable = false)
    private boolean transactionReminderEnabled;

    @Column(name = "transaction_reminder_days_before", nullable = false)
    private short transactionReminderDaysBefore;

    @Column(name = "transaction_created_at", nullable = false)
    private OffsetDateTime transactionCreatedAt;

    @Column(name = "transaction_updated_at", nullable = false)
    private OffsetDateTime transactionUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false)
    private UserGroup userGroup;

    protected Transaction() {
    }

    private Transaction(
            String transactionDescription,
            BigDecimal transactionAmount,
            Boolean transactionAffectsAccountBalance,
            Boolean transactionAffectsLiquidity,
            Category category,
            LocalDate transactionChargeDate,
            Boolean transactionIsConfirmed,
            Account account,
            CreditCard creditCard,
            Bucket bucket,
            boolean transactionIsSimulated,
            SimulationGroup simulationGroup,
            boolean transactionIsUserEntered,
            RecurringTransaction recurringTransaction,
            Boolean transactionReminderEnabled,
            Short transactionReminderDaysBefore,
            UserGroup userGroup
    ) {
        this.transactionDescription = cleanDescription(transactionDescription);
        this.transactionAmount = normalizeAmount(transactionAmount);
        this.transactionAffectsAccountBalance = transactionAffectsAccountBalance == null
                ? DEFAULT_AFFECTS_ACCOUNT_BALANCE
                : transactionAffectsAccountBalance;
        this.transactionAffectsLiquidity = transactionAffectsLiquidity == null
                ? DEFAULT_AFFECTS_LIQUIDITY
                : transactionAffectsLiquidity;
        this.category = Objects.requireNonNull(category, "category");
        this.transactionChargeDate = Objects.requireNonNull(transactionChargeDate, "transactionChargeDate");
        this.transactionIsConfirmed = transactionIsConfirmed == null
                ? DEFAULT_IS_CONFIRMED
                : transactionIsConfirmed;
        this.account = Objects.requireNonNull(account, "account");
        this.creditCard = creditCard;
        this.bucket = bucket;
        this.transactionIsSimulated = transactionIsSimulated;
        this.simulationGroup = simulationGroup;
        this.transactionIsUserEntered = transactionIsUserEntered;
        this.recurringTransaction = recurringTransaction;
        this.transactionReminderEnabled = transactionReminderEnabled == null
                ? DEFAULT_REMINDER_ENABLED
                : transactionReminderEnabled;
        this.transactionReminderDaysBefore = transactionReminderDaysBefore == null
                ? DEFAULT_REMINDER_DAYS_BEFORE
                : transactionReminderDaysBefore;
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");

        validateAffectsSomething();
        validateSimulationConsistency();
        validateRecurringConsistency();
        validateReminderDaysBefore();
        validateEntitiesBelongToUserGroup();
        validateCreditCardBelongsToAccount();
    }

    public static Transaction createUserEntered(
            String transactionDescription,
            BigDecimal transactionAmount,
            Boolean transactionAffectsAccountBalance,
            Boolean transactionAffectsLiquidity,
            Category category,
            LocalDate transactionChargeDate,
            Boolean transactionIsConfirmed,
            Account account,
            CreditCard creditCard,
            Bucket bucket,
            boolean transactionIsSimulated,
            SimulationGroup simulationGroup,
            Boolean transactionReminderEnabled,
            Short transactionReminderDaysBefore,
            UserGroup userGroup
    ) {
        return new Transaction(
                transactionDescription,
                transactionAmount,
                transactionAffectsAccountBalance,
                transactionAffectsLiquidity,
                category,
                transactionChargeDate,
                transactionIsConfirmed,
                account,
                creditCard,
                bucket,
                transactionIsSimulated,
                simulationGroup,
                true,
                null,
                transactionReminderEnabled,
                transactionReminderDaysBefore,
                userGroup
        );
    }

    public static Transaction createConfirmedRecurringOccurrence(
            String transactionDescription,
            BigDecimal transactionAmount,
            Boolean transactionAffectsAccountBalance,
            Boolean transactionAffectsLiquidity,
            Category category,
            LocalDate transactionChargeDate,
            Account account,
            CreditCard creditCard,
            Bucket bucket,
            boolean transactionIsSimulated,
            SimulationGroup simulationGroup,
            RecurringTransaction recurringTransaction,
            Boolean transactionReminderEnabled,
            Short transactionReminderDaysBefore,
            UserGroup userGroup
    ) {
        return new Transaction(
                transactionDescription,
                transactionAmount,
                transactionAffectsAccountBalance,
                transactionAffectsLiquidity,
                category,
                transactionChargeDate,
                true,
                account,
                creditCard,
                bucket,
                transactionIsSimulated,
                simulationGroup,
                false,
                Objects.requireNonNull(recurringTransaction, "recurringTransaction"),
                transactionReminderEnabled,
                transactionReminderDaysBefore,
                userGroup
        );
    }

    private static String cleanDescription(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("finance.transaction.descriptionRequired");
        }

        String cleaned = value.trim();

        if (cleaned.length() > 500) {
            throw new IllegalArgumentException("finance.transaction.descriptionTooLong");
        }

        return cleaned;
    }

    private static BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("finance.transaction.amountRequired");
        }

        BigDecimal normalized;

        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("finance.transaction.amountInvalid", exception);
        }

        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("finance.transaction.amountNotZero");
        }

        return normalized;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();

        if (transactionCreatedAt == null) {
            transactionCreatedAt = now;
        }

        if (transactionUpdatedAt == null) {
            transactionUpdatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        touch();
    }

    public void touch() {
        transactionUpdatedAt = OffsetDateTime.now();
    }

    private void validateAffectsSomething() {
        if (!transactionAffectsAccountBalance && !transactionAffectsLiquidity) {
            throw new IllegalArgumentException("finance.transaction.affectsSomethingRequired");
        }
    }

    private void validateSimulationConsistency() {
        if (transactionIsSimulated && simulationGroup == null) {
            throw new IllegalArgumentException("finance.transaction.simulationGroupRequired");
        }

        if (!transactionIsSimulated && simulationGroup != null) {
            throw new IllegalArgumentException("finance.transaction.simulationGroupNotAllowed");
        }
    }

    private void validateRecurringConsistency() {
        if (transactionIsUserEntered && recurringTransaction != null) {
            throw new IllegalArgumentException("finance.transaction.recurringTransactionNotAllowed");
        }

        if (!transactionIsUserEntered && recurringTransaction == null) {
            throw new IllegalArgumentException("finance.transaction.recurringTransactionRequired");
        }

        if (!transactionIsUserEntered && !transactionIsConfirmed) {
            throw new IllegalArgumentException("finance.transaction.recurringTransactionMustBeConfirmed");
        }
    }

    private void validateReminderDaysBefore() {
        if (transactionReminderDaysBefore < 0 || transactionReminderDaysBefore > 366) {
            throw new IllegalArgumentException("finance.transaction.reminderDaysInvalid");
        }
    }

    private void validateEntitiesBelongToUserGroup() {
        UUID expectedUserGroupId = userGroup.getUserGroupId();

        if (!category.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.transaction.categoryGroupMismatch");
        }

        if (!account.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.transaction.accountGroupMismatch");
        }

        if (creditCard != null
                && !creditCard.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.transaction.creditCardGroupMismatch");
        }

        if (bucket != null
                && !bucket.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.transaction.bucketGroupMismatch");
        }

        if (simulationGroup != null
                && !simulationGroup.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.transaction.simulationGroupMismatch");
        }

        if (recurringTransaction != null
                && !recurringTransaction.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.transaction.recurringTransactionGroupMismatch");
        }
    }

    private void validateCreditCardBelongsToAccount() {
        if (creditCard == null) {
            return;
        }

        UUID creditCardAccountId = creditCard.getAccount().getAccountId();
        UUID accountId = account.getAccountId();

        if (!creditCardAccountId.equals(accountId)) {
            throw new IllegalArgumentException("finance.transaction.creditCardAccountMismatch");
        }
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getTransactionDescription() {
        return transactionDescription;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public boolean isTransactionAffectsAccountBalance() {
        return transactionAffectsAccountBalance;
    }

    public boolean isTransactionAffectsLiquidity() {
        return transactionAffectsLiquidity;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDate getTransactionChargeDate() {
        return transactionChargeDate;
    }

    public boolean isTransactionIsConfirmed() {
        return transactionIsConfirmed;
    }

    public Account getAccount() {
        return account;
    }

    public CreditCard getCreditCard() {
        return creditCard;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public boolean isTransactionIsSimulated() {
        return transactionIsSimulated;
    }

    public SimulationGroup getSimulationGroup() {
        return simulationGroup;
    }

    public boolean isTransactionIsUserEntered() {
        return transactionIsUserEntered;
    }

    public RecurringTransaction getRecurringTransaction() {
        return recurringTransaction;
    }

    public boolean isTransactionReminderEnabled() {
        return transactionReminderEnabled;
    }

    public short getTransactionReminderDaysBefore() {
        return transactionReminderDaysBefore;
    }

    public OffsetDateTime getTransactionCreatedAt() {
        return transactionCreatedAt;
    }

    public OffsetDateTime getTransactionUpdatedAt() {
        return transactionUpdatedAt;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }
}