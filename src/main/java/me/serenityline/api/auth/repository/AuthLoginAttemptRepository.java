package me.serenityline.api.auth.repository;

import me.serenityline.api.auth.entity.AuthLoginAttempt;
import me.serenityline.api.auth.entity.AuthLoginFailureReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, UUID> {

    @Query("""
            select max(a.authLoginAttemptAt)
            from AuthLoginAttempt a
            where a.emailHash = :emailHash
              and a.ipAddressHash = :ipAddressHash
              and a.loginSuccessful = true
            """)
    Optional<OffsetDateTime> findLastSuccessfulAtByEmailHashAndIpAddressHash(
            String emailHash,
            String ipAddressHash
    );

    @Query("""
            select max(a.authLoginAttemptAt)
            from AuthLoginAttempt a
            where a.emailHash = :emailHash
              and a.loginSuccessful = true
            """)
    Optional<OffsetDateTime> findLastSuccessfulAtByEmailHash(String emailHash);

    @Query("""
            select count(a)
            from AuthLoginAttempt a
            where a.emailHash = :emailHash
              and a.ipAddressHash = :ipAddressHash
              and a.loginSuccessful = false
              and a.failureReason = :failureReason
              and a.authLoginAttemptAt > :after
            """)
    long countFailuresByEmailHashAndIpAddressHashAfter(
            String emailHash,
            String ipAddressHash,
            AuthLoginFailureReason failureReason,
            OffsetDateTime after
    );

    @Query("""
            select count(a)
            from AuthLoginAttempt a
            where a.emailHash = :emailHash
              and a.loginSuccessful = false
              and a.failureReason = :failureReason
              and a.authLoginAttemptAt > :after
            """)
    long countFailuresByEmailHashAfter(
            String emailHash,
            AuthLoginFailureReason failureReason,
            OffsetDateTime after
    );

    @Query("""
            select count(a)
            from AuthLoginAttempt a
            where a.ipAddressHash = :ipAddressHash
              and a.loginSuccessful = false
              and a.failureReason = :failureReason
              and a.authLoginAttemptAt > :after
            """)
    long countFailuresByIpAddressHashAfter(
            String ipAddressHash,
            AuthLoginFailureReason failureReason,
            OffsetDateTime after
    );
}