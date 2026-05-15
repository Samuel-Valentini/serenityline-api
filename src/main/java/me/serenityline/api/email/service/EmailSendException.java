package me.serenityline.api.email.service;

public class EmailSendException extends RuntimeException {

    private final String safeMessage;

    public EmailSendException(String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.safeMessage = safeMessage;
    }

    public EmailSendException(String safeMessage) {
        super(safeMessage);
        this.safeMessage = safeMessage;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}