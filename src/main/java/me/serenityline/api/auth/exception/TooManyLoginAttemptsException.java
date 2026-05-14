package me.serenityline.api.auth.exception;

public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("auth.login.tooManyAttempts");
    }
}