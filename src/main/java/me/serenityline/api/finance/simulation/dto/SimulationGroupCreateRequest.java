package me.serenityline.api.finance.simulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record SimulationGroupCreateRequest(

        @NotBlank(message = "finance.simulationGroup.nameRequired")
        @Size(max = 255, message = "finance.simulationGroup.nameTooLong")
        String simulationGroupName,

        @Size(max = 2000, message = "finance.simulationGroup.descriptionTooLong")
        String simulationGroupDescription,

        Set<UUID> accountIds
) {
}