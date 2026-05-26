package me.serenityline.api.finance.simulation.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.simulation.dto.SimulationGroupCreateRequest;
import me.serenityline.api.finance.simulation.dto.SimulationGroupResponse;
import me.serenityline.api.finance.simulation.dto.SimulationGroupUpdateRequest;
import me.serenityline.api.finance.simulation.service.*;
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
    private final SimulationGroupUpdateService simulationGroupUpdateService;
    private final SimulationGroupLifecycleService simulationGroupLifecycleService;
    private final SimulationGroupAccountService simulationGroupAccountService;

    public SimulationGroupController(SimulationGroupCreationService simulationGroupCreationService, SimulationGroupQueryService simulationGroupQueryService, SimulationGroupUpdateService simulationGroupUpdateService, SimulationGroupLifecycleService simulationGroupLifecycleService, SimulationGroupAccountService simulationGroupAccountService) {
        this.simulationGroupCreationService = Objects.requireNonNull(
                simulationGroupCreationService,
                "simulationGroupCreationService"
        );
        this.simulationGroupQueryService = Objects.requireNonNull(
                simulationGroupQueryService,
                "simulationGroupQueryService"
        );
        this.simulationGroupUpdateService = Objects.requireNonNull(
                simulationGroupUpdateService,
                "simulationGroupUpdateService"
        );
        this.simulationGroupLifecycleService = Objects.requireNonNull(
                simulationGroupLifecycleService,
                "simulationGroupLifecycleService"
        );
        this.simulationGroupAccountService = Objects.requireNonNull(
                simulationGroupAccountService,
                "simulationGroupAccountService"
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

    @PatchMapping("/{simulationGroupId}")
    public ResponseEntity<SimulationGroupResponse> updateSimulationGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID simulationGroupId,
            @Valid @RequestBody SimulationGroupUpdateRequest request
    ) {
        SimulationGroupResponse response = simulationGroupUpdateService.updateSimulationGroup(
                authenticatedUser.userId(),
                simulationGroupId,
                request
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{simulationGroupId}/archive")
    public ResponseEntity<SimulationGroupResponse> archiveSimulationGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID simulationGroupId
    ) {
        SimulationGroupResponse response = simulationGroupLifecycleService.archiveSimulationGroup(
                authenticatedUser.userId(),
                simulationGroupId
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{simulationGroupId}/restore")
    public ResponseEntity<SimulationGroupResponse> restoreSimulationGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID simulationGroupId
    ) {
        SimulationGroupResponse response = simulationGroupLifecycleService.restoreSimulationGroup(
                authenticatedUser.userId(),
                simulationGroupId
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{simulationGroupId}/accounts/{accountId}")
    public ResponseEntity<SimulationGroupResponse> linkAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID simulationGroupId,
            @PathVariable UUID accountId
    ) {
        SimulationGroupResponse response = simulationGroupAccountService.linkAccount(
                authenticatedUser.userId(),
                simulationGroupId,
                accountId
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{simulationGroupId}/accounts/{accountId}")
    public ResponseEntity<SimulationGroupResponse> unlinkAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID simulationGroupId,
            @PathVariable UUID accountId
    ) {
        SimulationGroupResponse response = simulationGroupAccountService.unlinkAccount(
                authenticatedUser.userId(),
                simulationGroupId,
                accountId
        );

        return ResponseEntity.ok(response);
    }
}