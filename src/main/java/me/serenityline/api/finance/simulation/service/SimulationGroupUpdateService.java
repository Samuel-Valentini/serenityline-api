package me.serenityline.api.finance.simulation.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.simulation.dto.SimulationGroupResponse;
import me.serenityline.api.finance.simulation.dto.SimulationGroupUpdateRequest;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.simulation.repository.SimulationGroupAccountRepository;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;

import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SimulationGroupUpdateService {

    private final UserRepository userRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final SimulationGroupAccountRepository simulationGroupAccountRepository;

    public SimulationGroupUpdateService(
            UserRepository userRepository,
            SimulationGroupRepository simulationGroupRepository,
            SimulationGroupAccountRepository simulationGroupAccountRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.simulationGroupAccountRepository = Objects.requireNonNull(simulationGroupAccountRepository, "simulationGroupAccountRepository");
    }

    @Transactional
    public SimulationGroupResponse updateSimulationGroup(
            UUID currentUserId,
            UUID simulationGroupId,
            SimulationGroupUpdateRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(simulationGroupId, "simulationGroupId");
        Objects.requireNonNull(request, "request");

        if (request.simulationGroupName() == null
                && request.simulationGroupDescription() == null) {
            throw new IllegalArgumentException("finance.simulationGroup.updateEmpty");
        }

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        SimulationGroup simulationGroup = findOperableActiveSimulationGroup(
                currentUser,
                userGroupId,
                simulationGroupId
        );

        String simulationGroupName = resolveUpdatedName(
                simulationGroup,
                request.simulationGroupName()
        );

        String simulationGroupDescription = resolveUpdatedDescription(
                simulationGroup,
                request.simulationGroupDescription()
        );

        if (simulationGroupRepository.existsActiveByNormalizedNameExcludingSimulationGroupId(
                userGroupId,
                simulationGroup.getSimulationGroupId(),
                simulationGroupName
        )) {
            throwDuplicateName(currentUser);
        }

        simulationGroup.update(
                simulationGroupName,
                simulationGroupDescription
        );

        try {
            simulationGroup = simulationGroupRepository.saveAndFlush(simulationGroup);
        } catch (DataIntegrityViolationException exception) {
            throwDuplicateName(currentUser, exception);
        }

        List<UUID> accountIds = findAccountIdsForResponse(
                currentUser,
                userGroupId,
                simulationGroup
        );

        return SimulationGroupResponse.from(
                simulationGroup,
                accountIds
        );
    }

    private User findCurrentUser(UUID currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    }

    private SimulationGroup findOperableActiveSimulationGroup(
            User currentUser,
            UUID userGroupId,
            UUID simulationGroupId
    ) {
        if (canManageAllSimulationGroups(currentUser)) {
            return simulationGroupRepository
                    .findBySimulationGroupIdAndUserGroup_UserGroupIdAndSimulationGroupArchivedAtIsNull(
                            simulationGroupId,
                            userGroupId
                    )
                    .orElseThrow(() -> new ResourceNotFoundException("finance.simulationGroup.notFound"));
        }

        return simulationGroupRepository.findActiveOperableToLinkedUserById(
                        simulationGroupId,
                        userGroupId,
                        currentUser.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.simulationGroup.notFound"));
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

    private boolean canManageAllSimulationGroups(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR;
    }

    private boolean canSeeAllSimulationGroups(User user) {
        return canManageAllSimulationGroups(user)
                || user.getUserRole() == UserRole.VIEWER_COLLABORATOR;
    }

    private void throwDuplicateName(User currentUser) {
        if (currentUser.getUserRole() == UserRole.COLLABORATOR) {
            throw new IllegalArgumentException("finance.simulationGroup.nameNotAllowed");
        }

        throw new IllegalStateException("finance.simulationGroup.nameAlreadyExists");
    }

    private void throwDuplicateName(User currentUser, Throwable cause) {
        if (currentUser.getUserRole() == UserRole.COLLABORATOR) {
            throw new IllegalArgumentException("finance.simulationGroup.nameNotAllowed", cause);
        }

        throw new IllegalStateException("finance.simulationGroup.nameAlreadyExists", cause);
    }

    private String resolveUpdatedName(
            SimulationGroup simulationGroup,
            String requestedName
    ) {
        if (requestedName == null) {
            return simulationGroup.getSimulationGroupName();
        }

        if (requestedName.isBlank()) {
            throw new IllegalArgumentException("finance.simulationGroup.nameRequired");
        }

        return requestedName.trim();
    }

    private String resolveUpdatedDescription(
            SimulationGroup simulationGroup,
            String requestedDescription
    ) {
        if (requestedDescription == null) {
            return simulationGroup.getSimulationGroupDescription();
        }

        if (requestedDescription.isBlank()) {
            return null;
        }

        return requestedDescription.trim();
    }
}