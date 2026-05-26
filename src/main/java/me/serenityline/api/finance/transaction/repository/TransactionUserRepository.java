package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.TransactionUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionUserRepository extends JpaRepository<TransactionUser, UUID> {
}