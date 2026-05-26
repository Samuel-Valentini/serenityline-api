package me.serenityline.api.finance.simulation.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.simulation.dto.SimulationGroupResponse;
import me.serenityline.api.finance.simulation.dto.SimulationGroupStatusFilter;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.simulation.repository.SimulationGroupAccountRepository;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SimulationGroupQueryService {

    private final UserRepository userRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final SimulationGroupAccountRepository simulationGroupAccountRepository;

    public SimulationGroupQueryService(
            UserRepository userRepository,
            SimulationGroupRepository simulationGroupRepository,
            SimulationGroupAccountRepository simulationGroupAccountRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.simulationGroupAccountRepository = Objects.requireNonNull(simulationGroupAccountRepository, "simulationGroupAccountRepository");
    }

    @Transactional(readOnly = true)
    public List<SimulationGroupResponse> findSimulationGroups(
            UUID currentUserId,
            String status
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        SimulationGroupStatusFilter statusFilter = SimulationGroupStatusFilter.from(status);

        List<SimulationGroup> simulationGroups = findVisibleSimulationGroups(
                currentUser,
                userGroupId,
                statusFilter
        );

        return simulationGroups.stream()
                .map(simulationGroup -> SimulationGroupResponse.from(
                        simulationGroup,
                        findAccountIdsForResponse(currentUser, userGroupId, simulationGroup)
                ))
                .toList();
    }

    private User findCurrentUser(UUID currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    }

    private List<SimulationGroup> findVisibleSimulationGroups(
            User currentUser,
            UUID userGroupId,
            SimulationGroupStatusFilter statusFilter
    ) {
        if (canSeeAllSimulationGroups(currentUser)) {
            return simulationGroupRepository.findAllByUserGroupIdAndStatus(
                    userGroupId,
                    statusFilter.includeActive(),
                    statusFilter.includeArchived()
            );
        }

        return simulationGroupRepository.findAllVisibleToLinkedUserByStatus(
                userGroupId,
                currentUser.getUserId(),
                statusFilter.includeActive(),
                statusFilter.includeArchived()
        );
    }

    private List<UUID> findAccountIdsForResponse(
            User currentUser,
            UUID userGroupId,
            SimulationGroup simulationGroup
    ) {
        if (canSeeAllSimulationGroups(currentUser)) {
            return simulationGroupAccountRepository.findAccountIds(
                    simulationGroup.getSimulationGroupId(),
                    userGroupId
            );
        }

        return simulationGroupAccountRepository.findVisibleAccountIds(
                simulationGroup.getSimulationGroupId(),
                userGroupId,
                currentUser.getUserId()
        );
    }

    private boolean canSeeAllSimulationGroups(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR
                || user.getUserRole() == UserRole.VIEWER_COLLABORATOR;
    }
}