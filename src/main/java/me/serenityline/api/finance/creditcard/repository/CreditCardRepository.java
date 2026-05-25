package me.serenityline.api.finance.creditcard.repository;

import me.serenityline.api.finance.creditcard.entity.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    long countByUserGroup_UserGroupId(UUID userGroupId);

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
}