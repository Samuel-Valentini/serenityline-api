package me.serenityline.api.finance.simulation.dto;

import me.serenityline.api.finance.simulation.entity.SimulationGroup;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record SimulationGroupResponse(
        UUID simulationGroupId,
        String simulationGroupName,
        String simulationGroupDescription,
        OffsetDateTime simulationGroupCreatedAt,
        OffsetDateTime simulationGroupUpdatedAt,
        OffsetDateTime simulationGroupArchivedAt,
        List<UUID> accountIds
) {

    public static SimulationGroupResponse from(
            SimulationGroup simulationGroup,
            List<UUID> accountIds
    ) {
        return new SimulationGroupResponse(
                simulationGroup.getSimulationGroupId(),
                simulationGroup.getSimulationGroupName(),
                simulationGroup.getSimulationGroupDescription(),
                simulationGroup.getSimulationGroupCreatedAt(),
                simulationGroup.getSimulationGroupUpdatedAt(),
                simulationGroup.getSimulationGroupArchivedAt(),
                accountIds.stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList()
        );
    }
}