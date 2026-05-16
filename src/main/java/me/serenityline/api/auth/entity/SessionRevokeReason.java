package me.serenityline.api.auth.entity;

public enum SessionRevokeReason {
    USER_LOGOUT,
    PASSWORD_CHANGED,
    TOKEN_REUSE_DETECTED,
    ADMIN_REVOKED,
    ACCOUNT_DELETED,
    EMAIL_CHANGED
}