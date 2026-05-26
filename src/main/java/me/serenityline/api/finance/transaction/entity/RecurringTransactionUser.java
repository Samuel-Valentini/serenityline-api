package me.serenityline.api.finance.transaction.entity;

import jakarta.persistence.*;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recurring_transactions_users")
public class RecurringTransactionUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recurring_transaction_user_id", nullable = false)
    private UUID recurringTransactionUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_transaction_id", nullable = false)
    private RecurringTransaction recurringTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false)
    private UserGroup userGroup;

    @Column(name = "recurring_transaction_user_linked_at", nullable = false)
    private OffsetDateTime recurringTransactionUserLinkedAt;

    protected RecurringTransactionUser() {
    }

    private RecurringTransactionUser(
            RecurringTransaction recurringTransaction,
            User user,
            UserGroup userGroup
    ) {
        this.recurringTransaction = Objects.requireNonNull(recurringTransaction, "recurringTransaction");
        this.user = Objects.requireNonNull(user, "user");
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");

        validateUserGroupConsistency();
    }

    public static RecurringTransactionUser link(
            RecurringTransaction recurringTransaction,
            User user,
            UserGroup userGroup
    ) {
        return new RecurringTransactionUser(
                recurringTransaction,
                user,
                userGroup
        );
    }

    @PrePersist
    void prePersist() {
        if (recurringTransactionUserLinkedAt == null) {
            recurringTransactionUserLinkedAt = OffsetDateTime.now();
        }
    }

    private void validateUserGroupConsistency() {
        UUID expectedUserGroupId = userGroup.getUserGroupId();

        if (!recurringTransaction.getUserGroup().getUserGroupId().equals(expectedUserGroupId)
                || !user.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.recurringTransaction.userGroupMismatch");
        }
    }

    public UUID getRecurringTransactionUserId() {
        return recurringTransactionUserId;
    }

    public RecurringTransaction getRecurringTransaction() {
        return recurringTransaction;
    }

    public User getUser() {
        return user;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public OffsetDateTime getRecurringTransactionUserLinkedAt() {
        return recurringTransactionUserLinkedAt;
    }
}