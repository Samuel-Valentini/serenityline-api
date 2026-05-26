package me.serenityline.api.finance.simulation.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.simulation.dto.SimulationGroupCreateRequest;
import me.serenityline.api.finance.simulation.dto.SimulationGroupResponse;
import me.serenityline.api.finance.simulation.service.SimulationGroupCreationService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/finance/simulation-groups")
public class SimulationGroupController {

    private final SimulationGroupCreationService simulationGroupCreationService;

    public SimulationGroupController(SimulationGroupCreationService simulationGroupCreationService) {
        this.simulationGroupCreationService = Objects.requireNonNull(
                simulationGroupCreationService,
                "simulationGroupCreationService"
        );
    }

    @PostMapping
    public ResponseEntity<SimulationGroupResponse> createSimulationGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody SimulationGroupCreateRequest request
    ) {
        SimulationGroupResponse response = simulationGroupCreationService.createSimulationGroup(
                authenticatedUser.userId(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}