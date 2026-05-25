package me.serenityline.api.finance.bucket.repository;

import me.serenityline.api.finance.bucket.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BucketRepository extends JpaRepository<Bucket, UUID> {

    long countByUserGroup_UserGroupId(UUID userGroupId);

    Optional<Bucket> findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNull(
            UUID bucketId,
            UUID userGroupId
    );

    @Query("""
            select bucket
            from Bucket bucket
            where bucket.userGroup.userGroupId = :userGroupId
              and bucket.bucketClosedAt is null
            order by lower(bucket.bucketName), bucket.bucketName
            """)
    List<Bucket> findAllActiveByUserGroupId(
            @Param("userGroupId") UUID userGroupId
    );

    @Query(
            value = """
                    select bucket.*
                    from buckets bucket
                    where bucket.user_group_id = :userGroupId
                      and bucket.bucket_closed_at is null
                      and exists (
                          select 1
                          from buckets_accounts bucket_account
                          join accounts_users account_user
                            on account_user.account_id = bucket_account.account_id
                           and account_user.user_group_id = bucket_account.user_group_id
                          where bucket_account.bucket_id = bucket.bucket_id
                            and bucket_account.user_group_id = bucket.user_group_id
                            and account_user.user_id = :userId
                      )
                    order by bucket.bucket_name
                    """,
            nativeQuery = true
    )
    List<Bucket> findAllActiveVisibleForCollaborator(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query(
            value = """
                    select bucket.*
                    from buckets bucket
                    where bucket.bucket_id = :bucketId
                      and bucket.user_group_id = :userGroupId
                      and bucket.bucket_closed_at is null
                      and exists (
                          select 1
                          from buckets_accounts bucket_account
                          join accounts_users account_user
                            on account_user.account_id = bucket_account.account_id
                           and account_user.user_group_id = bucket_account.user_group_id
                          where bucket_account.bucket_id = bucket.bucket_id
                            and bucket_account.user_group_id = bucket.user_group_id
                            and account_user.user_id = :userId
                      )
                    """,
            nativeQuery = true
    )
    Optional<Bucket> findActiveVisibleForCollaborator(
            @Param("bucketId") UUID bucketId,
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query(
            value = """
                    select exists (
                        select 1
                        from buckets bucket
                        where bucket.user_group_id = :userGroupId
                          and bucket.bucket_closed_at is null
                          and lower(btrim(regexp_replace(bucket.bucket_name, '[[:space:]]+', ' ', 'g'))) = :normalizedBucketName
                    )
                    """,
            nativeQuery = true
    )
    boolean existsActiveByUserGroupIdAndNormalizedBucketName(
            @Param("userGroupId") UUID userGroupId,
            @Param("normalizedBucketName") String normalizedBucketName
    );

    @Query(
            value = """
                    select bucket.*
                    from buckets bucket
                    where bucket.bucket_id = :bucketId
                      and bucket.user_group_id = :userGroupId
                      and bucket.bucket_closed_at is null
                      and exists (
                          select 1
                          from buckets_accounts bucket_account
                          join accounts_users account_user
                            on account_user.account_id = bucket_account.account_id
                           and account_user.user_group_id = bucket_account.user_group_id
                          where bucket_account.bucket_id = bucket.bucket_id
                            and bucket_account.user_group_id = bucket.user_group_id
                            and account_user.user_id = :userId
                      )
                    """,
            nativeQuery = true
    )
    Optional<Bucket> findActiveLinkedToAccessibleAccount(
            @Param("bucketId") UUID bucketId,
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );
}