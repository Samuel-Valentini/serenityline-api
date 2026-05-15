package me.serenityline.api.auth.repository;

import jakarta.persistence.EntityManager;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.user.entity.PreferredTheme;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserPlatformRole;
import me.serenityline.api.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthActionTokenRepositoryUuidGenerationTest {

    private static final String USER_GROUP_NAME = "Test user group";
    private static final String USER_NAME = "Samuel";
    private static final String USER_EMAIL = "auth-action-token-id-test@example.com";
    private static final String USER_PASSWORD_HASH = "encoded-password-hash";

    private static final String TEMPORARY_TOKEN_HASH = "temporary-login-2fa-hash";
    private static final AuthActionTokenType TOKEN_TYPE = AuthActionTokenType.LOGIN_2FA_CODE;

    @Autowired
    private AuthActionTokenRepository authActionTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void authActionTokenShouldHaveIdAfterSaveBeforeFlush() {
        User user = createManagedUser();

        AuthActionToken token = new AuthActionToken(
                user,
                TEMPORARY_TOKEN_HASH,
                TOKEN_TYPE,
                OffsetDateTime.now().plusMinutes(10)
        );

        authActionTokenRepository.save(token);

        assertThat(token.getAuthActionTokenId()).isNotNull();
    }

    private User createManagedUser() {
        UUID userGroupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        insertUserGroup(userGroupId);
        insertUser(userId, userGroupId);

        return entityManager.getReference(User.class, userId);
    }

    private void insertUserGroup(UUID userGroupId) {
        jdbcTemplate.update(
                """
                        insert into user_groups (
                            user_group_id,
                            user_group_name,
                            user_group_created_at
                        )
                        values (?, ?, now())
                        """,
                userGroupId,
                USER_GROUP_NAME
        );
    }

    private void insertUser(
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update(
                """
                        insert into users (
                            user_id,
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_platform_role,
                            preferred_locale,
                            preferred_theme,
                            wants_invoice,
                            email_2fa_enabled,
                            payment_email_reminders_enabled,
                            user_password_hash,
                            user_created_at,
                            user_updated_at,
                            user_deleted_at,
                            user_is_enabled,
                            token_version,
                            user_last_login_at
                        )
                        values (
                            ?, ?, ?, ?,
                            ?, ?, ?, ?,
                            ?, ?, ?,
                            ?,
                            now(), now(), null,
                            ?, ?, null
                        )
                        """,
                userId,
                USER_NAME,
                USER_EMAIL,
                userGroupId,
                UserRole.values()[0].name(),
                UserPlatformRole.USER.name(),
                "it-IT",
                PreferredTheme.DEFAULT.name(),
                false,
                false,
                true,
                USER_PASSWORD_HASH,
                true,
                0L
        );
    }
}