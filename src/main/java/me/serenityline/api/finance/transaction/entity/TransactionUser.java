package me.serenityline.api.finance.transaction.entity;

import jakarta.persistence.*;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions_users")
public class TransactionUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_user_id", nullable = false)
    private UUID transactionUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false)
    private UserGroup userGroup;

    @Column(name = "transaction_user_linked_at", nullable = false)
    private OffsetDateTime transactionUserLinkedAt;

    protected TransactionUser() {
    }

    private TransactionUser(
            Transaction transaction,
            User user,
            UserGroup userGroup
    ) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.user = Objects.requireNonNull(user, "user");
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");

        validateUserGroupConsistency();
    }

    public static TransactionUser link(
            Transaction transaction,
            User user,
            UserGroup userGroup
    ) {
        return new TransactionUser(
                transaction,
                user,
                userGroup
        );
    }

    @PrePersist
    void prePersist() {
        if (transactionUserLinkedAt == null) {
            transactionUserLinkedAt = OffsetDateTime.now();
        }
    }

    private void validateUserGroupConsistency() {
        UUID expectedUserGroupId = userGroup.getUserGroupId();

        if (!transaction.getUserGroup().getUserGroupId().equals(expectedUserGroupId)
                || !user.getUserGroup().getUserGroupId().equals(expectedUserGroupId)) {
            throw new IllegalArgumentException("finance.transaction.userGroupMismatch");
        }
    }

    public UUID getTransactionUserId() {
        return transactionUserId;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public User getUser() {
        return user;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public OffsetDateTime getTransactionUserLinkedAt() {
        return transactionUserLinkedAt;
    }
}