package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecurringTransactionUserRepository extends JpaRepository<RecurringTransactionUser, UUID> {
}