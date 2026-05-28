package me.serenityline.api.finance.account.repository;

import me.serenityline.api.finance.account.entity.AccountUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountUserRepository extends JpaRepository<AccountUser, UUID> {

    boolean existsByAccount_AccountIdAndUser_UserId(
            UUID accountId,
            UUID userId
    );

    long countByAccount_AccountId(UUID accountId);

    long countByUser_UserId(UUID userId);

    List<AccountUser> findAllByAccount_AccountId(UUID accountId);

    List<AccountUser> findAllByUser_UserId(UUID userId);

    Optional<AccountUser> findByAccount_AccountIdAndUser_UserId(
            UUID accountId,
            UUID userId
    );

    @Query("""
            SELECT au.account.accountId
            FROM AccountUser au
            WHERE au.userGroup.userGroupId = :userGroupId
                AND au.user.userId = :userId
            """)
    List<UUID> findVisibleAccountIdsForUser(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );
}