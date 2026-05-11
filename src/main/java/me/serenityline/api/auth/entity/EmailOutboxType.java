package me.serenityline.api.auth.entity;

public enum EmailOutboxType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    EMAIL_CHANGE_CONFIRMATION,
    LOGIN_2FA_CODE,
    GENERIC
}
