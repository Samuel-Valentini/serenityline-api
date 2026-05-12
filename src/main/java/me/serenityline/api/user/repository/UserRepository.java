package me.serenityline.api.user.repository;

import me.serenityline.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndUserDeletedAtIsNull(String email);

    /*TODO auth/account recovery:
       Permettere a un utente soft-deleted di riaprire il proprio account entro la finestra di 30 giorni.*/

    Optional<User> findByEmailAndUserDeletedAtIsNotNull(String email);

    @Query("""
            select user
            from User user
            join fetch user.userGroup
            where user.email = :email
            """)
    Optional<User> findLoginCandidateByEmail(@Param("email") String email);
}