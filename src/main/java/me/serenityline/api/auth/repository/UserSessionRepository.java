package me.serenityline.api.auth.repository;

import jakarta.persistence.LockModeType;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from UserSession session
            where session.user = :user
              and session.sessionRevokedAt is null
              and session.sessionExpiresAt > :now
            order by session.userSessionId
            """)
    List<UserSession> findActiveByUserForUpdate(
            @Param("user") User user,
            @Param("now") OffsetDateTime now
    );
}