package me.serenityline.api.finance.simulation.repository;

import me.serenityline.api.finance.simulation.entity.SimulationGroupAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SimulationGroupAccountRepository extends JpaRepository<SimulationGroupAccount, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO simulation_groups_accounts (
                simulation_group_id,
                account_id,
                user_group_id
            )
            VALUES (
                :simulationGroupId,
                :accountId,
                :userGroupId
            )
            ON CONFLICT (simulation_group_id, account_id, user_group_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(
            @Param("simulationGroupId") UUID simulationGroupId,
            @Param("accountId") UUID accountId,
            @Param("userGroupId") UUID userGroupId
    );

    @Query(value = """
            SELECT simulation_group_account.account_id
            FROM simulation_groups_accounts simulation_group_account
            WHERE simulation_group_account.simulation_group_id = :simulationGroupId
              AND simulation_group_account.user_group_id = :userGroupId
            ORDER BY simulation_group_account.account_id
            """, nativeQuery = true)
    List<UUID> findAccountIds(
            @Param("simulationGroupId") UUID simulationGroupId,
            @Param("userGroupId") UUID userGroupId
    );
}