package me.serenityline.api.auth.repository;

import jakarta.persistence.LockModeType;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthActionTokenRepository extends JpaRepository<AuthActionToken, UUID> {

    Optional<AuthActionToken> findByAuthActionTokenHash(String authActionTokenHash);

    /*
      Finds the most recent still-valid action token for a specific user and token type.

      Returns the first AuthActionToken that:
      - belongs to the given user;
      - has the given type, for example PASSWORD_RESET or EMAIL_VERIFICATION;
      - has not been used yet;
      - has not been revoked;
      - has not expired compared to the provided timestamp.

      Ordering by authActionCreatedAt descending ensures that the newest usable token
      is returned.
    */

    /* (Italian)

      Cerca il token di azione più recente ancora valido per uno specifico utente e tipo di token.

      Restituisce il primo AuthActionToken che:
      - appartiene all'utente indicato;
      - ha il tipo indicato, ad esempio PASSWORD_RESET o EMAIL_VERIFICATION;
      - non è ancora stato usato;
      - non è stato revocato;
      - non è scaduto rispetto al momento passato come parametro.

      L'ordinamento per authActionCreatedAt decrescente fa sì che venga restituito
      il token più recente tra quelli ancora utilizzabili.
    */


    Optional<AuthActionToken> findFirstByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfterOrderByAuthActionCreatedAtDesc(
            User user,
            AuthActionTokenType authActionTokenType,
            OffsetDateTime now
    );

    List<AuthActionToken> findAllByUserAndAuthActionTokenTypeAndAuthActionUsedAtIsNullAndAuthActionRevokedAtIsNullAndAuthActionExpiresAtAfter(
            User user,
            AuthActionTokenType authActionTokenType,
            OffsetDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from AuthActionToken token
            join fetch token.user
            where token.authActionTokenHash = :authActionTokenHash
            """)
    Optional<AuthActionToken> findByAuthActionTokenHashForUpdate(
            @Param("authActionTokenHash") String authActionTokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from AuthActionToken token
            join fetch token.user
            where token.authActionTokenId = :authActionTokenId
            """)
    Optional<AuthActionToken> findByIdForUpdate(
            @Param("authActionTokenId") UUID authActionTokenId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    delete from auth_action_tokens
                    where auth_action_token_id in (
                        select auth_action_token_id
                        from auth_action_tokens
                        where auth_action_expires_at < :cutoff
                           or auth_action_used_at < :cutoff
                           or auth_action_revoked_at < :cutoff
                        order by auth_action_created_at asc
                        limit :limit
                    )
                    """,
            nativeQuery = true
    )
    int deleteCleanupCandidates(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("limit") int limit
    );
}
