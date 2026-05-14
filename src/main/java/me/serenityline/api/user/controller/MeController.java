package me.serenityline.api.user.controller;

import me.serenityline.api.security.auth.AuthenticatedUser;
import me.serenityline.api.user.dto.CurrentUserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public CurrentUserResponse me(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return CurrentUserResponse.from(authenticatedUser);
    }
}