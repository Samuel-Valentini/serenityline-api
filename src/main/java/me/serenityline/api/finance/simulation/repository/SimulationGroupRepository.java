package me.serenityline.api.finance.simulation.repository;

import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}