package me.serenityline.api.user.service.deletion;

import me.serenityline.api.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AccountHardDeletionProcessor {

    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 10_000;

    private final AccountHardDeletionRepository accountHardDeletionRepository;
    private final Clock clock;
    private final int batchSize;

    public AccountHardDeletionProcessor(
            AccountHardDeletionRepository accountHardDeletionRepository,
            Clock clock,
            @Value("${serenityline.account-deletion.hard-deletion.batch-size:50}") int batchSize
    ) {
        this.accountHardDeletionRepository = Objects.requireNonNull(
                accountHardDeletionRepository,
                "accountHardDeletionRepository"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.batchSize = batchSize;

        validateConfiguration();
    }

    @Transactional
    public AccountHardDeletionResult processDueHardDeletions() {
        validateConfiguration();

        OffsetDateTime cutoff = OffsetDateTime.now(clock)
                .minusDays(User.SOFT_DELETE_GRACE_PERIOD_DAYS);

        List<AccountHardDeletionCandidate> candidates =
                accountHardDeletionRepository.findDueCandidatesForUpdate(
                        cutoff,
                        batchSize
                );

        int ownerGroupsDeleted = 0;
        int collaboratorUsersDeleted = 0;
        int rowsDeleted = 0;

        for (AccountHardDeletionCandidate candidate : candidates) {
            int candidateRowsDeleted;

            if (candidate.isOwner()) {
                candidateRowsDeleted = accountHardDeletionRepository.hardDeleteOwnerGroup(
                        candidate.userGroupId()
                );

                if (candidateRowsDeleted > 0) {
                    ownerGroupsDeleted++;
                }
            } else {
                candidateRowsDeleted = accountHardDeletionRepository.hardDeleteCollaboratorUser(
                        candidate.userId()
                );

                if (candidateRowsDeleted > 0) {
                    collaboratorUsersDeleted++;
                }
            }

            rowsDeleted += candidateRowsDeleted;
        }

        return new AccountHardDeletionResult(
                candidates.size(),
                ownerGroupsDeleted,
                collaboratorUsersDeleted,
                rowsDeleted
        );
    }

    private void validateConfiguration() {
        if (batchSize < MIN_BATCH_SIZE || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalStateException("accountHardDeletion.batchSize.invalid");
        }
    }
}