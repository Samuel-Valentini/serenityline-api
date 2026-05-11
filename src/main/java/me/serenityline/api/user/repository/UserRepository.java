package me.serenityline.api.user.repository;

import me.serenityline.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndUserDeletedAtIsNull(String email);

    /*TODO auth/account recovery:
       Permettere a un utente soft-deleted di riaprire il proprio account entro la finestra di 30 giorni.*/

    Optional<User> findByEmailAndUserDeletedAtIsNotNull(String email);
}