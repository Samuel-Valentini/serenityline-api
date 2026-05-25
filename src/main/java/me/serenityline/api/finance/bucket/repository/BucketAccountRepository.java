package me.serenityline.api.finance.bucket.repository;

import me.serenityline.api.finance.bucket.entity.BucketAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BucketAccountRepository extends JpaRepository<BucketAccount, UUID> {

    long countByBucket_BucketId(UUID bucketId);

    List<BucketAccount> findAllByBucket_BucketIdOrderByBucketAccountCreatedAtAsc(UUID bucketId);
}