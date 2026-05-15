package me.serenityline.api.auth.exception;

public class Login2faInvalidOrExpiredException extends IllegalArgumentException {

    public Login2faInvalidOrExpiredException() {
        super("auth.login2fa.invalidOrExpired");
    }
}