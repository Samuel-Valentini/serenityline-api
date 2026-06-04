package me.serenityline.api.user.service.deletion;

import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "serenityline.account-deletion.hard-deletion.enabled=false",
        "serenityline.account-deletion.hard-deletion.batch-size=50"
})
class AccountHardDeletionProcessorIntegrationTest extends IntegrationTestSupport {

    private static final int GRACE_DAYS = 30;
    @Autowired
    private AccountHardDeletionProcessor processor;
    @Autowired
    private AccountHardDeletionRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void processDueHardDeletionsShouldDeleteDueOwnerGroupAndAllLinkedFinanceAndAuthDataButKeepOtherGroups() {
        GroupFixture dueOwnerGroup = createRichGroup(
                "due-owner",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                null
        );

        GroupFixture otherGroup = createRichGroup(
                "other-group",
                null,
                null
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(1);
        assertThat(result.ownerGroupsDeleted()).isEqualTo(1);
        assertThat(result.collaboratorUsersDeleted()).isZero();
        assertThat(result.rowsDeleted()).isPositive();
        assertThat(result.deletedSubjects()).isEqualTo(1);

        assertOwnerGroupFullyDeleted(dueOwnerGroup);
        assertGroupFullyPresent(otherGroup);
    }

    @Test
    void processDueHardDeletionsShouldDeleteDueCollaboratorOnlyAndKeepOwnerGroupFinanceAndOwnerData() {
        GroupFixture group = createRichGroup(
                "due-collaborator",
                null,
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L)
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(1);
        assertThat(result.ownerGroupsDeleted()).isZero();
        assertThat(result.collaboratorUsersDeleted()).isEqualTo(1);
        assertThat(result.rowsDeleted()).isPositive();
        assertThat(result.deletedSubjects()).isEqualTo(1);

        assertThat(exists("user_groups", "user_group_id", group.userGroupId())).isTrue();
        assertThat(exists("users", "user_id", group.ownerId())).isTrue();
        assertThat(exists("users", "user_id", group.collaboratorId())).isFalse();

        assertPersonalDataGone(group.collaboratorId());
        assertPersonalDataStillPresent(group.ownerId());

        assertCollaboratorLinksGone(group.collaboratorId());
        assertOwnerLinksStillPresent(group.ownerId());

        assertSharedFinanceGraphStillPresentAfterCollaboratorDeletion(group.financeData());

        assertThat(countWhere("finance_reminder_notifications", "user_id = ?", group.collaboratorId()))
                .isZero();

        assertThat(countWhere("finance_reminder_notifications", "user_id = ?", group.ownerId()))
                .isEqualTo(1);

        assertThat(exists(
                "finance_reminder_notifications",
                "finance_reminder_notification_id",
                group.financeData().collaboratorReminderNotificationId()
        )).isFalse();

        assertThat(exists(
                "finance_reminder_notifications",
                "finance_reminder_notification_id",
                group.financeData().ownerReminderNotificationId()
        )).isTrue();
    }

    @Test
    void processDueHardDeletionsShouldIgnoreSoftDeletedUsersInsideGracePeriod() {
        GroupFixture group = createRichGroup(
                "inside-grace",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS - 1L),
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS - 1L)
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isZero();
        assertThat(result.ownerGroupsDeleted()).isZero();
        assertThat(result.collaboratorUsersDeleted()).isZero();
        assertThat(result.rowsDeleted()).isZero();

        assertGroupFullyPresent(group);
    }

    @Test
    void processDueHardDeletionsShouldIgnoreActiveUsersEvenWhenTheyAreOld() {
        GroupFixture group = createRichGroup(
                "active-old",
                null,
                null
        );

        makeUserCreatedLongAgo(group.ownerId(), 365);
        makeUserCreatedLongAgo(group.collaboratorId(), 365);

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isZero();
        assertThat(result.rowsDeleted()).isZero();

        assertGroupFullyPresent(group);
    }

    @Test
    void processDueHardDeletionsShouldTreatExactGraceBoundaryAsDue() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(GRACE_DAYS).withNano(0);

        GroupFixture group = createRichGroup(
                "exact-cutoff",
                cutoff,
                null
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(1);
        assertThat(result.ownerGroupsDeleted()).isEqualTo(1);

        assertOwnerGroupFullyDeleted(group);
    }

    @Test
    void processDueHardDeletionsShouldRespectBatchSizeAndPrioritizeOwnersBeforeCollaborators() {
        GroupFixture dueOwnerGroup = createRichGroup(
                "batch-owner",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                null
        );

        GroupFixture firstCollaboratorGroup = createRichGroup(
                "batch-collaborator-1",
                null,
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 3L)
        );

