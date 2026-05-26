package me.serenityline.api.finance.simulation.entity;

import jakarta.persistence.*;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "simulation_groups")
public class SimulationGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "simulation_group_id", nullable = false)
    private UUID simulationGroupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false)
    private UserGroup userGroup;

    @Column(name = "simulation_group_name", nullable = false, length = 255)
    private String simulationGroupName;

    @Column(name = "simulation_group_description", length = 2000)
    private String simulationGroupDescription;

    @Column(name = "simulation_group_created_at", nullable = false)
    private OffsetDateTime simulationGroupCreatedAt;

    @Column(name = "simulation_group_updated_at", nullable = false)
    private OffsetDateTime simulationGroupUpdatedAt;

    @Column(name = "simulation_group_archived_at")
    private OffsetDateTime simulationGroupArchivedAt;

    protected SimulationGroup() {
    }

    private SimulationGroup(
            UserGroup userGroup,
            String simulationGroupName,
            String simulationGroupDescription
    ) {
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");
        this.simulationGroupName = Objects.requireNonNull(simulationGroupName, "simulationGroupName");
        this.simulationGroupDescription = simulationGroupDescription;
    }

    public static SimulationGroup create(
            UserGroup userGroup,
            String simulationGroupName,
            String simulationGroupDescription
    ) {
        return new SimulationGroup(
                userGroup,
                simulationGroupName,
                simulationGroupDescription
        );
    }

    @PrePersist
    void prePersist() {

        OffsetDateTime now = OffsetDateTime.now();

        if (simulationGroupCreatedAt == null) {
            simulationGroupCreatedAt = now;
        }

        if (simulationGroupUpdatedAt == null) {
            simulationGroupUpdatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        simulationGroupUpdatedAt = OffsetDateTime.now();
    }

    public void update(String name, String description) {
        this.simulationGroupName = Objects.requireNonNull(name, "name");
        this.simulationGroupDescription = description;
        touch();
    }

    public void archive() {
        if (simulationGroupArchivedAt != null) return;
        this.simulationGroupArchivedAt = OffsetDateTime.now();
        touch();
    }

    public void restore() {
        if (simulationGroupArchivedAt == null) {
            return;
        }
        this.simulationGroupArchivedAt = null;
        touch();
    }

    private void touch() {
        this.simulationGroupUpdatedAt = OffsetDateTime.now();
    }

    public UUID getSimulationGroupId() {
        return simulationGroupId;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public String getSimulationGroupName() {
        return simulationGroupName;
    }

    public String getSimulationGroupDescription() {
        return simulationGroupDescription;
    }

    public OffsetDateTime getSimulationGroupCreatedAt() {
        return simulationGroupCreatedAt;
    }

    public OffsetDateTime getSimulationGroupUpdatedAt() {
        return simulationGroupUpdatedAt;
    }

    public OffsetDateTime getSimulationGroupArchivedAt() {
        return simulationGroupArchivedAt;
    }
}