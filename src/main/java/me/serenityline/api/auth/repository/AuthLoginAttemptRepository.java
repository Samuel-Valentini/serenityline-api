package me.serenityline.api.auth.repository;

import me.serenityline.api.auth.entity.AuthLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, UUID> {
}
