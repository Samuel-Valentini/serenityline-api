package me.serenityline.api.finance.account.entity;

import jakarta.persistence.*;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts_users")
public class AccountUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_user_id", nullable = false)
    private UUID accountUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false, updatable = false)
    private UserGroup userGroup;

    @Column(name = "account_access_granted_at", nullable = false, updatable = false)
    private OffsetDateTime accountAccessGrantedAt;

    protected AccountUser() {
    }

    private AccountUser(Account account, User user, UserGroup userGroup) {
        this.account = account;
        this.user = user;
        this.userGroup = userGroup;
    }

    public static AccountUser grant(Account account, User user) {
        if (!account.getUserGroupId().equals(user.getUserGroup().getUserGroupId())) {
            throw new IllegalArgumentException("finance.account.userGroupMismatch");
        }

        return new AccountUser(account, user, user.getUserGroup());
    }

    @PrePersist
    void onCreate() {
        this.accountAccessGrantedAt = OffsetDateTime.now();
    }

    public UUID getAccountUserId() {
        return accountUserId;
    }

    public Account getAccount() {
        return account;
    }

    public UUID getAccountId() {
        return account.getAccountId();
    }

    public User getUser() {
        return user;
    }

    public UUID getUserId() {
        return user.getUserId();
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public UUID getUserGroupId() {
        return userGroup.getUserGroupId();
    }

    public OffsetDateTime getAccountAccessGrantedAt() {
        return accountAccessGrantedAt;
    }
}