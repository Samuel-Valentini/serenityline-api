package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

    Optional<RecurringTransaction> findByRecurringTransactionIdAndUserGroup_UserGroupId(
            UUID recurringTransactionId,
            UUID userGroupId
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.recurring_transaction_id = :recurringTransactionId
              AND rt.user_group_id = :userGroupId
              AND EXISTS (
                    SELECT 1
                    FROM accounts_users au
                    WHERE au.account_id = current_details.linked_account_id
                      AND au.user_group_id = rt.user_group_id
                      AND au.user_id = :userId
              )
            """, nativeQuery = true)
    Optional<RecurringTransaction> findReadableByLinkedUserAccess(
            @Param("recurringTransactionId") UUID recurringTransactionId,
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND (
                    CAST(:accountId AS uuid) IS NULL
                    OR current_details.linked_account_id = CAST(:accountId AS uuid)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReadableBaseByUserGroup(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountId") UUID accountId
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
              )
              AND (
                    CAST(:accountId AS uuid) IS NULL
                    OR current_details.linked_account_id = CAST(:accountId AS uuid)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReadableBaseAndSimulatedByUserGroup(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupIds") List<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND (
                    CAST(:accountId AS uuid) IS NULL
                    OR current_details.linked_account_id = CAST(:accountId AS uuid)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
              AND EXISTS (
                    SELECT 1
                    FROM accounts_users au
                    WHERE au.account_id = current_details.linked_account_id
                      AND au.user_group_id = rt.user_group_id
                      AND au.user_id = :userId
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReadableBaseByLinkedUserAccess(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
              )
              AND (
                    CAST(:accountId AS uuid) IS NULL
                    OR current_details.linked_account_id = CAST(:accountId AS uuid)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
              AND EXISTS (
                    SELECT 1
                    FROM accounts_users au
                    WHERE au.account_id = current_details.linked_account_id
                      AND au.user_group_id = rt.user_group_id
                      AND au.user_id = :userId
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReadableBaseAndSimulatedByLinkedUserAccess(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupIds") List<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
                AND rt.recurring_transaction_is_simulated = FALSE
                AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                        AND rth.effective_to IS NULL
                )
                AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_details_history rdth
                    JOIN accounts_users au
                        ON au.account_id = rdth.linked_account_id
                        AND au.user_group_id = rdth.user_group_id
                    WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                        AND rdth.user_group_id = rt.user_group_id
                        AND au.user_id = :userId
                        AND (
                            CAST(:accountId AS uuid) IS NULL
                            OR rdth.linked_account_id = CAST(:accountId AS uuid)
                        )
                )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findCalendarReadableBaseByLinkedUserAccess(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
                AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
                )
                AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                        AND rth.effective_to IS NULL
                )
                AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_details_history rdth
                    JOIN accounts_users au
                        ON au.account_id = rdth.linked_account_id
                        AND au.user_group_id = rdth.user_group_id
                    WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                        AND rdth.user_group_id = rt.user_group_id
                        AND au.user_id = :userId
                        AND (
                            CAST(:accountId AS uuid) IS NULL
                            OR rdth.linked_account_id = CAST(:accountId AS uuid)
                        )
                )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findCalendarReadableBaseAndSimulatedByLinkedUserAccess(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupIds") List<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
              AND EXISTS (
                     SELECT 1
                     FROM recurring_transaction_details_history rdth
                     WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                       AND rdth.user_group_id = rt.user_group_id
                       AND (
                             CAST(:accountId AS uuid) IS NULL
                             OR rdth.linked_account_id = CAST(:accountId AS uuid)
                       )
               )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findCalendarReadableBaseByUserGroup(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountId") UUID accountId
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
              AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
              AND EXISTS (
                     SELECT 1
                     FROM recurring_transaction_details_history rdth
                     WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                       AND rdth.user_group_id = rt.user_group_id
                       AND (
                             CAST(:accountId AS uuid) IS NULL
                             OR rdth.linked_account_id = CAST(:accountId AS uuid)
                       )
               )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findCalendarReadableBaseAndSimulatedByUserGroup(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupIds") List<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
              AND EXISTS (
                     SELECT 1
                     FROM recurring_transaction_details_history rdth
                     WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                       AND rdth.user_group_id = rt.user_group_id
                       AND rdth.linked_account_id IN (:accountIds)
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findCalendarReadableBaseByUserGroupForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountIds") Collection<UUID> accountIds
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
              AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
              AND EXISTS (
                     SELECT 1
                     FROM recurring_transaction_details_history rdth
                     WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                       AND rdth.user_group_id = rt.user_group_id
                       AND rdth.linked_account_id IN (:accountIds)
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findCalendarReadableBaseAndSimulatedByUserGroupForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("simulationGroupIds") Collection<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
                AND rt.recurring_transaction_is_simulated = FALSE
                AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                        AND rth.effective_to IS NULL
                )
                AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_details_history rdth
                    JOIN accounts_users au
                        ON au.account_id = rdth.linked_account_id
                        AND au.user_group_id = rdth.user_group_id
                    WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                        AND rdth.user_group_id = rt.user_group_id
                        AND au.user_id = :userId
                        AND rdth.linked_account_id IN (:accountIds)
                )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findCalendarReadableBaseByLinkedUserAccessForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountIds") Collection<UUID> accountIds
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
                AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
                )
                AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                        AND rth.effective_to IS NULL
                )
                AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_details_history rdth
                    JOIN accounts_users au
                        ON au.account_id = rdth.linked_account_id
                        AND au.user_group_id = rdth.user_group_id
                    WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                        AND rdth.user_group_id = rt.user_group_id
                        AND au.user_id = :userId
                        AND rdth.linked_account_id IN (:accountIds)
                )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findCalendarReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("simulationGroupIds") Collection<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                        CAST(:asOfDate AS date),
                        rt.recurring_transaction_first_payment_date
                  )
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND (
                    CAST(:accountId AS uuid) IS NULL
                    OR current_details.linked_account_id = CAST(:accountId AS uuid)
              )
              AND EXISTS (
                     SELECT 1
                     FROM recurring_transaction_history rth
                     WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                       AND rth.effective_from <= GREATEST(
                             CAST(:asOfDate AS date),
                             rt.recurring_transaction_first_payment_date
                       )
                       AND (
                             rth.effective_to IS NULL
                             OR rth.effective_to > GREATEST(
                                 CAST(:asOfDate AS date),
                                 rt.recurring_transaction_first_payment_date
                             )
                       )
               )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReportReadableBaseByUserGroup(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountId") UUID accountId,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                        CAST(:asOfDate AS date),
                        rt.recurring_transaction_first_payment_date
                  )
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
              )
              AND (
                    CAST(:accountId AS uuid) IS NULL
                    OR current_details.linked_account_id = CAST(:accountId AS uuid)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_from <= GREATEST(
                            CAST(:asOfDate AS date),
                            rt.recurring_transaction_first_payment_date
                      )
                      AND (
                            rth.effective_to IS NULL
                            OR rth.effective_to > GREATEST(
                                CAST(:asOfDate AS date),
                                rt.recurring_transaction_first_payment_date
                            )
                      )
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReportReadableBaseAndSimulatedByUserGroup(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupIds") Collection<UUID> simulationGroupIds,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                        CAST(:asOfDate AS date),
                        rt.recurring_transaction_first_payment_date
                  )
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND (
                    CAST(:accountId AS uuid) IS NULL
                    OR current_details.linked_account_id = CAST(:accountId AS uuid)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_from <= GREATEST(
                            CAST(:asOfDate AS date),
                            rt.recurring_transaction_first_payment_date
                      )
                      AND (
                            rth.effective_to IS NULL
                            OR rth.effective_to > GREATEST(
                                CAST(:asOfDate AS date),
                                rt.recurring_transaction_first_payment_date
                            )
                      )
              )
              AND EXISTS (
                    SELECT 1
                    FROM accounts_users au
                    WHERE au.account_id = current_details.linked_account_id
                      AND au.user_group_id = rt.user_group_id
                      AND au.user_id = :userId
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReportReadableBaseByLinkedUserAccess(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                        CAST(:asOfDate AS date),
                        rt.recurring_transaction_first_payment_date
                  )
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
              )
              AND (
                    CAST(:accountId AS uuid) IS NULL
                    OR current_details.linked_account_id = CAST(:accountId AS uuid)
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_from <= GREATEST(
                            CAST(:asOfDate AS date),
                            rt.recurring_transaction_first_payment_date
                      )
                      AND (
                            rth.effective_to IS NULL
                            OR rth.effective_to > GREATEST(
                                CAST(:asOfDate AS date),
                                rt.recurring_transaction_first_payment_date
                            )
                      )
              )
              AND EXISTS (
                    SELECT 1
                    FROM accounts_users au
                    WHERE au.account_id = current_details.linked_account_id
                      AND au.user_group_id = rt.user_group_id
                      AND au.user_id = :userId
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReportReadableBaseAndSimulatedByLinkedUserAccess(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupIds") Collection<UUID> simulationGroupIds,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                        CAST(:asOfDate AS date),
                        rt.recurring_transaction_first_payment_date
                  )
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND current_details.linked_account_id IN (:accountIds)
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_from <= GREATEST(
                            CAST(:asOfDate AS date),
                            rt.recurring_transaction_first_payment_date
                      )
                      AND (
                            rth.effective_to IS NULL
                            OR rth.effective_to > GREATEST(
                                CAST(:asOfDate AS date),
                                rt.recurring_transaction_first_payment_date
                            )
                      )
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReportReadableBaseByUserGroupForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                        CAST(:asOfDate AS date),
                        rt.recurring_transaction_first_payment_date
                  )
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
              )
              AND current_details.linked_account_id IN (:accountIds)
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_from <= GREATEST(
                            CAST(:asOfDate AS date),
                            rt.recurring_transaction_first_payment_date
                      )
                      AND (
                            rth.effective_to IS NULL
                            OR rth.effective_to > GREATEST(
                                CAST(:asOfDate AS date),
                                rt.recurring_transaction_first_payment_date
                            )
                      )
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReportReadableBaseAndSimulatedByUserGroupForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("simulationGroupIds") Collection<UUID> simulationGroupIds,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                        CAST(:asOfDate AS date),
                        rt.recurring_transaction_first_payment_date
                  )
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND current_details.linked_account_id IN (:accountIds)
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_from <= GREATEST(
                            CAST(:asOfDate AS date),
                            rt.recurring_transaction_first_payment_date
                      )
                      AND (
                            rth.effective_to IS NULL
                            OR rth.effective_to > GREATEST(
                                CAST(:asOfDate AS date),
                                rt.recurring_transaction_first_payment_date
                            )
                      )
              )
              AND EXISTS (
                    SELECT 1
                    FROM accounts_users au
                    WHERE au.account_id = current_details.linked_account_id
                      AND au.user_group_id = rt.user_group_id
                      AND au.user_id = :userId
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReportReadableBaseByLinkedUserAccessForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            JOIN LATERAL (
                SELECT rdth.*
                FROM recurring_transaction_details_history rdth
                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                  AND rdth.user_group_id = rt.user_group_id
                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                        CAST(:asOfDate AS date),
                        rt.recurring_transaction_first_payment_date
                  )
                ORDER BY
                    rdth.recurring_transaction_details_effective_from DESC,
                    rdth.recurring_transaction_details_history_created_at DESC,
                    rdth.recurring_transaction_details_history_id DESC
                LIMIT 1
            ) current_details ON TRUE
            WHERE rt.user_group_id = :userGroupId
              AND (
                    rt.recurring_transaction_is_simulated = FALSE
                    OR rt.simulation_group_id IN (:simulationGroupIds)
              )
              AND current_details.linked_account_id IN (:accountIds)
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
            
                      AND rth.effective_from <= GREATEST(
                            CAST(:asOfDate AS date),
                            rt.recurring_transaction_first_payment_date
                      )
                      AND (
                            rth.effective_to IS NULL
                            OR rth.effective_to > GREATEST(
                                CAST(:asOfDate AS date),
                                rt.recurring_transaction_first_payment_date
                            )
                      )
              )
              AND EXISTS (
                    SELECT 1
                    FROM accounts_users au
                    WHERE au.account_id = current_details.linked_account_id
                      AND au.user_group_id = rt.user_group_id
                      AND au.user_id = :userId
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findReportReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("simulationGroupIds") Collection<UUID> simulationGroupIds,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT rt.*
            FROM recurring_transactions rt
            WHERE rt.user_group_id = :userGroupId
              AND rt.recurring_transaction_is_simulated = FALSE
              AND rt.recurring_transaction_first_payment_date <= :latestRelevantDate
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_history rth
                    WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                      AND rth.effective_to IS NULL
              )
              AND EXISTS (
                    SELECT 1
                    FROM recurring_transaction_details_history details
                    WHERE details.recurring_transaction_id = rt.recurring_transaction_id
                      AND details.user_group_id = rt.user_group_id
                      AND details.linked_bucket_id = :bucketId
                      AND details.recurring_transaction_details_effective_from <= :latestRelevantDate
              )
            ORDER BY
                rt.recurring_transaction_first_payment_date,
                rt.recurring_transaction_created_at,
                rt.recurring_transaction_id
            """, nativeQuery = true)
    List<RecurringTransaction> findBaseOpenRecurringTransactionsEverLinkedToBucket(
            @Param("userGroupId") UUID userGroupId,
            @Param("bucketId") UUID bucketId,
            @Param("latestRelevantDate") LocalDate latestRelevantDate
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM recurring_transactions rt
                WHERE rt.user_group_id = :userGroupId
                  AND rt.recurring_transaction_is_simulated = FALSE
                  AND EXISTS (
                        SELECT 1
                        FROM recurring_transaction_history rth
                        WHERE rth.recurring_transaction_id = rt.recurring_transaction_id
                          AND rth.effective_from <= GREATEST(
                                CAST(:asOfDate AS date),
                                rt.recurring_transaction_first_payment_date
                          )
                          AND (
                                rth.effective_to IS NULL
                                OR rth.effective_to > CAST(:asOfDate AS date)
                          )
                          AND (
                                rth.recurring_transaction_end_date IS NULL
                                OR rth.recurring_transaction_end_date > CAST(:asOfDate AS date)
                          )
                  )
                  AND (
                        EXISTS (
                            SELECT 1
                            FROM LATERAL (
                                SELECT rdth.linked_bucket_id
                                FROM recurring_transaction_details_history rdth
                                WHERE rdth.recurring_transaction_id = rt.recurring_transaction_id
                                  AND rdth.user_group_id = rt.user_group_id
                                  AND rdth.recurring_transaction_details_effective_from <= GREATEST(
                                        CAST(:asOfDate AS date),
                                        rt.recurring_transaction_first_payment_date
                                  )
                                ORDER BY
                                    rdth.recurring_transaction_details_effective_from DESC,
                                    rdth.recurring_transaction_details_history_created_at DESC,
                                    rdth.recurring_transaction_details_history_id DESC
                                LIMIT 1
                            ) current_details
                            WHERE current_details.linked_bucket_id = :bucketId
                        )
                        OR EXISTS (
                            SELECT 1
                            FROM recurring_transaction_details_history future_details
                            WHERE future_details.recurring_transaction_id = rt.recurring_transaction_id
                              AND future_details.user_group_id = rt.user_group_id
                              AND future_details.linked_bucket_id = :bucketId
                              AND future_details.recurring_transaction_details_effective_from > CAST(:asOfDate AS date)
                        )
                  )
            )
            """, nativeQuery = true)
    boolean existsOpenOrFutureBaseRecurringTransactionLinkedToBucket(
            @Param("bucketId") UUID bucketId,
            @Param("userGroupId") UUID userGroupId,
            @Param("asOfDate") LocalDate asOfDate
    );

}