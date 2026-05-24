package me.serenityline.api.finance.account.entity;

import jakarta.persistence.*;
import me.serenityline.api.user.entity.UserGroup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    @Column(name = "account_description", length = 1000)
    private String accountDescription;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "issuing_institution", length = 255)
    private String issuingInstitution;

    @Column(name = "account_created_at", nullable = false, updatable = false)
    private OffsetDateTime accountCreatedAt;

    @Column(name = "account_updated_at", nullable = false)
    private OffsetDateTime accountUpdatedAt;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "opening_balance_date", nullable = false)
    private LocalDate openingBalanceDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false, updatable = false)
    private UserGroup userGroup;

    protected Account() {
    }

    private Account(
            String accountName,
            String accountDescription,
            String currency,
            String issuingInstitution,
            BigDecimal openingBalance,
            LocalDate openingBalanceDate,
            UserGroup userGroup
    ) {
        this.accountName = accountName;
        this.accountDescription = accountDescription;
        this.currency = currency;
        this.issuingInstitution = issuingInstitution;
        this.openingBalance = openingBalance;
        this.openingBalanceDate = openingBalanceDate;
        this.userGroup = userGroup;
    }

    public static Account create(
            String accountName,
            String accountDescription,
            String currency,
            String issuingInstitution,
            BigDecimal openingBalance,
            LocalDate openingBalanceDate,
            UserGroup userGroup
    ) {
        return new Account(
                accountName,
                accountDescription,
                currency,
                issuingInstitution,
                openingBalance,
                openingBalanceDate,
                userGroup
        );
    }

    public void update(
            String accountName,
            String accountDescription,
            String issuingInstitution,
            BigDecimal openingBalance,
            LocalDate openingBalanceDate
    ) {
        this.accountName = Objects.requireNonNull(accountName, "accountName");
        this.accountDescription = accountDescription;
        this.issuingInstitution = issuingInstitution;
        this.openingBalance = Objects.requireNonNull(openingBalance, "openingBalance");
        this.openingBalanceDate = Objects.requireNonNull(openingBalanceDate, "openingBalanceDate");
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.accountCreatedAt = now;
        this.accountUpdatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.accountUpdatedAt = OffsetDateTime.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountDescription() {
        return accountDescription;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIssuingInstitution() {
        return issuingInstitution;
    }

    public OffsetDateTime getAccountCreatedAt() {
        return accountCreatedAt;
    }

    public OffsetDateTime getAccountUpdatedAt() {
        return accountUpdatedAt;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public LocalDate getOpeningBalanceDate() {
        return openingBalanceDate;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public UUID getUserGroupId() {
        return userGroup.getUserGroupId();
    }
}