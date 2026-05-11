package me.serenityline.api.auth.repository;

import me.serenityline.api.auth.entity.RefreshToken;
import me.serenityline.api.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
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
}