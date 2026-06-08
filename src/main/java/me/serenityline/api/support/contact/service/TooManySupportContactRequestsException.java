package me.serenityline.api.support.contact.service;

public class TooManySupportContactRequestsException extends RuntimeException {

    public TooManySupportContactRequestsException() {
        super("support.contact.tooManyRequests");
    }
}