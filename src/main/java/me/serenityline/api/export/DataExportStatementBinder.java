package me.serenityline.api.export;

import me.serenityline.api.security.auth.AuthenticatedUser;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
interface DataExportStatementBinder {

    static DataExportStatementBinder none() {
        return (statement, authenticatedUser) -> {
        };
    }

    static DataExportStatementBinder authenticatedUserId() {
        return (statement, authenticatedUser) ->
                statement.setObject(1, authenticatedUser.userId());
    }

    static DataExportStatementBinder authenticatedUserGroupId() {
        return (statement, authenticatedUser) ->
                statement.setObject(1, authenticatedUser.userGroupId());
    }

    void bind(
            PreparedStatement statement,
            AuthenticatedUser authenticatedUser
    ) throws SQLException;
}