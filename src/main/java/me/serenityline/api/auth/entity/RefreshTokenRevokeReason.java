package me.serenityline.api.auth.entity;

public enum RefreshTokenRevokeReason {
    USER_LOGOUT,
    PASSWORD_CHANGED,
    REUSE_DETECTED,
    SESSION_REVOKED,
    ADMIN_REVOKED
}
