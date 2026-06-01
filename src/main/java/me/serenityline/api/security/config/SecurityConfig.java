package me.serenityline.api.security.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.serenityline.api.auth.config.RefreshTokenProperties;
import me.serenityline.api.security.auth.JwtAuthenticationFilter;
import me.serenityline.api.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityCorsProperties.class, JwtProperties.class, RefreshTokenProperties.class})
public class SecurityConfig {

    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String AUTH_CSRF_PATH = "/api/auth/csrf";
    private static final String AUTH_REFRESH_PATH = "/api/auth/refresh";

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
    );

    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Accept-Language",
            CSRF_HEADER_NAME
    );

    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    private final SecurityCorsProperties corsProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RefreshTokenProperties refreshTokenProperties;

    public SecurityConfig(SecurityCorsProperties corsProperties, JwtAuthenticationFilter jwtAuthenticationFilter, RefreshTokenProperties refreshTokenProperties) {
        this.corsProperties = corsProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(cookieCsrfTokenRepository())
                        .requireCsrfProtectionMatcher(this::requiresRefreshCsrfProtection)
                )
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
                        )
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, AUTH_CSRF_PATH).permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/login",
                                "/api/auth/restore-account",
                                "/api/auth/resend-email-verification",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/login/2fa/verify",
                                "/api/auth/email-change/confirm",
                                "/api/auth/user-invitations/accept"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }

    private CookieCsrfTokenRepository cookieCsrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();

        repository.setCookieName(CSRF_COOKIE_NAME);
        repository.setHeaderName(CSRF_HEADER_NAME);
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(refreshTokenProperties.cookieSecure())
                .httpOnly(true)
                .sameSite(refreshTokenProperties.cookieSameSite())
        );

        return repository;
    }

    private boolean requiresRefreshCsrfProtection(HttpServletRequest request) {
        if (!HttpMethod.POST.name().equals(request.getMethod())) {
            return false;
        }

        if (!AUTH_REFRESH_PATH.equals(requestPathWithoutContextPath(request))) {
            return false;
        }

        return hasNonBlankRefreshCookie(request);
    }

    private boolean hasNonBlankRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return false;
        }

        return Arrays.stream(cookies)
                .anyMatch(cookie ->
                        refreshTokenProperties.cookieName().equals(cookie.getName())
                                && cookie.getValue() != null
                                && !cookie.getValue().isBlank()
                );
    }

    private String requestPathWithoutContextPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }

        return path;
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(CORS_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}