package me.serenityline.api.finance.simulation.dto;

import jakarta.validation.constraints.Size;

public record SimulationGroupUpdateRequest(

        @Size(max = 255, message = "finance.simulationGroup.nameTooLong")
        String simulationGroupName,

        @Size(max = 2000, message = "finance.simulationGroup.descriptionTooLong")
        String simulationGroupDescription
) {
}