        GroupFixture secondCollaboratorGroup = createRichGroup(
                "batch-collaborator-2",
                null,
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 2L)
        );

        AccountHardDeletionProcessor batchTwoProcessor = new AccountHardDeletionProcessor(
                repository,
                clock,
                2
        );

        AccountHardDeletionResult firstResult = batchTwoProcessor.processDueHardDeletions();

        assertThat(firstResult.candidatesFound()).isEqualTo(2);
        assertThat(firstResult.ownerGroupsDeleted()).isEqualTo(1);
        assertThat(firstResult.collaboratorUsersDeleted()).isEqualTo(1);

        assertOwnerGroupFullyDeleted(dueOwnerGroup);

        assertThat(exists("users", "user_id", firstCollaboratorGroup.collaboratorId()))
                .isFalse();

        assertThat(exists("users", "user_id", secondCollaboratorGroup.collaboratorId()))
                .isTrue();

        AccountHardDeletionResult secondResult = batchTwoProcessor.processDueHardDeletions();

        assertThat(secondResult.candidatesFound()).isEqualTo(1);
        assertThat(secondResult.ownerGroupsDeleted()).isZero();
        assertThat(secondResult.collaboratorUsersDeleted()).isEqualTo(1);

        assertThat(exists("users", "user_id", secondCollaboratorGroup.collaboratorId()))
                .isFalse();
    }

    @Test
    void processDueHardDeletionsShouldDeleteSuperCollaboratorAndViewerCollaboratorAsCollaborators() {
        UUID groupId = createUserGroup("non-standard-collaborators");

        UUID ownerId = createUser(
                groupId,
                "OWNER",
                "non-standard-owner",
                null
        );

        UUID superCollaboratorId = createUser(
                groupId,
                "SUPER_COLLABORATOR",
                "non-standard-super",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L)
        );

        UUID viewerCollaboratorId = createUser(
                groupId,
                "VIEWER_COLLABORATOR",
                "non-standard-viewer",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L)
        );

        createPersonalData(ownerId, "non-standard-owner");
        createPersonalData(superCollaboratorId, "non-standard-super");
        createPersonalData(viewerCollaboratorId, "non-standard-viewer");

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(2);
        assertThat(result.ownerGroupsDeleted()).isZero();
        assertThat(result.collaboratorUsersDeleted()).isEqualTo(2);

        assertThat(exists("user_groups", "user_group_id", groupId)).isTrue();
        assertThat(exists("users", "user_id", ownerId)).isTrue();
        assertThat(exists("users", "user_id", superCollaboratorId)).isFalse();
        assertThat(exists("users", "user_id", viewerCollaboratorId)).isFalse();

        assertPersonalDataGone(superCollaboratorId);
        assertPersonalDataGone(viewerCollaboratorId);
        assertPersonalDataStillPresent(ownerId);
    }

    @Test
    void repositoryFindDueCandidatesShouldReturnOnlyDuePendingDeletionUsersFromDatabase() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(GRACE_DAYS).withNano(0);

        UUID dueGroupId = createUserGroup("repository-due");
        UUID dueOwnerId = createUser(
                dueGroupId,
                "OWNER",
                "repository-due-owner",
                cutoff
        );

        UUID recentGroupId = createUserGroup("repository-recent");
        UUID recentOwnerId = createUser(
                recentGroupId,
                "OWNER",
                "repository-recent-owner",
                cutoff.plusSeconds(1)
        );

        UUID activeGroupId = createUserGroup("repository-active");
        UUID activeOwnerId = createUser(
                activeGroupId,
                "OWNER",
                "repository-active-owner",
                null
        );

        var candidates = repository.findDueCandidatesForUpdate(cutoff, 10);

        assertThat(candidates)
                .extracting(AccountHardDeletionCandidate::userId)
                .contains(dueOwnerId)
                .doesNotContain(recentOwnerId, activeOwnerId);
    }

    @Test
    void cleanupWorkerShouldNotBeRegisteredWhenHardDeletionIsDisabled() {
        assertThat(applicationContext.getBeansOfType(AccountHardDeletionWorker.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AccountHardDeletionSchedulingConfig.class)).isEmpty();
    }

    @Test
    void processorConstructorShouldRejectBatchSizeBelowMinimum() {
        assertThatThrownBy(() -> new AccountHardDeletionProcessor(
                repository,
                clock,
                0
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("accountHardDeletion.batchSize.invalid");
    }

    @Test
    void processorConstructorShouldRejectBatchSizeAboveMaximum() {
        assertThatThrownBy(() -> new AccountHardDeletionProcessor(
                repository,
                clock,
                10_001
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("accountHardDeletion.batchSize.invalid");
    }

    @Test
    void processorConstructorShouldAcceptBoundaryBatchSizes() {
        assertThatCode(() -> new AccountHardDeletionProcessor(
                repository,
                clock,
                1
        )).doesNotThrowAnyException();

        assertThatCode(() -> new AccountHardDeletionProcessor(
                repository,
                clock,
                10_000
        )).doesNotThrowAnyException();
    }

    @Test
    void processDueHardDeletionsShouldReturnEmptyResultWhenThereAreNoPendingDeletionUsers() {
        GroupFixture group = createRichGroup(
                "no-pending-users",
                null,
                null
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isZero();
        assertThat(result.ownerGroupsDeleted()).isZero();
        assertThat(result.collaboratorUsersDeleted()).isZero();
        assertThat(result.rowsDeleted()).isZero();
        assertThat(result.deletedSubjects()).isZero();
        assertThat(result.hasWork()).isFalse();

        assertGroupFullyPresent(group);
    }

    @Test
    void processDueHardDeletionsShouldBeIdempotentAfterOwnerGroupWasAlreadyDeleted() {
        GroupFixture group = createRichGroup(
                "idempotent-owner",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                null
        );

        AccountHardDeletionResult firstResult = processor.processDueHardDeletions();

        assertThat(firstResult.candidatesFound()).isEqualTo(1);
        assertThat(firstResult.ownerGroupsDeleted()).isEqualTo(1);
        assertThat(firstResult.rowsDeleted()).isPositive();

        assertOwnerGroupFullyDeleted(group);

        AccountHardDeletionResult secondResult = processor.processDueHardDeletions();

        assertThat(secondResult.candidatesFound()).isZero();
        assertThat(secondResult.ownerGroupsDeleted()).isZero();
        assertThat(secondResult.collaboratorUsersDeleted()).isZero();
        assertThat(secondResult.rowsDeleted()).isZero();
        assertThat(secondResult.hasWork()).isFalse();
    }

    @Test
    void processDueHardDeletionsShouldNotDeleteUserDeletedJustAfterCutoff() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock)
                .minusDays(User.SOFT_DELETE_GRACE_PERIOD_DAYS);

        OffsetDateTime justAfterCutoff = cutoff.plusSeconds(1);

        GroupFixture group = createRichGroup(
                "just-after-cutoff",
                justAfterCutoff,
                null
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isZero();
        assertThat(result.rowsDeleted()).isZero();

        assertGroupFullyPresent(group);
    }

    @Test
    void processDueHardDeletionsShouldDeleteDueDisabledPendingDeletionUser() {
        GroupFixture group = createRichGroup(
                "disabled-due-owner",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                null
        );

        disableUser(group.ownerId());

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(1);
        assertThat(result.ownerGroupsDeleted()).isEqualTo(1);
        assertThat(result.rowsDeleted()).isPositive();

        assertOwnerGroupFullyDeleted(group);
    }

    @Test
    void processDueHardDeletionsShouldNotDeleteDisabledUserWhenNotPendingDeletion() {
        GroupFixture group = createRichGroup(
                "disabled-not-pending",
                null,
                null
        );

        disableUser(group.ownerId());
        disableUser(group.collaboratorId());

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isZero();
        assertThat(result.rowsDeleted()).isZero();

        assertGroupFullyPresent(group);
    }

    @Test
    void processDueHardDeletionsShouldKeepNullUserEmailOutboxAndNullUserLoginAttempts() {
        UUID orphanEmailOutboxId = createEmailOutboxWithoutUser("orphan-outbox");
        UUID nullUserLoginAttemptId = createNullUserLoginAttempt("null-user-rate-limited");

        GroupFixture group = createRichGroup(
                "owner-with-orphans",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                null
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.ownerGroupsDeleted()).isEqualTo(1);

        assertOwnerGroupFullyDeleted(group);

        assertThat(exists("email_outbox", "email_outbox_id", orphanEmailOutboxId)).isTrue();
        assertThat(exists("auth_login_attempts", "auth_login_attempt_id", nullUserLoginAttemptId)).isTrue();

        assertThat(countWhere("email_outbox", "email_outbox_id = ? and user_id is null", orphanEmailOutboxId))
                .isEqualTo(1);

        assertThat(countWhere("auth_login_attempts", "auth_login_attempt_id = ? and user_id is null", nullUserLoginAttemptId))
                .isEqualTo(1);
    }

    @Test
    void processDueHardDeletionsShouldKeepOtherGroupEvenWhenOtherGroupHasDueCollaboratorAndBatchDeletesOnlyOwnerGroup() {
        GroupFixture dueOwnerGroup = createRichGroup(
                "owner-first-batch",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                null
        );

        GroupFixture dueCollaboratorGroup = createRichGroup(
                "collaborator-second-batch",
                null,
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 10L)
        );

        AccountHardDeletionProcessor batchOneProcessor = new AccountHardDeletionProcessor(
                repository,
                clock,
                1
        );

        AccountHardDeletionResult firstResult = batchOneProcessor.processDueHardDeletions();

        assertThat(firstResult.candidatesFound()).isEqualTo(1);
        assertThat(firstResult.ownerGroupsDeleted()).isEqualTo(1);
        assertThat(firstResult.collaboratorUsersDeleted()).isZero();

        assertOwnerGroupFullyDeleted(dueOwnerGroup);

        assertThat(exists("user_groups", "user_group_id", dueCollaboratorGroup.userGroupId())).isTrue();
        assertThat(exists("users", "user_id", dueCollaboratorGroup.ownerId())).isTrue();
        assertThat(exists("users", "user_id", dueCollaboratorGroup.collaboratorId())).isTrue();

        AccountHardDeletionResult secondResult = batchOneProcessor.processDueHardDeletions();

        assertThat(secondResult.candidatesFound()).isEqualTo(1);
        assertThat(secondResult.ownerGroupsDeleted()).isZero();
        assertThat(secondResult.collaboratorUsersDeleted()).isEqualTo(1);

        assertThat(exists("user_groups", "user_group_id", dueCollaboratorGroup.userGroupId())).isTrue();
        assertThat(exists("users", "user_id", dueCollaboratorGroup.ownerId())).isTrue();
        assertThat(exists("users", "user_id", dueCollaboratorGroup.collaboratorId())).isFalse();

        assertSharedFinanceGraphStillPresentAfterCollaboratorDeletion(dueCollaboratorGroup.financeData());
    }

    @Test
    void processDueHardDeletionsShouldHandleOwnerAndCollaboratorDueInSameGroupWithoutDoubleDeleting() {
        GroupFixture group = createRichGroup(
                "owner-and-collaborator-due",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 2L)
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(2);
        assertThat(result.ownerGroupsDeleted()).isEqualTo(1);
        assertThat(result.collaboratorUsersDeleted()).isZero();
        assertThat(result.deletedSubjects()).isEqualTo(1);
        assertThat(result.rowsDeleted()).isPositive();

        assertOwnerGroupFullyDeleted(group);

        AccountHardDeletionResult secondResult = processor.processDueHardDeletions();

        assertThat(secondResult.candidatesFound()).isZero();
        assertThat(secondResult.rowsDeleted()).isZero();
    }

    @Test
    void processDueHardDeletionsShouldKeepOwnerNotificationForSameRecurringTransactionWhenCollaboratorIsDeleted() {
        GroupFixture group = createRichGroup(
                "same-recurring-notifications",
                null,
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L)
        );

        UUID extraOwnerEmailOutboxId = createAdditionalEmailOutboxForUser(
                group.ownerId(),
                "owner-same-recurring-reminder"
        );

        UUID ownerRecurringReminderNotificationId = createRecurringReminderNotification(
                group.ownerId(),
                group.userGroupId(),
                group.financeData().recurringTransactionId(),
                extraOwnerEmailOutboxId,
                "owner same recurring reminder"
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(1);
        assertThat(result.collaboratorUsersDeleted()).isEqualTo(1);

        assertThat(exists(
                "finance_reminder_notifications",
                "finance_reminder_notification_id",
                ownerRecurringReminderNotificationId
        )).isTrue();

        assertThat(exists(
                "finance_reminder_notifications",
                "finance_reminder_notification_id",
                group.financeData().collaboratorReminderNotificationId()
        )).isFalse();

        assertSharedFinanceGraphStillPresentAfterCollaboratorDeletion(group.financeData());
    }

    @Test
    void processDueHardDeletionsShouldKeepFinanceDataWhenDeletedCollaboratorWasCategoryCreator() {
        GroupFixture group = createRichGroup(
                "collaborator-category-creator",
                null,
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L)
        );

        jdbcTemplate.update(
                """
                        update categories
                        set category_created_by_user_id = ?
                        where category_id = ?
                        """,
                group.collaboratorId(),
                group.financeData().categoryId()
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.collaboratorUsersDeleted()).isEqualTo(1);

        assertThat(exists("users", "user_id", group.collaboratorId())).isFalse();
        assertThat(exists("categories", "category_id", group.financeData().categoryId())).isTrue();

        UUID categoryCreatedByUserId = jdbcTemplate.queryForObject(
                """
                        select category_created_by_user_id
                        from categories
                        where category_id = ?
                        """,
                UUID.class,
                group.financeData().categoryId()
        );

        assertThat(categoryCreatedByUserId).isEqualTo(group.collaboratorId());

        assertSharedFinanceGraphStillPresentAfterCollaboratorDeletion(group.financeData());
    }

    @Test
    void processDueHardDeletionsShouldNotDeleteFinancialPrioritiesWhenOwnerGroupIsDeleted() {
        long financialPrioritiesBefore = countAll("financial_priorities");

        GroupFixture group = createRichGroup(
                "keep-financial-priorities",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                null
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.ownerGroupsDeleted()).isEqualTo(1);
        assertOwnerGroupFullyDeleted(group);

        long financialPrioritiesAfter = countAll("financial_priorities");

        assertThat(financialPrioritiesAfter).isEqualTo(financialPrioritiesBefore);
        assertThat(financialPrioritiesAfter).isPositive();
    }

    @Test
    void hardDeleteCollaboratorUserShouldReturnZeroAndNotDeleteAnythingForUnknownUserId() {
        GroupFixture group = createRichGroup(
                "unknown-user-direct-repository-call",
                null,
                null
        );

        int rowsDeleted = repository.hardDeleteCollaboratorUser(UUID.randomUUID());

        assertThat(rowsDeleted).isZero();

        assertGroupFullyPresent(group);
    }

    @Test
    void hardDeleteOwnerGroupShouldReturnZeroAndNotDeleteAnythingForUnknownGroupId() {
        GroupFixture group = createRichGroup(
                "unknown-group-direct-repository-call",
                null,
                null
        );

        int rowsDeleted = repository.hardDeleteOwnerGroup(UUID.randomUUID());

        assertThat(rowsDeleted).isZero();

        assertGroupFullyPresent(group);
    }

    @Test
    void repositoryShouldRejectInvalidCandidateSearchArguments() {
        assertThatThrownBy(() -> repository.findDueCandidatesForUpdate(null, 10))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("accountHardDeletion.cutoff.required");

        assertThatThrownBy(() -> repository.findDueCandidatesForUpdate(OffsetDateTime.now(clock), 0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("accountHardDeletion.limit.invalid");
    }

    @Test
    void repositoryShouldRejectNullDeleteArguments() {
        assertThatThrownBy(() -> repository.hardDeleteCollaboratorUser(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId");

        assertThatThrownBy(() -> repository.hardDeleteOwnerGroup(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userGroupId");
    }

    @Test
    void processDueHardDeletionsShouldIgnoreUsersWithFutureDeletedAt() {
        GroupFixture group = createRichGroup(
                "future-deleted-at",
                OffsetDateTime.now(clock).plusDays(1),
                OffsetDateTime.now(clock).plusDays(1)
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isZero();
        assertThat(result.rowsDeleted()).isZero();

        assertGroupFullyPresent(group);
    }

    @Test
    void processDueHardDeletionsShouldDeleteDueCollaboratorButKeepGroupWhenOwnerIsPendingDeletionInsideGracePeriod() {
        GroupFixture group = createRichGroup(
                "owner-inside-grace-collaborator-due",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS - 1L),
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L)
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(1);
        assertThat(result.ownerGroupsDeleted()).isZero();
        assertThat(result.collaboratorUsersDeleted()).isEqualTo(1);

        assertThat(exists("user_groups", "user_group_id", group.userGroupId())).isTrue();
        assertThat(exists("users", "user_id", group.ownerId())).isTrue();
        assertThat(exists("users", "user_id", group.collaboratorId())).isFalse();

        assertSharedFinanceGraphStillPresentAfterCollaboratorDeletion(group.financeData());
    }

    @Test
    void processDueHardDeletionsShouldDeleteWholeGroupWhenOwnerIsDueEvenIfCollaboratorIsInsideGracePeriod() {
        GroupFixture group = createRichGroup(
                "owner-due-collaborator-inside-grace",
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L),
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS - 1L)
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(1);
        assertThat(result.ownerGroupsDeleted()).isEqualTo(1);
        assertThat(result.collaboratorUsersDeleted()).isZero();

        assertOwnerGroupFullyDeleted(group);
    }

    @Test
    void processDueHardDeletionsShouldNotTouchAnotherGroupWhenDeletingDueCollaborator() {
        GroupFixture targetGroup = createRichGroup(
                "target-collaborator-delete",
                null,
                OffsetDateTime.now(clock).minusDays(GRACE_DAYS + 1L)
        );

        GroupFixture untouchedGroup = createRichGroup(
                "untouched-group",
                null,
                null
        );

        AccountHardDeletionResult result = processor.processDueHardDeletions();

        assertThat(result.candidatesFound()).isEqualTo(1);
        assertThat(result.collaboratorUsersDeleted()).isEqualTo(1);

        assertThat(exists("users", "user_id", targetGroup.collaboratorId())).isFalse();
        assertSharedFinanceGraphStillPresentAfterCollaboratorDeletion(targetGroup.financeData());

        assertGroupFullyPresent(untouchedGroup);
    }

    @Test
    void hardDeletionWorkerShouldNotBeRegisteredWhenDisabled() {
        assertThat(applicationContext.getBeansOfType(AccountHardDeletionWorker.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AccountHardDeletionSchedulingConfig.class)).isEmpty();
    }

    private GroupFixture createRichGroup(
            String suffix,
            OffsetDateTime ownerDeletedAt,
            OffsetDateTime collaboratorDeletedAt
    ) {
        UUID groupId = createUserGroup(suffix);

        UUID ownerId = createUser(
                groupId,
                "OWNER",
                suffix + "-owner",
                ownerDeletedAt
        );

        UUID collaboratorId = createUser(
                groupId,
                "COLLABORATOR",
                suffix + "-collaborator",
                collaboratorDeletedAt
        );

        PersonalData ownerPersonalData = createPersonalData(
                ownerId,
                suffix + "-owner"
        );

        PersonalData collaboratorPersonalData = createPersonalData(
                collaboratorId,
                suffix + "-collaborator"
        );

        FinanceData financeData = createFinanceData(
                groupId,
                ownerId,
                collaboratorId,
                ownerPersonalData.emailOutboxId(),
                collaboratorPersonalData.emailOutboxId(),
                suffix
        );

        return new GroupFixture(
                groupId,
                ownerId,
                collaboratorId,
                ownerPersonalData,
                collaboratorPersonalData,
                financeData
        );
    }

    private UUID createUserGroup(String suffix) {
        return insertReturningUuid(
                """
                        insert into user_groups (
                            user_group_name
                        )
                        values (?)
                        returning user_group_id
                        """,
                "Hard deletion test group " + suffix + " " + UUID.randomUUID()
        );
    }

    private UUID createUser(
            UUID userGroupId,
            String role,
            String suffix,
            OffsetDateTime deletedAt
    ) {
        OffsetDateTime createdAt = deletedAt == null
                ? OffsetDateTime.now(clock).minusDays(60)
                : deletedAt.minusDays(10);

        OffsetDateTime updatedAt = deletedAt == null
                ? OffsetDateTime.now(clock).minusDays(1)
                : deletedAt;

        String normalizedSuffix = suffix
                .toLowerCase()
                .replace("_", "-")
                .replace(" ", "-");

        return insertReturningUuid(
                """
                        insert into users (
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_password_hash,
                            user_created_at,
                            user_updated_at,
                            user_deleted_at,
                            user_is_enabled
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, true)
                        returning user_id
                        """,
                "Hard Deletion Test " + suffix,
                "hard-deletion-" + normalizedSuffix + "-" + UUID.randomUUID() + "@example.com",
                userGroupId,
                role,
                "{bcrypt}not-a-real-test-hash",
                createdAt,
                updatedAt,
                deletedAt
        );
    }

    private PersonalData createPersonalData(UUID userId, String suffix) {
        UUID emailOutboxId = insertReturningUuid(
                """
                        insert into email_outbox (
                            user_id,
                            recipient_email,
                            email_type,
                            encryption_key_id,
                            subject_encrypted,
                            subject_iv,
                            subject_tag,
                            body_text_encrypted,
                            body_text_iv,
                            body_text_tag,
                            delete_body_after_send,
                            email_status
                        )
                        values (?, ?, 'GENERIC', 'test-key', ?, ?, ?, ?, ?, ?, false, 'PENDING')
                        returning email_outbox_id
                        """,
                userId,
                "recipient-" + suffix + "-" + UUID.randomUUID() + "@example.com",
                bytes(1),
                bytes(12),
                bytes(16),
                bytes(1),
                bytes(12),
                bytes(16)
        );

        UUID authActionTokenId = insertReturningUuid(
                """
                        insert into auth_action_tokens (
                            user_id,
                            auth_action_token_hash,
                            auth_action_token_type,
                            auth_action_created_at,
                            auth_action_expires_at
                        )
                        values (?, ?, 'RESTORE_ACCOUNT', ?, ?)
                        returning auth_action_token_id
                        """,
                userId,
                "hard-deletion-action-token-" + suffix + "-" + UUID.randomUUID(),
                OffsetDateTime.now(clock).minusMinutes(5),
                OffsetDateTime.now(clock).plusMinutes(55)
        );

        UUID userSessionId = insertReturningUuid(
                """
                        insert into user_sessions (
                            user_id,
                            session_created_at,
                            session_last_seen_at,
                            session_expires_at,
                            ip_address_hash,
                            user_agent,
                            device_label
                        )
                        values (?, ?, ?, ?, ?, ?, ?)
                        returning user_session_id
                        """,
                userId,
                OffsetDateTime.now(clock).minusHours(2),
                OffsetDateTime.now(clock).minusHours(1),
                OffsetDateTime.now(clock).plusDays(30),
                "ip-hash-" + suffix,
                "JUnit hard deletion browser",
                "JUnit hard deletion device " + suffix
        );

        UUID refreshTokenId = insertReturningUuid(
                """
                        insert into refresh_tokens (
                            user_id,
                            user_session_id,
                            refresh_token_hash,
                            refresh_token_created_at,
                            refresh_token_expires_at
                        )
                        values (?, ?, ?, ?, ?)
                        returning refresh_token_id
                        """,
                userId,
                userSessionId,
                "hard-deletion-refresh-token-" + suffix + "-" + UUID.randomUUID(),
                OffsetDateTime.now(clock).minusHours(1),
                OffsetDateTime.now(clock).plusDays(30)
        );

        UUID authLoginAttemptId = insertReturningUuid(
                """
                        insert into auth_login_attempts (
                            user_id,
                            email_hash,
                            ip_address_hash,
                            login_successful
                        )
                        values (?, ?, ?, true)
                        returning auth_login_attempt_id
                        """,
                userId,
                "email-hash-" + suffix + "-" + UUID.randomUUID(),
                "ip-hash-" + suffix + "-" + UUID.randomUUID()
        );

        return new PersonalData(
                emailOutboxId,
                authActionTokenId,
                userSessionId,
                refreshTokenId,
                authLoginAttemptId
        );
    }

    private FinanceData createFinanceData(
            UUID userGroupId,
            UUID ownerId,
            UUID collaboratorId,
            UUID ownerEmailOutboxId,
            UUID collaboratorEmailOutboxId,
            String suffix
    ) {
        UUID categoryId = insertReturningUuid(
                """
                        insert into categories (
                            user_group_id,
                            category_created_by_user_id,
                            category_current_name
                        )
                        values (?, ?, ?)
                        returning category_id
                        """,
                userGroupId,
                ownerId,
                "Hard deletion category " + suffix + " " + UUID.randomUUID()
        );

        jdbcTemplate.update(
                """
                        insert into category_status_history (
                            category_id,
                            category_is_active
                        )
                        values (?, true)
                        """,
                categoryId
        );

        jdbcTemplate.update(
                """
                        insert into category_details_history (
                            category_id,
                            category_name,
                            category_description
                        )
                        values (?, ?, ?)
                        """,
                categoryId,
                "Hard deletion category " + suffix,
                "Category created by hard deletion test"
        );

        UUID accountId = insertReturningUuid(
                """
                        insert into accounts (
                            account_name,
                            currency,
                            opening_balance,
                            opening_balance_date,
                            user_group_id
                        )
                        values (?, 'EUR', 1000.00, ?, ?)
                        returning account_id
                        """,
                "Hard deletion account " + suffix + " " + UUID.randomUUID(),
                LocalDate.now(clock).minusDays(10),
                userGroupId
        );

        linkAccountToUser(accountId, ownerId, userGroupId);
        linkAccountToUser(accountId, collaboratorId, userGroupId);

        UUID creditCardId = insertReturningUuid(
                """
                        insert into credit_cards (
                            credit_card_name,
                            credit_card_charge_day,
                            account_id,
                            user_group_id
                        )
                        values (?, 15, ?, ?)
                        returning credit_card_id
                        """,
                "Hard deletion card " + suffix + " " + UUID.randomUUID(),
                accountId,
                userGroupId
        );

        UUID bucketId = insertReturningUuid(
                """
                        insert into buckets (
                            bucket_name,
                            user_group_id
                        )
                        values (?, ?)
                        returning bucket_id
                        """,
                "Hard deletion bucket " + suffix + " " + UUID.randomUUID(),
                userGroupId
        );

        jdbcTemplate.update(
                """
                        insert into buckets_accounts (
                            bucket_id,
                            account_id,
                            user_group_id
                        )
                        values (?, ?, ?)
                        """,
                bucketId,
                accountId,
                userGroupId
        );

        UUID simulationGroupId = insertReturningUuid(
                """
                        insert into simulation_groups (
                            user_group_id,
                            simulation_group_name
                        )
                        values (?, ?)
                        returning simulation_group_id
                        """,
                userGroupId,
                "Hard deletion simulation " + suffix + " " + UUID.randomUUID()
        );

        jdbcTemplate.update(
                """
                        insert into simulation_groups_accounts (
                            simulation_group_id,
                            account_id,
                            user_group_id
                        )
                        values (?, ?, ?)
                        """,
                simulationGroupId,
                accountId,
                userGroupId
        );

        UUID recurringTransactionId = insertReturningUuid(
                """
                        insert into recurring_transactions (
                            recurring_transaction_first_payment_date,
                            user_group_id
                        )
                        values (?, ?)
                        returning recurring_transaction_id
                        """,
                LocalDate.now(clock).plusDays(5),
                userGroupId
        );

        jdbcTemplate.update(
                """
                        insert into recurring_transaction_history (
                            recurring_transaction_id,
                            effective_from,
                            day_of_unit,
                            recurrence_interval,
                            recurrence_unit,
                            payment_amount
                        )
                        values (?, ?, 1, 1, 'MONTH', -100.00)
                        """,
                recurringTransactionId,
                LocalDate.now(clock)
        );

        UUID financialPriorityId = jdbcTemplate.queryForObject(
                """
                        select financial_priority_id
                        from financial_priorities
                        where financial_priority_name = 'ESSENTIAL'
                        """,
                UUID.class
        );

        jdbcTemplate.update(
                """
                        insert into recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_credit_card_id,
                            linked_bucket_id,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                recurringTransactionId,
                "Hard deletion recurring " + suffix,
                categoryId,
                financialPriorityId,
                accountId,
                creditCardId,
                bucketId,
                LocalDate.now(clock),
                userGroupId
        );

        linkRecurringTransactionToUser(recurringTransactionId, ownerId, userGroupId);
        linkRecurringTransactionToUser(recurringTransactionId, collaboratorId, userGroupId);

        UUID userEnteredTransactionId = insertReturningUuid(
                """
                        insert into transactions (
                            transaction_description,
                            transaction_amount,
                            category_id,
                            transaction_charge_date,
                            transaction_is_confirmed,
                            account_id,
                            credit_card_id,
                            bucket_id,
                            transaction_is_simulated,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        values (?, -50.00, ?, ?, true, ?, ?, ?, false, true, true, 7, ?)
                        returning transaction_id
                        """,
                "Hard deletion user transaction " + suffix,
                categoryId,
                LocalDate.now(clock).plusDays(10),
                accountId,
                creditCardId,
                bucketId,
                userGroupId
        );

        UUID recurringOccurrenceTransactionId = insertReturningUuid(
                """
                        insert into transactions (
                            transaction_description,
                            transaction_amount,
                            category_id,
                            transaction_charge_date,
                            transaction_is_confirmed,
                            account_id,
                            credit_card_id,
                            bucket_id,
                            transaction_is_simulated,
                            transaction_is_user_entered,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        values (?, -100.00, ?, ?, true, ?, ?, ?, false, false, ?, ?, ?, true, 7, ?)
                        returning transaction_id
                        """,
                "Hard deletion recurring occurrence " + suffix,
                categoryId,
                LocalDate.now(clock).plusDays(15),
                accountId,
                creditCardId,
                bucketId,
                recurringTransactionId,
                LocalDate.now(clock).plusDays(15),
                OffsetDateTime.now(clock),
                userGroupId
        );

        linkTransactionToUser(userEnteredTransactionId, ownerId, userGroupId);
        linkTransactionToUser(userEnteredTransactionId, collaboratorId, userGroupId);
        linkTransactionToUser(recurringOccurrenceTransactionId, ownerId, userGroupId);
        linkTransactionToUser(recurringOccurrenceTransactionId, collaboratorId, userGroupId);

        UUID ownerReminderNotificationId = insertReturningUuid(
                """
                        insert into finance_reminder_notifications (
                            user_id,
                            user_group_id,
                            transaction_id,
                            charge_date,
                            notified_description,
                            notified_amount,
                            notified_currency,
                            reminder_date,
                            email_outbox_id
                        )
                        values (?, ?, ?, ?, ?, -50.00, 'EUR', ?, ?)
                        returning finance_reminder_notification_id
                        """,
                ownerId,
                userGroupId,
                userEnteredTransactionId,
                LocalDate.now(clock).plusDays(10),
                "Owner reminder " + suffix,
                LocalDate.now(clock).plusDays(3),
                ownerEmailOutboxId
        );

        UUID collaboratorReminderNotificationId = insertReturningUuid(
                """
                        insert into finance_reminder_notifications (
                            user_id,
                            user_group_id,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            charge_date,
                            notified_description,
                            notified_amount,
                            notified_currency,
                            reminder_date,
                            email_outbox_id
                        )
                        values (?, ?, ?, ?, ?, ?, -100.00, 'EUR', ?, ?)
                        returning finance_reminder_notification_id
                        """,
                collaboratorId,
                userGroupId,
                recurringTransactionId,
                LocalDate.now(clock).plusDays(15),
                LocalDate.now(clock).plusDays(15),
                "Collaborator reminder " + suffix,
                LocalDate.now(clock).plusDays(8),
                collaboratorEmailOutboxId
        );

        return new FinanceData(
                categoryId,
                accountId,
                creditCardId,
                bucketId,
                simulationGroupId,
                recurringTransactionId,
                userEnteredTransactionId,
                recurringOccurrenceTransactionId,
                ownerReminderNotificationId,
                collaboratorReminderNotificationId
        );
    }

    private void linkAccountToUser(
            UUID accountId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update(
                """
                        insert into accounts_users (
                            account_id,
                            user_id,
                            user_group_id
                        )
                        values (?, ?, ?)
                        """,
                accountId,
                userId,
                userGroupId
        );
    }

    private void linkRecurringTransactionToUser(
            UUID recurringTransactionId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update(
                """
                        insert into recurring_transactions_users (
                            recurring_transaction_id,
                            user_id,
                            user_group_id
                        )
                        values (?, ?, ?)
                        """,
                recurringTransactionId,
                userId,
                userGroupId
        );
    }

    private void linkTransactionToUser(
            UUID transactionId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update(
                """
                        insert into transactions_users (
                            transaction_id,
                            user_id,
                            user_group_id
                        )
                        values (?, ?, ?)
                        """,
                transactionId,
                userId,
                userGroupId
        );
    }

    private void makeUserCreatedLongAgo(UUID userId, int daysAgo) {
        jdbcTemplate.update(
                """
                        update users
                        set user_created_at = ?,
                            user_updated_at = ?
                        where user_id = ?
                        """,
                OffsetDateTime.now(clock).minusDays(daysAgo),
                OffsetDateTime.now(clock).minusDays(daysAgo - 1L),
                userId
        );
    }

    private void assertOwnerGroupFullyDeleted(GroupFixture group) {
        assertThat(exists("user_groups", "user_group_id", group.userGroupId())).isFalse();

        assertThat(exists("users", "user_id", group.ownerId())).isFalse();
        assertThat(exists("users", "user_id", group.collaboratorId())).isFalse();

        assertPersonalDataGone(group.ownerId());
        assertPersonalDataGone(group.collaboratorId());

        assertFinanceGraphDeleted(group.financeData());
    }

    private void assertGroupFullyPresent(GroupFixture group) {
        assertThat(exists("user_groups", "user_group_id", group.userGroupId())).isTrue();

        assertThat(exists("users", "user_id", group.ownerId())).isTrue();
        assertThat(exists("users", "user_id", group.collaboratorId())).isTrue();

        assertPersonalDataStillPresent(group.ownerId());
        assertPersonalDataStillPresent(group.collaboratorId());

        assertFinanceGraphStillPresent(group.financeData());
    }

    private void assertPersonalDataGone(UUID userId) {
        assertThat(countWhere("email_outbox", "user_id = ?", userId)).isZero();
        assertThat(countWhere("auth_login_attempts", "user_id = ?", userId)).isZero();
        assertThat(countWhere("auth_action_tokens", "user_id = ?", userId)).isZero();
        assertThat(countWhere("refresh_tokens", "user_id = ?", userId)).isZero();
        assertThat(countWhere("user_sessions", "user_id = ?", userId)).isZero();
    }

    private void assertPersonalDataStillPresent(UUID userId) {
        assertThat(countWhere("email_outbox", "user_id = ?", userId)).isPositive();
        assertThat(countWhere("auth_login_attempts", "user_id = ?", userId)).isPositive();
        assertThat(countWhere("auth_action_tokens", "user_id = ?", userId)).isPositive();
        assertThat(countWhere("refresh_tokens", "user_id = ?", userId)).isPositive();
        assertThat(countWhere("user_sessions", "user_id = ?", userId)).isPositive();
    }

    private void assertCollaboratorLinksGone(UUID collaboratorId) {
        assertThat(countWhere("accounts_users", "user_id = ?", collaboratorId)).isZero();
        assertThat(countWhere("transactions_users", "user_id = ?", collaboratorId)).isZero();
        assertThat(countWhere("recurring_transactions_users", "user_id = ?", collaboratorId)).isZero();
    }

    private void assertOwnerLinksStillPresent(UUID ownerId) {
        assertThat(countWhere("accounts_users", "user_id = ?", ownerId)).isPositive();
        assertThat(countWhere("transactions_users", "user_id = ?", ownerId)).isPositive();
        assertThat(countWhere("recurring_transactions_users", "user_id = ?", ownerId)).isPositive();
    }

    private void assertFinanceGraphDeleted(FinanceData financeData) {
        assertThat(exists("finance_reminder_notifications", "finance_reminder_notification_id", financeData.ownerReminderNotificationId())).isFalse();
        assertThat(exists("finance_reminder_notifications", "finance_reminder_notification_id", financeData.collaboratorReminderNotificationId())).isFalse();

        assertThat(exists("transactions", "transaction_id", financeData.userEnteredTransactionId())).isFalse();
        assertThat(exists("transactions", "transaction_id", financeData.recurringOccurrenceTransactionId())).isFalse();

        assertThat(exists("transactions_users", "transaction_id", financeData.userEnteredTransactionId())).isFalse();
        assertThat(exists("transactions_users", "transaction_id", financeData.recurringOccurrenceTransactionId())).isFalse();

        assertThat(exists("recurring_transactions_users", "recurring_transaction_id", financeData.recurringTransactionId())).isFalse();
        assertThat(exists("recurring_transaction_details_history", "recurring_transaction_id", financeData.recurringTransactionId())).isFalse();
        assertThat(exists("recurring_transaction_history", "recurring_transaction_id", financeData.recurringTransactionId())).isFalse();
        assertThat(exists("recurring_transactions", "recurring_transaction_id", financeData.recurringTransactionId())).isFalse();

        assertThat(exists("simulation_groups_accounts", "simulation_group_id", financeData.simulationGroupId())).isFalse();
        assertThat(exists("simulation_groups", "simulation_group_id", financeData.simulationGroupId())).isFalse();

        assertThat(exists("buckets_accounts", "bucket_id", financeData.bucketId())).isFalse();
        assertThat(exists("credit_cards", "credit_card_id", financeData.creditCardId())).isFalse();
        assertThat(exists("buckets", "bucket_id", financeData.bucketId())).isFalse();
        assertThat(exists("accounts", "account_id", financeData.accountId())).isFalse();

        assertThat(exists("category_status_history", "category_id", financeData.categoryId())).isFalse();
        assertThat(exists("category_details_history", "category_id", financeData.categoryId())).isFalse();
        assertThat(exists("categories", "category_id", financeData.categoryId())).isFalse();
    }

    private void assertFinanceGraphStillPresent(FinanceData financeData) {
        assertThat(exists("finance_reminder_notifications", "finance_reminder_notification_id", financeData.ownerReminderNotificationId())).isTrue();
        assertThat(exists("finance_reminder_notifications", "finance_reminder_notification_id", financeData.collaboratorReminderNotificationId())).isTrue();

        assertThat(exists("transactions", "transaction_id", financeData.userEnteredTransactionId())).isTrue();
        assertThat(exists("transactions", "transaction_id", financeData.recurringOccurrenceTransactionId())).isTrue();

        assertThat(exists("transactions_users", "transaction_id", financeData.userEnteredTransactionId())).isTrue();
        assertThat(exists("transactions_users", "transaction_id", financeData.recurringOccurrenceTransactionId())).isTrue();

        assertThat(exists("recurring_transactions_users", "recurring_transaction_id", financeData.recurringTransactionId())).isTrue();
        assertThat(exists("recurring_transaction_details_history", "recurring_transaction_id", financeData.recurringTransactionId())).isTrue();
        assertThat(exists("recurring_transaction_history", "recurring_transaction_id", financeData.recurringTransactionId())).isTrue();
        assertThat(exists("recurring_transactions", "recurring_transaction_id", financeData.recurringTransactionId())).isTrue();

        assertThat(exists("simulation_groups_accounts", "simulation_group_id", financeData.simulationGroupId())).isTrue();
        assertThat(exists("simulation_groups", "simulation_group_id", financeData.simulationGroupId())).isTrue();

        assertThat(exists("buckets_accounts", "bucket_id", financeData.bucketId())).isTrue();
        assertThat(exists("credit_cards", "credit_card_id", financeData.creditCardId())).isTrue();
        assertThat(exists("buckets", "bucket_id", financeData.bucketId())).isTrue();
        assertThat(exists("accounts", "account_id", financeData.accountId())).isTrue();

        assertThat(exists("category_status_history", "category_id", financeData.categoryId())).isTrue();
        assertThat(exists("category_details_history", "category_id", financeData.categoryId())).isTrue();
        assertThat(exists("categories", "category_id", financeData.categoryId())).isTrue();
    }

    private boolean exists(
            String tableName,
            String idColumnName,
            UUID id
    ) {
        return countWhere(tableName, idColumnName + " = ?", id) > 0;
    }

    private long countWhere(
            String tableName,
            String whereClause,
            Object... args
    ) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + whereClause,
                Long.class,
                args
        );

        return count == null ? 0 : count;
    }

    private UUID insertReturningUuid(
            String sql,
            Object... args
    ) {
        return jdbcTemplate.queryForObject(sql, UUID.class, args);
    }

    private byte[] bytes(int size) {
        byte[] bytes = new byte[size];

        for (int index = 0; index < size; index++) {
            bytes[index] = 1;
        }

        return bytes;
    }

    private void assertSharedFinanceGraphStillPresentAfterCollaboratorDeletion(FinanceData financeData) {
        assertThat(exists("transactions", "transaction_id", financeData.userEnteredTransactionId())).isTrue();
        assertThat(exists("transactions", "transaction_id", financeData.recurringOccurrenceTransactionId())).isTrue();

        assertThat(exists("transactions_users", "transaction_id", financeData.userEnteredTransactionId())).isTrue();
        assertThat(exists("transactions_users", "transaction_id", financeData.recurringOccurrenceTransactionId())).isTrue();

        assertThat(exists("recurring_transactions_users", "recurring_transaction_id", financeData.recurringTransactionId())).isTrue();
        assertThat(exists("recurring_transaction_details_history", "recurring_transaction_id", financeData.recurringTransactionId())).isTrue();
        assertThat(exists("recurring_transaction_history", "recurring_transaction_id", financeData.recurringTransactionId())).isTrue();
        assertThat(exists("recurring_transactions", "recurring_transaction_id", financeData.recurringTransactionId())).isTrue();

        assertThat(exists("simulation_groups_accounts", "simulation_group_id", financeData.simulationGroupId())).isTrue();
        assertThat(exists("simulation_groups", "simulation_group_id", financeData.simulationGroupId())).isTrue();

        assertThat(exists("buckets_accounts", "bucket_id", financeData.bucketId())).isTrue();
        assertThat(exists("credit_cards", "credit_card_id", financeData.creditCardId())).isTrue();
        assertThat(exists("buckets", "bucket_id", financeData.bucketId())).isTrue();
        assertThat(exists("accounts", "account_id", financeData.accountId())).isTrue();

        assertThat(exists("category_status_history", "category_id", financeData.categoryId())).isTrue();
        assertThat(exists("category_details_history", "category_id", financeData.categoryId())).isTrue();
        assertThat(exists("categories", "category_id", financeData.categoryId())).isTrue();

        assertThat(exists(
                "finance_reminder_notifications",
                "finance_reminder_notification_id",
                financeData.ownerReminderNotificationId()
        )).isTrue();

        assertThat(exists(
                "finance_reminder_notifications",
                "finance_reminder_notification_id",
                financeData.collaboratorReminderNotificationId()
        )).isFalse();
    }

    private void disableUser(UUID userId) {
        jdbcTemplate.update(
                """
                        update users
                        set user_is_enabled = false,
                            user_updated_at = ?
                        where user_id = ?
                        """,
                OffsetDateTime.now(clock),
                userId
        );
    }

    private UUID createEmailOutboxWithoutUser(String suffix) {
        return insertReturningUuid(
                """
                        insert into email_outbox (
                            user_id,
                            recipient_email,
                            email_type,
                            encryption_key_id,
                            subject_encrypted,
                            subject_iv,
                            subject_tag,
                            body_text_encrypted,
                            body_text_iv,
                            body_text_tag,
                            delete_body_after_send,
                            email_status
                        )
                        values (null, ?, 'GENERIC', 'test-key', ?, ?, ?, ?, ?, ?, false, 'PENDING')
                        returning email_outbox_id
                        """,
                "orphan-" + suffix + "-" + UUID.randomUUID() + "@example.com",
                bytes(1),
                bytes(12),
                bytes(16),
                bytes(1),
                bytes(12),
                bytes(16)
        );
    }

    private UUID createNullUserLoginAttempt(String suffix) {
        return insertReturningUuid(
                """
                        insert into auth_login_attempts (
                            user_id,
                            email_hash,
                            ip_address_hash,
                            login_successful,
                            failure_reason
                        )
                        values (null, ?, ?, false, 'RATE_LIMITED')
                        returning auth_login_attempt_id
                        """,
                "null-user-email-hash-" + suffix + "-" + UUID.randomUUID(),
                "null-user-ip-hash-" + suffix + "-" + UUID.randomUUID()
        );
    }

    private UUID createRecurringReminderNotification(
            UUID userId,
            UUID userGroupId,
            UUID recurringTransactionId,
            UUID emailOutboxId,
            String description
    ) {
        return insertReturningUuid(
                """
                        insert into finance_reminder_notifications (
                            user_id,
                            user_group_id,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            charge_date,
                            notified_description,
                            notified_amount,
                            notified_currency,
                            reminder_date,
                            email_outbox_id
                        )
                        values (?, ?, ?, ?, ?, ?, -100.00, 'EUR', ?, ?)
                        returning finance_reminder_notification_id
                        """,
                userId,
                userGroupId,
                recurringTransactionId,
                LocalDate.now(clock).plusDays(20),
                LocalDate.now(clock).plusDays(20),
                description,
                LocalDate.now(clock).plusDays(13),
                emailOutboxId
        );
    }

    private long countAll(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName,
                Long.class
        );

        return count == null ? 0 : count;
    }

    private UUID createAdditionalEmailOutboxForUser(UUID userId, String suffix) {
        return insertReturningUuid(
                """
                        insert into email_outbox (
                            user_id,
                            recipient_email,
                            email_type,
                            encryption_key_id,
                            subject_encrypted,
                            subject_iv,
                            subject_tag,
                            body_text_encrypted,
                            body_text_iv,
                            body_text_tag,
                            delete_body_after_send,
                            email_status
                        )
                        values (?, ?, 'GENERIC', 'test-key', ?, ?, ?, ?, ?, ?, false, 'PENDING')
                        returning email_outbox_id
                        """,
                userId,
                "extra-recipient-" + suffix + "-" + UUID.randomUUID() + "@example.com",
                bytes(1),
                bytes(12),
                bytes(16),
                bytes(1),
                bytes(12),
                bytes(16)
        );
    }

    @TestConfiguration
    static class FixedClockTestConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-06-03T10:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }

    private record GroupFixture(
            UUID userGroupId,
            UUID ownerId,
            UUID collaboratorId,
            PersonalData ownerPersonalData,
            PersonalData collaboratorPersonalData,
            FinanceData financeData
    ) {
    }

    private record PersonalData(
            UUID emailOutboxId,
            UUID authActionTokenId,
            UUID userSessionId,
            UUID refreshTokenId,
            UUID authLoginAttemptId
    ) {
    }

    private record FinanceData(
            UUID categoryId,
            UUID accountId,
            UUID creditCardId,
            UUID bucketId,
            UUID simulationGroupId,
            UUID recurringTransactionId,
            UUID userEnteredTransactionId,
            UUID recurringOccurrenceTransactionId,
            UUID ownerReminderNotificationId,
            UUID collaboratorReminderNotificationId
    ) {
    }
}