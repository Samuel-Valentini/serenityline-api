package me.serenityline.api.auth.repository;

import me.serenityline.api.auth.entity.EmailOutbox;
import me.serenityline.api.auth.entity.EmailOutboxStatus;
import me.serenityline.api.auth.entity.EmailOutboxType;
import me.serenityline.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
    List<EmailOutbox> findAllByUserAndEmailTypeAndEmailStatus(
            User user,
            EmailOutboxType emailType,
            EmailOutboxStatus emailStatus
    );

    @Query(
            value = """
                    select *
                    from email_outbox
                    where email_status = 'PENDING'
                      and attempts < max_attempts
                      and email_scheduled_at <= :now
                    order by email_scheduled_at asc, email_created_at asc
                    limit :limit
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<EmailOutbox> findPendingDueForUpdate(
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );
}