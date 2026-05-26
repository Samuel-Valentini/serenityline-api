package me.serenityline.api.finance.simulation.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.simulation.dto.SimulationGroupResponse;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.simulation.repository.SimulationGroupAccountRepository;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SimulationGroupAccountService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final SimulationGroupAccountRepository simulationGroupAccountRepository;

    public SimulationGroupAccountService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            SimulationGroupRepository simulationGroupRepository,
            SimulationGroupAccountRepository simulationGroupAccountRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.simulationGroupAccountRepository = Objects.requireNonNull(simulationGroupAccountRepository, "simulationGroupAccountRepository");
    }

    @Transactional
    public SimulationGroupResponse linkAccount(
            UUID currentUserId,
            UUID simulationGroupId,
            UUID accountId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(simulationGroupId, "simulationGroupId");
        Objects.requireNonNull(accountId, "accountId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        SimulationGroup simulationGroup = findOperableActiveSimulationGroup(
                currentUser,
                userGroupId,
                simulationGroupId
        );

        Account account = resolveOperableAccount(
                currentUser,
                userGroupId,
                accountId
        );

        simulationGroupAccountRepository.insertIfMissing(
                simulationGroup.getSimulationGroupId(),
                account.getAccountId(),
                userGroupId
        );

        return SimulationGroupResponse.from(
                simulationGroup,
                findAccountIdsForResponse(currentUser, userGroupId, simulationGroup)
        );
    }

    @Transactional
    public SimulationGroupResponse unlinkAccount(
            UUID currentUserId,
            UUID simulationGroupId,
            UUID accountId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(simulationGroupId, "simulationGroupId");
        Objects.requireNonNull(accountId, "accountId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        SimulationGroup simulationGroup = findOperableActiveSimulationGroup(
                currentUser,
                userGroupId,
                simulationGroupId
        );

        Account account = resolveOperableAccount(
                currentUser,
                userGroupId,
                accountId
        );

        if (!simulationGroupAccountRepository.existsLink(
                simulationGroup.getSimulationGroupId(),
                account.getAccountId(),
                userGroupId
        )) {
            throw new ResourceNotFoundException("finance.simulationGroup.accountLinkNotFound");
        }

        if (!canManageAllSimulationGroups(currentUser)) {
            long remainingVisibleAccounts = simulationGroupAccountRepository.countVisibleAccountIdsExcludingAccountId(
                    simulationGroup.getSimulationGroupId(),
                    userGroupId,
                    currentUser.getUserId(),
                    account.getAccountId()
            );

            if (remainingVisibleAccounts == 0) {
                throw new IllegalArgumentException("finance.simulationGroup.accessibleAccountRequired");
            }
        }

        simulationGroupAccountRepository.deleteLink(
                simulationGroup.getSimulationGroupId(),
                account.getAccountId(),
                userGroupId
        );

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

    private Account resolveOperableAccount(
            User currentUser,
            UUID userGroupId,
            UUID accountId
    ) {
        if (canManageAllSimulationGroups(currentUser)) {
            return accountRepository.findByAccountIdAndUserGroup_UserGroupId(accountId, userGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
        }

        if (currentUser.getUserRole() == UserRole.VIEWER_COLLABORATOR) {
            Account account = accountRepository.findByAccountIdAndUserGroup_UserGroupId(accountId, userGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));

            accountRepository.findVisibleAccountForLinkedUser(
                            accountId,
                            userGroupId,
                            currentUser.getUserId()
                    )
                    .orElseThrow(() -> new AccessDeniedException("finance.account.operationNotAllowed"));

            return account;
        }

        return accountRepository.findVisibleAccountForLinkedUser(
                        accountId,
                        userGroupId,
                        currentUser.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
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
}