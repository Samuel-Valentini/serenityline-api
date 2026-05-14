package me.serenityline.api.security.auth;

import me.serenityline.api.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String userName,
        String email,
        UUID userGroupId,
        String userGroupName,
        String userRole,
        String userPlatformRole,
        String preferredLocale,
        String preferredTheme,
        boolean wantsInvoice,
        List<GrantedAuthority> authorities
) implements Principal {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(userName, "userName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(userGroupName, "userGroupName");
        Objects.requireNonNull(userRole, "userRole");
        Objects.requireNonNull(userPlatformRole, "userPlatformRole");
        Objects.requireNonNull(preferredLocale, "preferredLocale");
        Objects.requireNonNull(preferredTheme, "preferredTheme");

        authorities = authorities == null
                ? List.of()
                : List.copyOf(authorities);
    }

    public static AuthenticatedUser from(User user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.authentication.user.required");
        }

        return new AuthenticatedUser(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                user.getUserGroup().getUserGroupId(),
                user.getUserGroup().getUserGroupName(),
                user.getUserRole().name(),
                user.getUserPlatformRole().name(),
                user.getPreferredLocale(),
                user.getPreferredTheme().name(),
                user.isWantsInvoice(),
                authoritiesFrom(user)
        );
    }

    private static List<GrantedAuthority> authoritiesFrom(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        authorities.add(new SimpleGrantedAuthority("GROUP_ROLE_" + user.getUserRole().name()));
        authorities.add(new SimpleGrantedAuthority("PLATFORM_ROLE_" + user.getUserPlatformRole().name()));

        return List.copyOf(authorities);
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}