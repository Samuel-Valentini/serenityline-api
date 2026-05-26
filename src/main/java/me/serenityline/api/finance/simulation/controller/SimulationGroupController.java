package me.serenityline.api.finance.simulation.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.simulation.dto.SimulationGroupCreateRequest;
import me.serenityline.api.finance.simulation.dto.SimulationGroupResponse;
import me.serenityline.api.finance.simulation.service.SimulationGroupCreationService;
import me.serenityline.api.finance.simulation.service.SimulationGroupQueryService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/simulation-groups")
public class SimulationGroupController {

    private final SimulationGroupCreationService simulationGroupCreationService;
    private final SimulationGroupQueryService simulationGroupQueryService;

    public SimulationGroupController(SimulationGroupCreationService simulationGroupCreationService, SimulationGroupQueryService simulationGroupQueryService) {
        this.simulationGroupCreationService = Objects.requireNonNull(
                simulationGroupCreationService,
                "simulationGroupCreationService"
        );
        this.simulationGroupQueryService = Objects.requireNonNull(
                simulationGroupQueryService,
                "simulationGroupQueryService"
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

    @GetMapping
    public ResponseEntity<List<SimulationGroupResponse>> findSimulationGroups(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) String status
    ) {
        List<SimulationGroupResponse> response = simulationGroupQueryService.findSimulationGroups(
                authenticatedUser.userId(),
                status
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{simulationGroupId}")
    public ResponseEntity<SimulationGroupResponse> findSimulationGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID simulationGroupId
    ) {
        SimulationGroupResponse response = simulationGroupQueryService.findSimulationGroup(
                authenticatedUser.userId(),
                simulationGroupId
        );

        return ResponseEntity.ok(response);
    }
}