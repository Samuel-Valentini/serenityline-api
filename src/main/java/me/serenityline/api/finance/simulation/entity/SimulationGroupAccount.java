package me.serenityline.api.finance.simulation.entity;

import jakarta.persistence.*;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "simulation_groups_accounts")
public class SimulationGroupAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "simulation_group_account_id", nullable = false)
    private UUID simulationGroupAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_group_id", nullable = false)
    private SimulationGroup simulationGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false)
    private UserGroup userGroup;

    @Column(name = "simulation_group_account_created_at", nullable = false)
    private OffsetDateTime simulationGroupAccountCreatedAt;

    protected SimulationGroupAccount() {
    }

    private SimulationGroupAccount(
            SimulationGroup simulationGroup,
            Account account,
            UserGroup userGroup
    ) {
        this.simulationGroup = Objects.requireNonNull(simulationGroup, "simulationGroup");
        this.account = Objects.requireNonNull(account, "account");
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");

        UUID simulationUserGroupId = simulationGroup.getUserGroup().getUserGroupId();
        UUID accountUserGroupId = account.getUserGroup().getUserGroupId();
        UUID userGroupId = userGroup.getUserGroupId();

        if (!simulationUserGroupId.equals(userGroupId)
                || !accountUserGroupId.equals(userGroupId)) {
            throw new IllegalArgumentException("finance.simulationGroup.accountGroupMismatch");
        }
    }

    public static SimulationGroupAccount link(
            SimulationGroup simulationGroup,
            Account account,
            UserGroup userGroup
    ) {
        return new SimulationGroupAccount(
                simulationGroup,
                account,
                userGroup
        );
    }

    @PrePersist
    void prePersist() {
        if (simulationGroupAccountCreatedAt == null) {
            simulationGroupAccountCreatedAt = OffsetDateTime.now();
        }
    }

    public UUID getSimulationGroupAccountId() {
        return simulationGroupAccountId;
    }

    public SimulationGroup getSimulationGroup() {
        return simulationGroup;
    }

    public Account getAccount() {
        return account;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public OffsetDateTime getSimulationGroupAccountCreatedAt() {
        return simulationGroupAccountCreatedAt;
    }
}