package me.serenityline.api.auth.entity;

public enum AuthActionTokenType {
    PASSWORD_RESET,
    EMAIL_VERIFICATION,
    EMAIL_VERIFICATION_RESEND,
    LOGIN_2FA_CODE,
    EMAIL_CHANGE_CONFIRMATION,
    RESTORE_ACCOUNT
}
