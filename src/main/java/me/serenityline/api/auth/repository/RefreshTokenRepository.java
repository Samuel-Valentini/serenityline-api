package me.serenityline.api.auth.repository;

import jakarta.persistence.LockModeType;
import me.serenityline.api.auth.entity.RefreshToken;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByRefreshTokenHash(String refreshTokenHash);


    /* Give me the most recent refresh token for this session that:
        - belongs to this userSession
        - has never been used
        - has not been revoked
        - has not expired
    ordered from newest to oldest
    taking only the first result */

    /* (Italian) Dammi il refresh token più recente di questa sessione che:
        - appartiene a questa userSession
        - non è mai stato usato
        - non è stato revocato
        - non è scaduto
    ordinato dal più recente al più vecchio
    prendendo solo il primo risultato*/

    Optional<RefreshToken> findFirstByUserSessionAndRefreshTokenUsedAtIsNullAndRefreshTokenRevokedAtIsNullAndRefreshTokenExpiresAtAfterOrderByRefreshTokenCreatedAtDesc(
            UserSession userSession,
            OffsetDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from RefreshToken token
            join fetch token.user user
            join fetch user.userGroup
            join fetch token.userSession
            where token.refreshTokenHash = :refreshTokenHash
            """)
    Optional<RefreshToken> findByRefreshTokenHashForUpdate(
            @Param("refreshTokenHash") String refreshTokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from RefreshToken token
            where token.user = :user
              and token.refreshTokenUsedAt is null
              and token.refreshTokenRevokedAt is null
              and token.refreshTokenExpiresAt > :now
            order by token.refreshTokenId
            """)
    List<RefreshToken> findActiveByUserForUpdate(
            @Param("user") User user,
            @Param("now") OffsetDateTime now
    );
}