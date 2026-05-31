package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BucketAccountUsageCheckerTest {

    private static final UUID BUCKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final UUID USER_GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000300");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;

    private BucketAccountUsageChecker bucketAccountUsageChecker;

    @BeforeEach
    void setUp() {
        bucketAccountUsageChecker = new BucketAccountUsageChecker(
                transactionRepository,
                recurringTransactionDetailsHistoryRepository
        );
    }

    @Test
    void isBucketAccountUsedShouldReturnTrueWhenTransactionExists() {
        given(transactionRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(true);

        boolean used = bucketAccountUsageChecker.isBucketAccountUsed(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        );

        assertThat(used).isTrue();
    }

    @Test
    void isBucketAccountUsedShouldReturnTrueWhenRecurringTransactionDetailsHistoryExists() {
        given(transactionRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(false);

        given(recurringTransactionDetailsHistoryRepository.existsByLinkedBucketIdAndLinkedAccountIdAndUserGroupId(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(true);

        boolean used = bucketAccountUsageChecker.isBucketAccountUsed(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        );

        assertThat(used).isTrue();
    }

    @Test
    void isBucketAccountUsedShouldReturnFalseWhenNoUsageExists() {
        given(transactionRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(false);

        given(recurringTransactionDetailsHistoryRepository.existsByLinkedBucketIdAndLinkedAccountIdAndUserGroupId(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(false);

        boolean used = bucketAccountUsageChecker.isBucketAccountUsed(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        );

        assertThat(used).isFalse();
    }

    @Test
    void isBucketAccountUsedShouldShortCircuitWhenTransactionExists() {
        given(transactionRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(true);

        boolean used = bucketAccountUsageChecker.isBucketAccountUsed(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        );

        assertThat(used).isTrue();

        verifyNoInteractions(recurringTransactionDetailsHistoryRepository);
    }

    @Test
    void isBucketAccountUsedShouldRejectNullBucketId() {
        assertThatNullPointerException()
                .isThrownBy(() -> bucketAccountUsageChecker.isBucketAccountUsed(
                        null,
                        ACCOUNT_ID,
                        USER_GROUP_ID
                ))
                .withMessage("bucketId");
    }

    @Test
    void isBucketAccountUsedShouldRejectNullAccountId() {
        assertThatNullPointerException()
                .isThrownBy(() -> bucketAccountUsageChecker.isBucketAccountUsed(
                        BUCKET_ID,
                        null,
                        USER_GROUP_ID
                ))
                .withMessage("accountId");
    }

    @Test
    void isBucketAccountUsedShouldRejectNullUserGroupId() {
        assertThatNullPointerException()
                .isThrownBy(() -> bucketAccountUsageChecker.isBucketAccountUsed(
                        BUCKET_ID,
                        ACCOUNT_ID,
                        null
                ))
                .withMessage("userGroupId");
    }
}