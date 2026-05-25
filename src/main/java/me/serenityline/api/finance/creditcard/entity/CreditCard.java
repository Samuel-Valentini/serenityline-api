package me.serenityline.api.finance.creditcard.entity;

import jakarta.persistence.*;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "credit_cards")
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "credit_card_id", nullable = false)
    private UUID creditCardId;

    @Column(name = "credit_card_name", nullable = false, length = 255)
    private String creditCardName;

    @Column(name = "credit_card_description", length = 2000)
    private String creditCardDescription;

    @Column(name = "credit_card_charge_day", nullable = false)
    private Short creditCardChargeDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false, updatable = false)
    private UserGroup userGroup;

    @Column(name = "credit_card_created_at", nullable = false, updatable = false)
    private OffsetDateTime creditCardCreatedAt;

    @Column(name = "credit_card_updated_at", nullable = false)
    private OffsetDateTime creditCardUpdatedAt;

    protected CreditCard() {
    }

    private CreditCard(
            String creditCardName,
            String creditCardDescription,
            short creditCardChargeDay,
            Account account,
            UserGroup userGroup
    ) {
        this.creditCardName = Objects.requireNonNull(creditCardName, "creditCardName");
        this.creditCardDescription = creditCardDescription;
        this.creditCardChargeDay = validateChargeDay(creditCardChargeDay);
        this.account = Objects.requireNonNull(account, "account");
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");
    }

    public static CreditCard create(
            String creditCardName,
            String creditCardDescription,
            short creditCardChargeDay,
            Account account,
            UserGroup userGroup
    ) {
        if (!account.getUserGroupId().equals(userGroup.getUserGroupId())) {
            throw new IllegalArgumentException("finance.creditCard.userGroupMismatch");
        }

        return new CreditCard(
                creditCardName,
                creditCardDescription,
                creditCardChargeDay,
                account,
                userGroup
        );
    }

    private static short validateChargeDay(short creditCardChargeDay) {
        if (creditCardChargeDay < 1 || creditCardChargeDay > 31) {
            throw new IllegalArgumentException("finance.creditCard.chargeDay.invalid");
        }

        return creditCardChargeDay;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.creditCardCreatedAt = now;
        this.creditCardUpdatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.creditCardUpdatedAt = OffsetDateTime.now();
    }

    public UUID getCreditCardId() {
        return creditCardId;
    }

    public String getCreditCardName() {
        return creditCardName;
    }

    public String getCreditCardDescription() {
        return creditCardDescription;
    }

    public Short getCreditCardChargeDay() {
        return creditCardChargeDay;
    }

    public Account getAccount() {
        return account;
    }

    public UUID getAccountId() {
        return account.getAccountId();
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public UUID getUserGroupId() {
        return userGroup.getUserGroupId();
    }

    public OffsetDateTime getCreditCardCreatedAt() {
        return creditCardCreatedAt;
    }

    public OffsetDateTime getCreditCardUpdatedAt() {
        return creditCardUpdatedAt;
    }
}