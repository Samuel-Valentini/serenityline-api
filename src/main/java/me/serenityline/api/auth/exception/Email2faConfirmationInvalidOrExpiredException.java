package me.serenityline.api.auth.exception;

public class Email2faConfirmationInvalidOrExpiredException extends IllegalArgumentException {

    public Email2faConfirmationInvalidOrExpiredException() {
        super("auth.email2fa.confirmation.invalidOrExpired");
    }
}