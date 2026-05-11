package me.serenityline.api.auth.repository;

import me.serenityline.api.auth.entity.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
}