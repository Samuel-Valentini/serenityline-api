package me.serenityline.api.auth.dto;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token
) {
    private static final String PUBLIC_CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String PUBLIC_CSRF_PARAMETER_NAME = "_csrf";

    public static CsrfTokenResponse from(CsrfToken csrfToken) {
        if (csrfToken == null) {
            throw new IllegalArgumentException("auth.csrfToken.required");
        }

        String token = csrfToken.getToken();

        return new CsrfTokenResponse(
                PUBLIC_CSRF_HEADER_NAME,
                PUBLIC_CSRF_PARAMETER_NAME,
                token
        );
    }
}