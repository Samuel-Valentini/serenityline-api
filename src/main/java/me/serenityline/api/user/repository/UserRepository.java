package me.serenityline.api.user.repository;

import jakarta.persistence.LockModeType;
import me.serenityline.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndUserIdNot(String email, UUID userId);

    Optional<User> findByEmailAndUserDeletedAtIsNull(String email);

    Optional<User> findByEmailAndUserDeletedAtIsNotNull(String email);

    @Query("""
            select user
            from User user
            join fetch user.userGroup
            where user.email = :email
            """)
    Optional<User> findLoginCandidateByEmail(@Param("email") String email);

    @Query("""
            select user
            from User user
            join fetch user.userGroup
            where user.userId = :userId
              and user.userDeletedAt is null
              and user.userIsEnabled = true
            """)
    Optional<User> findAuthenticationUserById(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select user
            from User user
            where user.userId = :userId
              and user.userDeletedAt is null
              and user.userIsEnabled = true
            """)
    Optional<User> findActiveUserByIdForUpdate(@Param("userId") UUID userId);

}