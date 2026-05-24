package me.serenityline.api.finance.account.repository;

import me.serenityline.api.finance.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    long countByUserGroup_UserGroupId(UUID userGroupId);

    Optional<Account> findByAccountIdAndUserGroup_UserGroupId(
            UUID accountId,
            UUID userGroupId
    );

    List<Account> findAllByUserGroup_UserGroupIdOrderByAccountNameAsc(
            UUID userGroupId
    );

    @Query(
            value = """
                    select exists (
                        select 1
                        from accounts a
                        where a.user_group_id = :userGroupId
                          and lower(btrim(regexp_replace(a.account_name, '[[:space:]]+', ' ', 'g'))) = :normalizedAccountName
                    )
                    """,
            nativeQuery = true
    )
    boolean existsByUserGroupIdAndNormalizedAccountName(
            @Param("userGroupId") UUID userGroupId,
            @Param("normalizedAccountName") String normalizedAccountName
    );

    @Query("""
            select account
            from Account account
            join AccountUser accountUser on accountUser.account = account
            where account.userGroup.userGroupId = :userGroupId
              and accountUser.user.userId = :userId
            order by lower(account.accountName), account.accountName
            """)
    List<Account> findAllVisibleToLinkedUser(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query("""
            select account
            from Account account
            join AccountUser accountUser on accountUser.account = account
            where account.accountId = :accountId
              and account.userGroup.userGroupId = :userGroupId
              and accountUser.user.userId = :userId
            """)
    Optional<Account> findVisibleAccountForLinkedUser(
            @Param("accountId") UUID accountId,
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query(
            value = """
                    select exists (
                        select 1
                        from accounts a
                        where a.user_group_id = :userGroupId
                          and a.account_id <> :accountId
                          and lower(btrim(regexp_replace(a.account_name, '[[:space:]]+', ' ', 'g'))) = :normalizedAccountName
                    )
                    """,
            nativeQuery = true
    )
    boolean existsByUserGroupIdAndNormalizedAccountNameExcludingAccountId(
            @Param("userGroupId") UUID userGroupId,
            @Param("normalizedAccountName") String normalizedAccountName,
            @Param("accountId") UUID accountId
    );

}