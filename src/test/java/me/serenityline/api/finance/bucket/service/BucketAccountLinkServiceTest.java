package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.repository.BucketAccountRepository;
import me.serenityline.api.finance.bucket.repository.BucketRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BucketAccountLinkServiceTest {

    private static final UUID CURRENT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BUCKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000200");

    @Mock
    private BucketRepository bucketRepository;

    @Mock
    private BucketAccountRepository bucketAccountRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BucketAccountUsageChecker bucketAccountUsageChecker;

    @Mock
    private User currentUser;

    @Mock
    private UserGroup userGroup;

    @Mock
    private Bucket bucket;

    @Mock
    private Account account;

    private BucketAccountLinkService bucketAccountLinkService;

    @BeforeEach
    void setUp() {
        bucketAccountLinkService = new BucketAccountLinkService(
                bucketRepository,
                bucketAccountRepository,
                accountRepository,
                userRepository,
                bucketAccountUsageChecker
        );
    }

    @Test
    void unlinkAccountShouldRejectUsedBucketAccountLink() {
        givenCurrentOwner();

        given(bucketRepository.findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNull(
                BUCKET_ID,
                USER_GROUP_ID
        )).willReturn(Optional.of(bucket));

        given(accountRepository.findByAccountIdAndUserGroup_UserGroupId(
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(Optional.of(account));

        given(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(true);

        given(bucketAccountUsageChecker.isBucketAccountUsed(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(true);

        assertThatIllegalStateException()
                .isThrownBy(() -> bucketAccountLinkService.unlinkAccount(
                        CURRENT_USER_ID,
                        BUCKET_ID,
                        ACCOUNT_ID
                ))
                .withMessage("finance.bucketAccount.alreadyUsed");

        verify(bucketAccountRepository, never()).deleteByBucketIdAndAccountIdAndUserGroupId(
                any(),
                any(),
                any()
        );
    }

    @Test
    void unlinkAccountShouldNotCheckUsageWhenLinkDoesNotExist() {
        givenCurrentOwner();

        given(bucketRepository.findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNull(
                BUCKET_ID,
                USER_GROUP_ID
        )).willReturn(Optional.of(bucket));

        given(accountRepository.findByAccountIdAndUserGroup_UserGroupId(
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(Optional.of(account));

        given(bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                BUCKET_ID,
                ACCOUNT_ID,
                USER_GROUP_ID
        )).willReturn(false);

        bucketAccountLinkService.unlinkAccount(
                CURRENT_USER_ID,
                BUCKET_ID,
                ACCOUNT_ID
        );

        verifyNoInteractions(bucketAccountUsageChecker);

        verify(bucketAccountRepository, never()).deleteByBucketIdAndAccountIdAndUserGroupId(
                any(),
                any(),
                any()
        );
    }

    private void givenCurrentOwner() {
        given(userRepository.findById(CURRENT_USER_ID))
                .willReturn(Optional.of(currentUser));

        given(currentUser.isUserIsEnabled())
                .willReturn(true);

        given(currentUser.isPendingDeletion())
                .willReturn(false);

        given(currentUser.getUserGroup())
                .willReturn(userGroup);

        given(userGroup.getUserGroupId())
                .willReturn(USER_GROUP_ID);

        given(currentUser.getUserRole())
                .willReturn(UserRole.OWNER);
    }
}