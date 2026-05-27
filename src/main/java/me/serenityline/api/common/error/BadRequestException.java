package me.serenityline.api.common.error;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String code) {
        super(code);
    }
}