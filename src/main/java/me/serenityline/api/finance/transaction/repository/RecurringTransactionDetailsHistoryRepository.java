package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecurringTransactionDetailsHistoryRepository
        extends JpaRepository<RecurringTransactionDetailsHistory, UUID> {
}