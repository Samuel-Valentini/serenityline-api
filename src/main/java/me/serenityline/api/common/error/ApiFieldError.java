package me.serenityline.api.common.error;

public record ApiFieldError(String field,
                            String code,
                            String message) {
}
