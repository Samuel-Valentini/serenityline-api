package me.serenityline.api.finance.simulation.repository;

import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SimulationGroupRepository extends JpaRepository<SimulationGroup, UUID> {

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM simulation_groups simulation_group
                WHERE simulation_group.user_group_id = :userGroupId
                  AND simulation_group.simulation_group_archived_at IS NULL
                  AND lower(btrim(regexp_replace(simulation_group.simulation_group_name, '[[:space:]]+', ' ', 'g')))
                    = lower(btrim(regexp_replace(:simulationGroupName, '[[:space:]]+', ' ', 'g')))
            )
            """, nativeQuery = true)
    boolean existsActiveByNormalizedName(
            @Param("userGroupId") UUID userGroupId,
            @Param("simulationGroupName") String simulationGroupName
    );

    @Query(value = """
            SELECT simulation_group.*
            FROM simulation_groups simulation_group
            WHERE simulation_group.user_group_id = :userGroupId
              AND (
                    (:includeActive = true AND simulation_group.simulation_group_archived_at IS NULL)
                    OR
                    (:includeArchived = true AND simulation_group.simulation_group_archived_at IS NOT NULL)
              )
            ORDER BY lower(simulation_group.simulation_group_name), simulation_group.simulation_group_name
            """, nativeQuery = true)
    List<SimulationGroup> findAllByUserGroupIdAndStatus(
            @Param("userGroupId") UUID userGroupId,
            @Param("includeActive") boolean includeActive,
            @Param("includeArchived") boolean includeArchived
    );

    @Query(value = """
            SELECT simulation_group.*
            FROM simulation_groups simulation_group
            WHERE simulation_group.user_group_id = :userGroupId
              AND (
                    (:includeActive = true AND simulation_group.simulation_group_archived_at IS NULL)
                    OR
                    (:includeArchived = true AND simulation_group.simulation_group_archived_at IS NOT NULL)
              )
              AND EXISTS (
                    SELECT 1
                    FROM simulation_groups_accounts simulation_group_account
                    JOIN accounts_users account_user
                      ON account_user.account_id = simulation_group_account.account_id
                     AND account_user.user_group_id = simulation_group_account.user_group_id
                    WHERE simulation_group_account.simulation_group_id = simulation_group.simulation_group_id
                      AND simulation_group_account.user_group_id = simulation_group.user_group_id
                      AND account_user.user_id = :userId
              )
            ORDER BY lower(simulation_group.simulation_group_name), simulation_group.simulation_group_name
            """, nativeQuery = true)
    List<SimulationGroup> findAllVisibleToLinkedUserByStatus(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("includeActive") boolean includeActive,
            @Param("includeArchived") boolean includeArchived
    );
}