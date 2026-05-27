package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionResponse;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.*;

@Service
public class RecurringTransactionListService {

    private final UserRepository userRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final RecurringTransactionAccessService recurringTransactionAccessService;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;
    @Value("${serenityline.finance.max-simulation-group-ids:50}")
    private int maxSimulationGroupIds;

    public RecurringTransactionListService(
            UserRepository userRepository,
            SimulationGroupRepository simulationGroupRepository,
            RecurringTransactionAccessService recurringTransactionAccessService,
            RecurringTransactionRepository recurringTransactionRepository,
            RecurringTransactionHistoryRepository recurringTransactionHistoryRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.simulationGroupRepository = Objects.requireNonNull(
                simulationGroupRepository,
                "simulationGroupRepository"
        );
        this.recurringTransactionAccessService = Objects.requireNonNull(
                recurringTransactionAccessService,
                "recurringTransactionAccessService"
        );
        this.recurringTransactionRepository = Objects.requireNonNull(
                recurringTransactionRepository,
                "recurringTransactionRepository"
        );
        this.recurringTransactionHistoryRepository = Objects.requireNonNull(
                recurringTransactionHistoryRepository,
                "recurringTransactionHistoryRepository"
        );
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(
                recurringTransactionDetailsHistoryRepository,
                "recurringTransactionDetailsHistoryRepository"
        );
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> listRecurringTransactions(
            UUID currentUserId,
            UUID accountId,
            List<UUID> simulationGroupIds
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        recurringTransactionAccessService.assertReadableAccountFilter(
                currentUser,
                userGroupId,
                accountId
        );

        List<UUID> normalizedSimulationGroupIds = normalizeSimulationGroupIds(simulationGroupIds);

        validateSimulationGroups(
                currentUser,
                userGroupId,
                normalizedSimulationGroupIds
        );

        List<RecurringTransaction> recurringTransactions = findReadableRecurringTransactions(
                currentUser,
                userGroupId,
                accountId,
                normalizedSimulationGroupIds
        );

        return recurringTransactions.stream()
                .map(recurringTransaction -> toResponse(recurringTransaction, userGroupId))
                .toList();
    }

    private List<RecurringTransaction> findReadableRecurringTransactions(
            User currentUser,
            UUID userGroupId,
            UUID accountId,
            List<UUID> simulationGroupIds
    ) {
        boolean includeSimulations = !simulationGroupIds.isEmpty();

        if (recurringTransactionAccessService.canReadAllGroupRecurringTransactions(currentUser)) {
            if (includeSimulations) {
                return recurringTransactionRepository.findReadableBaseAndSimulatedByUserGroup(
                        userGroupId,
                        accountId,
                        simulationGroupIds
                );
            }

            return recurringTransactionRepository.findReadableBaseByUserGroup(
                    userGroupId,
                    accountId
            );
        }

        if (includeSimulations) {
            return recurringTransactionRepository.findReadableBaseAndSimulatedByLinkedUserAccess(
                    userGroupId,
                    currentUser.getUserId(),
                    accountId,
                    simulationGroupIds
            );
        }

        return recurringTransactionRepository.findReadableBaseByLinkedUserAccess(
                userGroupId,
                currentUser.getUserId(),
                accountId
        );
    }

    private RecurringTransactionResponse toResponse(
            RecurringTransaction recurringTransaction,
            UUID userGroupId
    ) {
        RecurringTransactionHistory currentHistory = recurringTransactionHistoryRepository
                .findFirstByRecurringTransaction_RecurringTransactionIdAndEffectiveToIsNullOrderByEffectiveFromDescRecurringTransactionHistoryCreatedAtDesc(
                        recurringTransaction.getRecurringTransactionId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.historyNotFound"));

        RecurringTransactionDetailsHistory currentDetails = recurringTransactionDetailsHistoryRepository
                .findFirstByRecurringTransaction_RecurringTransactionIdAndUserGroup_UserGroupIdOrderByRecurringTransactionDetailsEffectiveFromDescRecurringTransactionDetailsHistoryCreatedAtDesc(
                        recurringTransaction.getRecurringTransactionId(),
                        userGroupId
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.detailsNotFound"));

        return RecurringTransactionResponse.from(
                recurringTransaction,
                currentHistory,
                currentDetails
        );
    }

    private List<UUID> normalizeSimulationGroupIds(List<UUID> simulationGroupIds) {
        if (simulationGroupIds == null || simulationGroupIds.isEmpty()) {
            return List.of();
        }

        List<UUID> normalized = new LinkedHashSet<>(simulationGroupIds)
                .stream()
                .toList();

        if (normalized.size() > maxSimulationGroupIds) {
            throw new IllegalArgumentException("finance.recurringTransaction.tooManySimulationGroups");
        }

        return normalized;
    }

    private void validateSimulationGroups(
            User currentUser,
            UUID userGroupId,
            List<UUID> simulationGroupIds
    ) {
        if (simulationGroupIds.isEmpty()) {
            return;
        }

        List<UUID> validSimulationGroupIds;

        if (recurringTransactionAccessService.canReadAllGroupRecurringTransactions(currentUser)) {
            validSimulationGroupIds = simulationGroupRepository.findActiveIdsByUserGroupId(
                    simulationGroupIds,
                    userGroupId
            );
        } else {
            validSimulationGroupIds = simulationGroupRepository.findActiveIdsReadableByLinkedUser(
                    simulationGroupIds,
                    userGroupId,
                    currentUser.getUserId()
            );
        }

        if (!sameIds(simulationGroupIds, validSimulationGroupIds)) {
            throw new ResourceNotFoundException("finance.simulationGroup.notFound");
        }
    }

    private boolean sameIds(
            Collection<UUID> requestedIds,
            Collection<UUID> validIds
    ) {
        return new LinkedHashSet<>(requestedIds).equals(new LinkedHashSet<>(validIds));
    }
}