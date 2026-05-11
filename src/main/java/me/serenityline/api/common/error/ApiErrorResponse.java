package me.serenityline.api.common.error;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(String code,
                               String message,
                               OffsetDateTime timestamp,
                               String path,
                               List<ApiFieldError> fieldErrors) {

    public static ApiErrorResponse of(
            String code,
            String message,
            String path
    ) {
        return new ApiErrorResponse(
                code,
                message,
                OffsetDateTime.now(),
                path,
                List.of()
        );
    }

    public static ApiErrorResponse withFieldErrors(
            String code,
            String message,
            String path,
            List<ApiFieldError> fieldErrors
    ) {
        return new ApiErrorResponse(
                code,
                message,
                OffsetDateTime.now(),
                path,
                fieldErrors == null ? List.of() : List.copyOf(fieldErrors)
        );
    }
}
