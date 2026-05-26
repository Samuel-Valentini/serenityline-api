package me.serenityline.api.finance.bucket.repository;

import me.serenityline.api.finance.bucket.entity.BucketAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BucketAccountRepository extends JpaRepository<BucketAccount, UUID> {

    long countByBucket_BucketId(UUID bucketId);

    List<BucketAccount> findAllByBucket_BucketIdOrderByBucketAccountCreatedAtAsc(UUID bucketId);

    @Query(
            value = """
                    select bucket_account.account_id
                    from buckets_accounts bucket_account
                    where bucket_account.bucket_id = :bucketId
                    order by bucket_account.bucket_account_created_at asc
                    """,
            nativeQuery = true
    )
    List<UUID> findAccountIdsByBucketId(
            @Param("bucketId") UUID bucketId
    );

    @Query(
            value = """
                    select bucket_account.account_id
                    from buckets_accounts bucket_account
                    join accounts_users account_user
                      on account_user.account_id = bucket_account.account_id
                     and account_user.user_group_id = bucket_account.user_group_id
                    where bucket_account.bucket_id = :bucketId
                      and bucket_account.user_group_id = :userGroupId
                      and account_user.user_id = :userId
                    order by bucket_account.bucket_account_created_at asc
                    """,
            nativeQuery = true
    )
    List<UUID> findVisibleAccountIdsByBucketIdForCollaborator(
            @Param("bucketId") UUID bucketId,
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query(
            value = """
                    select exists (
                        select 1
                        from buckets_accounts bucket_account
                        where bucket_account.bucket_id = :bucketId
                          and bucket_account.account_id = :accountId
                          and bucket_account.user_group_id = :userGroupId
                    )
                    """,
            nativeQuery = true
    )
    boolean existsByBucketIdAndAccountIdAndUserGroupId(
            @Param("bucketId") UUID bucketId,
            @Param("accountId") UUID accountId,
            @Param("userGroupId") UUID userGroupId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    insert into buckets_accounts (
                        bucket_id,
                        account_id,
                        user_group_id
                    )
                    values (
                        :bucketId,
                        :accountId,
                        :userGroupId
                    )
                    on conflict (bucket_id, account_id, user_group_id) do nothing
                    """,
            nativeQuery = true
    )
    int insertIfMissing(
            @Param("bucketId") UUID bucketId,
            @Param("accountId") UUID accountId,
            @Param("userGroupId") UUID userGroupId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    delete from buckets_accounts
                    where bucket_id = :bucketId
                      and account_id = :accountId
                      and user_group_id = :userGroupId
                    """,
            nativeQuery = true
    )
    int deleteByBucketIdAndAccountIdAndUserGroupId(
            @Param("bucketId") UUID bucketId,
            @Param("accountId") UUID accountId,
            @Param("userGroupId") UUID userGroupId
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM buckets_accounts bucket_account
                WHERE bucket_account.bucket_id = :bucketId
                  AND bucket_account.account_id = :accountId
                  AND bucket_account.user_group_id = :userGroupId
            )
            """, nativeQuery = true)
    boolean existsLink(
            @Param("bucketId") UUID bucketId,
            @Param("accountId") UUID accountId,
            @Param("userGroupId") UUID userGroupId
    );
}