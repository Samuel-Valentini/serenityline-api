package me.serenityline.api.user.service.deletion;

import me.serenityline.api.user.entity.UserRole;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class AccountHardDeletionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AccountHardDeletionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public List<AccountHardDeletionCandidate> findDueCandidatesForUpdate(
            OffsetDateTime cutoff,
            int limit
    ) {
        if (cutoff == null) {
            throw new IllegalArgumentException("accountHardDeletion.cutoff.required");
        }

        if (limit < 1) {
            throw new IllegalArgumentException("accountHardDeletion.limit.invalid");
        }

        String sql = """
                select
                    user_id,
                    user_group_id,
                    user_role
                from users
                where user_deleted_at is not null
                  and user_deleted_at <= :cutoff
                order by
                    case when user_role = 'OWNER' then 0 else 1 end,
                    user_deleted_at asc,
                    user_id asc
                limit :limit
                for update skip locked
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", cutoff)
                .addValue("limit", limit);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new AccountHardDeletionCandidate(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("user_group_id", UUID.class),
                        UserRole.valueOf(rs.getString("user_role"))
                )
        );
    }

    public int hardDeleteCollaboratorUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId);

        int rowsDeleted = 0;

        rowsDeleted += update("""
                delete from finance_reminder_notifications
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from email_outbox
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from auth_login_attempts
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from auth_action_tokens
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from refresh_tokens
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from user_sessions
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from transactions_users
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from recurring_transactions_users
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from accounts_users
                where user_id = :userId
                """, params);

        rowsDeleted += update("""
                delete from users
                where user_id = :userId
                """, params);

        return rowsDeleted;
    }

    public int hardDeleteOwnerGroup(UUID userGroupId) {
        Objects.requireNonNull(userGroupId, "userGroupId");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userGroupId", userGroupId);

        int rowsDeleted = 0;

        rowsDeleted += update("""
                delete from finance_reminder_notifications
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from email_outbox
                where user_id in (
                    select user_id
                    from users
                    where user_group_id = :userGroupId
                )
                """, params);

        rowsDeleted += update("""
                delete from auth_login_attempts
                where user_id in (
                    select user_id
                    from users
                    where user_group_id = :userGroupId
                )
                """, params);

        rowsDeleted += update("""
                delete from auth_action_tokens
                where user_id in (
                    select user_id
                    from users
                    where user_group_id = :userGroupId
                )
                """, params);

        rowsDeleted += update("""
                delete from refresh_tokens
                where user_id in (
                    select user_id
                    from users
                    where user_group_id = :userGroupId
                )
                """, params);

        rowsDeleted += update("""
                delete from user_sessions
                where user_id in (
                    select user_id
                    from users
                    where user_group_id = :userGroupId
                )
                """, params);

        rowsDeleted += update("""
                delete from transactions_users
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from recurring_transactions_users
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from accounts_users
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from transactions
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from recurring_transactions
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from simulation_groups_accounts
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from simulation_groups
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from buckets_accounts
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from credit_cards
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from buckets
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from accounts
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from category_status_history
                where category_id in (
                    select category_id
                    from categories
                    where user_group_id = :userGroupId
                )
                """, params);

        rowsDeleted += update("""
                delete from category_details_history
                where category_id in (
                    select category_id
                    from categories
                    where user_group_id = :userGroupId
                )
                """, params);

        rowsDeleted += update("""
                delete from categories
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from users
                where user_group_id = :userGroupId
                """, params);

        rowsDeleted += update("""
                delete from user_groups
                where user_group_id = :userGroupId
                """, params);

        return rowsDeleted;
    }

    private int update(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.update(sql, params);
    }
}