package me.serenityline.api.auth.repository;

import me.serenityline.api.auth.entity.EmailOutbox;
import me.serenityline.api.auth.entity.EmailOutboxStatus;
import me.serenityline.api.auth.entity.EmailOutboxType;
import me.serenityline.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
    List<EmailOutbox> findAllByUserAndEmailTypeAndEmailStatus(
            User user,
            EmailOutboxType emailType,
            EmailOutboxStatus emailStatus
    );
}