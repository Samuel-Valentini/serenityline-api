package me.serenityline.api.finance.simulation.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.simulation.dto.SimulationGroupResponse;
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
public class SimulationGroupLifecycleService {

    private final UserRepository userRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final SimulationGroupAccountRepository simulationGroupAccountRepository;

    public SimulationGroupLifecycleService(
            UserRepository userRepository,
            SimulationGroupRepository simulationGroupRepository,
            SimulationGroupAccountRepository simulationGroupAccountRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.simulationGroupAccountRepository = Objects.requireNonNull(simulationGroupAccountRepository, "simulationGroupAccountRepository");
    }

    @Transactional
    public SimulationGroupResponse archiveSimulationGroup(
            UUID currentUserId,
            UUID simulationGroupId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(simulationGroupId, "simulationGroupId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        SimulationGroup simulationGroup = findOperableActiveSimulationGroup(
                currentUser,
                userGroupId,
                simulationGroupId
        );

        simulationGroup.archive();

        simulationGroup = simulationGroupRepository.saveAndFlush(simulationGroup);

        return SimulationGroupResponse.from(
                simulationGroup,
                findAccountIdsForResponse(currentUser, userGroupId, simulationGroup)
        );
    }

    @Transactional
    public SimulationGroupResponse restoreSimulationGroup(
            UUID currentUserId,
            UUID simulationGroupId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(simulationGroupId, "simulationGroupId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        SimulationGroup simulationGroup = findOperableArchivedSimulationGroup(
                currentUser,
                userGroupId,
                simulationGroupId
        );

        if (simulationGroupRepository.existsActiveByNormalizedName(
                userGroupId,
                simulationGroup.getSimulationGroupName()
        )) {
            throwRestoreDuplicateName(
                    currentUser,
                    userGroupId,
                    simulationGroup.getSimulationGroupName()
            );
        }

        simulationGroup.restore();

        try {
            simulationGroup = simulationGroupRepository.saveAndFlush(simulationGroup);
        } catch (DataIntegrityViolationException exception) {
            throwRestoreDuplicateName(
                    currentUser,
                    userGroupId,
                    simulationGroup.getSimulationGroupName(),
                    exception
            );
        }

        return SimulationGroupResponse.from(
                simulationGroup,
                findAccountIdsForResponse(currentUser, userGroupId, simulationGroup)
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

    private SimulationGroup findOperableArchivedSimulationGroup(
            User currentUser,
            UUID userGroupId,
            UUID simulationGroupId
    ) {
        if (canManageAllSimulationGroups(currentUser)) {
            return simulationGroupRepository
                    .findBySimulationGroupIdAndUserGroup_UserGroupIdAndSimulationGroupArchivedAtIsNotNull(
                            simulationGroupId,
                            userGroupId
                    )
                    .orElseThrow(() -> new ResourceNotFoundException("finance.simulationGroup.notFound"));
        }

        return simulationGroupRepository.findArchivedOperableToLinkedUserById(
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

    private void throwRestoreDuplicateName(
            User currentUser,
            UUID userGroupId,
            String simulationGroupName
    ) {
        throwRestoreDuplicateName(
                currentUser,
                userGroupId,
                simulationGroupName,
                null
        );
    }

    private void throwRestoreDuplicateName(
            User currentUser,
            UUID userGroupId,
            String simulationGroupName,
            Throwable cause
    ) {
        if (currentUser.getUserRole() != UserRole.COLLABORATOR) {
            throw new IllegalStateException(
                    "finance.simulationGroup.nameAlreadyExists",
                    cause
            );
        }

        boolean visibleDuplicateExists = simulationGroupRepository.existsActiveByNormalizedNameVisibleToLinkedUser(
                userGroupId,
                currentUser.getUserId(),
                simulationGroupName
        );

        if (visibleDuplicateExists) {
            throw new IllegalStateException(
                    "finance.simulationGroup.nameAlreadyExists",
                    cause
            );
        }

        throw new IllegalArgumentException(
                "finance.simulationGroup.nameNotAllowed",
                cause
        );
    }
}