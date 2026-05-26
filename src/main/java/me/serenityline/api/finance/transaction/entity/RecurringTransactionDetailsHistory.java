package me.serenityline.api.finance.transaction.entity;

import jakarta.persistence.*;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.user.entity.UserGroup;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recurring_transaction_details_history")
public class RecurringTransactionDetailsHistory {

    private static final boolean DEFAULT_AFFECTS_ACCOUNT_BALANCE = true;
    private static final boolean DEFAULT_AFFECTS_LIQUIDITY = true;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recurring_transaction_details_history_id", nullable = false)
    private UUID recurringTransactionDetailsHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_transaction_id", nullable = false)
    private RecurringTransaction recurringTransaction;

    @Column(name = "recurring_transaction_description", nullable = false, length = 500)
    private String recurringTransactionDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_priority_id", nullable = false)
    private FinancialPriority financialPriority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "linked_account_id", nullable = false)
    private Account linkedAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_credit_card_id")
    private CreditCard linkedCreditCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_bucket_id")
    private Bucket linkedBucket;

    @Column(name = "recurring_transaction_affects_account_balance", nullable = false)
    private boolean recurringTransactionAffectsAccountBalance;

    @Column(name = "recurring_transaction_affects_liquidity", nullable = false)
    private boolean recurringTransactionAffectsLiquidity;

    @Column(name = "recurring_transaction_details_effective_from", nullable = false)
    private LocalDate recurringTransactionDetailsEffectiveFrom;

    @Column(name = "recurring_transaction_details_history_created_at", nullable = false)
    private OffsetDateTime recurringTransactionDetailsHistoryCreatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false)
    private UserGroup userGroup;

    protected RecurringTransactionDetailsHistory() {
    }

    private RecurringTransactionDetailsHistory(
            RecurringTransaction recurringTransaction,
            String recurringTransactionDescription,
            Category category,
            FinancialPriority financialPriority,
            Account linkedAccount,
            CreditCard linkedCreditCard,
            Bucket linkedBucket,
            Boolean recurringTransactionAffectsAccountBalance,
            Boolean recurringTransactionAffectsLiquidity,
            LocalDate recurringTransactionDetailsEffectiveFrom,
            UserGroup userGroup
    ) {
        this.recurringTransaction = Objects.requireNonNull(
                recurringTransaction,
                "recurringTransaction"
        );
        this.recurringTransactionDescription = cleanDescription(recurringTransactionDescription);
        this.category = Objects.requireNonNull(category, "category");
        this.financialPriority = Objects.requireNonNull(
                financialPriority,
                "financialPriority"
        );
        this.linkedAccount = Objects.requireNonNull(
                linkedAccount,
                "linkedAccount"
        );
        this.linkedCreditCard = linkedCreditCard;
        this.linkedBucket = linkedBucket;
        this.recurringTransactionAffectsAccountBalance = recurringTransactionAffectsAccountBalance == null
                ? DEFAULT_AFFECTS_ACCOUNT_BALANCE
                : recurringTransactionAffectsAccountBalance;
        this.recurringTransactionAffectsLiquidity = recurringTransactionAffectsLiquidity == null
                ? DEFAULT_AFFECTS_LIQUIDITY
                : recurringTransactionAffectsLiquidity;
        this.recurringTransactionDetailsEffectiveFrom = Objects.requireNonNull(
                recurringTransactionDetailsEffectiveFrom,
                "recurringTransactionDetailsEffectiveFrom"
        );
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");

        validateAffectsSomething();
        validateEntitiesBelongToUserGroup();
        validateCreditCardBelongsToLinkedAccount();
    }

    public static RecurringTransactionDetailsHistory create(
            RecurringTransaction recurringTransaction,
            String recurringTransactionDescription,
            Category category,
            FinancialPriority financialPriority,
            Account linkedAccount,
            CreditCard linkedCreditCard,
            Bucket linkedBucket,
            Boolean recurringTransactionAffectsAccountBalance,
            Boolean recurringTransactionAffectsLiquidity,
            LocalDate recurringTransactionDetailsEffectiveFrom,
            UserGroup userGroup
    ) {
        return new RecurringTransactionDetailsHistory(
                recurringTransaction,
                recurringTransactionDescription,
                category,
                financialPriority,
                linkedAccount,
                linkedCreditCard,
                linkedBucket,
                recurringTransactionAffectsAccountBalance,
                recurringTransactionAffectsLiquidity,
                recurringTransactionDetailsEffectiveFrom,
                userGroup
        );
    }

    private static String cleanDescription(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("finance.recurringTransaction.descriptionRequired");
        }

        String cleaned = value.trim();

        if (cleaned.length() > 500) {
            throw new IllegalArgumentException("finance.recurringTransaction.descriptionTooLong");
        }

        return cleaned;
    }

    @PrePersist
    void prePersist() {
        if (recurringTransactionDetailsHistoryCreatedAt == null) {
            recurringTransactionDetailsHistoryCreatedAt = OffsetDateTime.now();
        }
    }

    private void validateAffectsSomething() {
        if (!recurringTransactionAffectsAccountBalance
                && !recurringTransactionAffectsLiquidity) {
            throw new IllegalArgumentException(
                    "finance.recurringTransaction.affectsSomethingRequired"
            );
        }
    }

    private void validateEntitiesBelongToUserGroup() {
        UUID expectedUserGroupId = userGroup.getUserGroupId();

        if (!recurringTransaction.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.recurringTransaction.userGroupMismatch");
        }

        if (!category.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.recurringTransaction.categoryGroupMismatch");
        }

        if (!linkedAccount.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.recurringTransaction.accountGroupMismatch");
        }

        if (linkedCreditCard != null
                && !linkedCreditCard.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.recurringTransaction.creditCardGroupMismatch");
        }

        if (linkedBucket != null
                && !linkedBucket.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.recurringTransaction.bucketGroupMismatch");
        }
    }

    private void validateCreditCardBelongsToLinkedAccount() {
        if (linkedCreditCard == null) {
            return;
        }

        UUID creditCardAccountId = linkedCreditCard.getAccount().getAccountId();
        UUID linkedAccountId = linkedAccount.getAccountId();

        if (!creditCardAccountId.equals(linkedAccountId)) {
            throw new IllegalArgumentException(
                    "finance.recurringTransaction.creditCardAccountMismatch"
            );
        }
    }

    public UUID getRecurringTransactionDetailsHistoryId() {
        return recurringTransactionDetailsHistoryId;
    }

    public RecurringTransaction getRecurringTransaction() {
        return recurringTransaction;
    }

    public String getRecurringTransactionDescription() {
        return recurringTransactionDescription;
    }

    public Category getCategory() {
        return category;
    }

    public FinancialPriority getFinancialPriority() {
        return financialPriority;
    }

    public Account getLinkedAccount() {
        return linkedAccount;
    }

    public CreditCard getLinkedCreditCard() {
        return linkedCreditCard;
    }

    public Bucket getLinkedBucket() {
        return linkedBucket;
    }

    public boolean isRecurringTransactionAffectsAccountBalance() {
        return recurringTransactionAffectsAccountBalance;
    }

    public boolean isRecurringTransactionAffectsLiquidity() {
        return recurringTransactionAffectsLiquidity;
    }

    public LocalDate getRecurringTransactionDetailsEffectiveFrom() {
        return recurringTransactionDetailsEffectiveFrom;
    }

    public OffsetDateTime getRecurringTransactionDetailsHistoryCreatedAt() {
        return recurringTransactionDetailsHistoryCreatedAt;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }
}