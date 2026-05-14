package me.serenityline.api.security.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.serenityline.api.security.jwt.JwtTokenClaims;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserRepository userRepository
    ) {
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService, "jwtTokenService");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    private static void reject(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            reject(response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isBlank()) {
            reject(response);
            return;
        }

        Optional<JwtTokenClaims> claims = jwtTokenService.parseAndValidate(token);

        if (claims.isEmpty()) {
            reject(response);
            return;
        }

        Optional<User> userOptional = userRepository.findAuthenticationUserById(
                claims.get().userId()
        );

        if (userOptional.isEmpty()) {
            reject(response);
            return;
        }

        User user = userOptional.get();

        if (!Objects.equals(user.getTokenVersion(), claims.get().tokenVersion())) {
            reject(response);
            return;
        }

        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        authenticatedUser.authorities()
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.name().equals(request.getMethod())) {
            return true;
        }

        if (!HttpMethod.POST.name().equals(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        return switch (path) {
            case "/api/auth/register",
                 "/api/auth/verify-email",
                 "/api/auth/login",
                 "/api/auth/restore-account",
                 "/api/auth/resend-email-verification",
                 "/api/auth/refresh" -> true;
            default -> false;
        };
    }
}