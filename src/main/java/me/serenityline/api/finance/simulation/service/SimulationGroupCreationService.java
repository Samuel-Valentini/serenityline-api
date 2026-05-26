package me.serenityline.api.finance.simulation.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.simulation.dto.SimulationGroupCreateRequest;
import me.serenityline.api.finance.simulation.dto.SimulationGroupResponse;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.simulation.repository.SimulationGroupAccountRepository;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SimulationGroupCreationService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final SimulationGroupAccountRepository simulationGroupAccountRepository;

    public SimulationGroupCreationService(
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
    public SimulationGroupResponse createSimulationGroup(
            UUID currentUserId,
            SimulationGroupCreateRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(request, "request");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        String simulationGroupName = cleanRequiredName(request.simulationGroupName());
        String simulationGroupDescription = cleanOptionalDescription(request.simulationGroupDescription());

        Set<UUID> accountIds = normalizeAccountIds(request.accountIds());

        if (requiresAccountScope(currentUser) && accountIds.isEmpty()) {
            throw new IllegalArgumentException("finance.simulationGroup.accountIdsRequired");
        }

        if (simulationGroupRepository.existsActiveByNormalizedName(userGroupId, simulationGroupName)) {
            throwDuplicateName(currentUser);
        }

        List<Account> accounts = resolveAccounts(currentUser, userGroupId, accountIds);

        SimulationGroup simulationGroup = SimulationGroup.create(
                currentUser.getUserGroup(),
                simulationGroupName,
                simulationGroupDescription
        );

        try {
            simulationGroup = simulationGroupRepository.saveAndFlush(simulationGroup);
        } catch (DataIntegrityViolationException exception) {
            throwDuplicateName(currentUser, exception);
        }

        for (Account account : accounts) {
            simulationGroupAccountRepository.insertIfMissing(
                    simulationGroup.getSimulationGroupId(),
                    account.getAccountId(),
                    userGroupId
            );
        }

        List<UUID> linkedAccountIds = simulationGroupAccountRepository.findAccountIds(
                simulationGroup.getSimulationGroupId(),
                userGroupId
        );

        return SimulationGroupResponse.from(simulationGroup, linkedAccountIds);
    }

    private User findCurrentUser(UUID currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    }

    private String cleanRequiredName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("finance.simulationGroup.nameRequired");
        }

        return value.trim();
    }

    private String cleanOptionalDescription(String value) {
        if (value == null) {
            return null;
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException("finance.simulationGroup.descriptionBlank");
        }

        return value.trim();
    }

    private Set<UUID> normalizeAccountIds(Set<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Set.of();
        }

        if (accountIds.contains(null)) {
            throw new IllegalArgumentException("finance.simulationGroup.accountIdRequired");
        }

        return new LinkedHashSet<>(accountIds);
    }

    private List<Account> resolveAccounts(
            User currentUser,
            UUID userGroupId,
            Set<UUID> accountIds
    ) {
        return accountIds.stream()
                .map(accountId -> resolveAccount(currentUser, userGroupId, accountId))
                .toList();
    }

    private Account resolveAccount(
            User currentUser,
            UUID userGroupId,
            UUID accountId
    ) {
        if (canOperateOnAllAccounts(currentUser)) {
            return accountRepository.findByAccountIdAndUserGroup_UserGroupId(accountId, userGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
        }

        if (currentUser.getUserRole() == UserRole.VIEWER_COLLABORATOR) {
            Account account = accountRepository.findByAccountIdAndUserGroup_UserGroupId(accountId, userGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));

            accountRepository.findVisibleAccountForLinkedUser(accountId, userGroupId, currentUser.getUserId())
                    .orElseThrow(() -> new AccessDeniedException("finance.account.operationNotAllowed"));

            return account;
        }

        return accountRepository.findVisibleAccountForLinkedUser(accountId, userGroupId, currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
    }

    private boolean canOperateOnAllAccounts(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR;
    }

    private boolean requiresAccountScope(User user) {
        return user.getUserRole() == UserRole.VIEWER_COLLABORATOR
                || user.getUserRole() == UserRole.COLLABORATOR;
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
}