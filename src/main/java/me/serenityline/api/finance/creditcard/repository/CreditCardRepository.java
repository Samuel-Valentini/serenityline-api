package me.serenityline.api.finance.creditcard.repository;

import me.serenityline.api.finance.creditcard.entity.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    long countByUserGroup_UserGroupId(UUID userGroupId);

    List<CreditCard> findAllByUserGroup_UserGroupIdOrderByCreditCardNameAsc(
            UUID userGroupId
    );

    Optional<CreditCard> findByCreditCardIdAndUserGroup_UserGroupId(
            UUID creditCardId,
            UUID userGroupId
    );

    @Query("""
            select creditCard
            from CreditCard creditCard
            join AccountUser accountUser on accountUser.account = creditCard.account
            where creditCard.userGroup.userGroupId = :userGroupId
              and accountUser.user.userId = :userId
            order by lower(creditCard.creditCardName), creditCard.creditCardName
            """)
    List<CreditCard> findAllVisibleToLinkedUser(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query("""
            select creditCard
            from CreditCard creditCard
            join AccountUser accountUser on accountUser.account = creditCard.account
            where creditCard.creditCardId = :creditCardId
              and creditCard.userGroup.userGroupId = :userGroupId
              and accountUser.user.userId = :userId
            """)
    Optional<CreditCard> findVisibleCreditCardForLinkedUser(
            @Param("creditCardId") UUID creditCardId,
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query(
            value = """
                    select exists (
                        select 1
                        from credit_cards cc
                        where cc.user_group_id = :userGroupId
                          and lower(btrim(regexp_replace(cc.credit_card_name, '[[:space:]]+', ' ', 'g'))) = :normalizedCreditCardName
                    )
                    """,
            nativeQuery = true
    )
    boolean existsByUserGroupIdAndNormalizedCreditCardName(
            @Param("userGroupId") UUID userGroupId,
            @Param("normalizedCreditCardName") String normalizedCreditCardName
    );

    @Query(
            value = """
                    select exists (
                        select 1
                        from credit_cards cc
                        where cc.user_group_id = :userGroupId
                          and cc.credit_card_id <> :creditCardId
                          and lower(btrim(regexp_replace(cc.credit_card_name, '[[:space:]]+', ' ', 'g'))) = :normalizedCreditCardName
                    )
                    """,
            nativeQuery = true
    )
    boolean existsByUserGroupIdAndNormalizedCreditCardNameExcludingCreditCardId(
            @Param("userGroupId") UUID userGroupId,
            @Param("normalizedCreditCardName") String normalizedCreditCardName,
            @Param("creditCardId") UUID creditCardId
    );
}