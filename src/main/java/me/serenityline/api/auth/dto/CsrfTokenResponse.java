package me.serenityline.api.auth.dto;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token
) {
    public static CsrfTokenResponse from(CsrfToken csrfToken) {
        if (csrfToken == null) {
            throw new IllegalArgumentException("auth.csrfToken.required");
        }

        return new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()
        );
    }
}