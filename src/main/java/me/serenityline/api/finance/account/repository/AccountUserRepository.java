package me.serenityline.api.finance.account.repository;

import me.serenityline.api.finance.account.entity.AccountUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountUserRepository extends JpaRepository<AccountUser, UUID> {

    boolean existsByAccount_AccountIdAndUser_UserId(
            UUID accountId,
            UUID userId
    );

    long countByAccount_AccountId(UUID accountId);

    long countByUser_UserId(UUID userId);

    List<AccountUser> findAllByAccount_AccountId(UUID accountId);

    List<AccountUser> findAllByUser_UserId(UUID userId);
}