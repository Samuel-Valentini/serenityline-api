package me.serenityline.api.finance.bucket.repository;

import me.serenityline.api.finance.bucket.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface BucketRepository extends JpaRepository<Bucket, UUID> {

    long countByUserGroup_UserGroupId(UUID userGroupId);

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
}