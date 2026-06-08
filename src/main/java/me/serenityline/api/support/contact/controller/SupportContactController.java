package me.serenityline.api.support.contact.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import me.serenityline.api.security.auth.AuthenticatedUser;
import me.serenityline.api.support.contact.dto.SupportContactRequest;
import me.serenityline.api.support.contact.dto.SupportContactResponse;
import me.serenityline.api.support.contact.service.SupportContactService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Objects;

@RestController
public class SupportContactController {

    private final SupportContactService supportContactService;

    public SupportContactController(SupportContactService supportContactService) {
        this.supportContactService = Objects.requireNonNull(supportContactService, "supportContactService");
    }

    @PostMapping("/api/support/contact")
    public ResponseEntity<SupportContactResponse> contact(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody SupportContactRequest request,
            HttpServletRequest httpServletRequest,
            Locale locale
    ) {
        supportContactService.submit(
                request,
                authenticatedUser,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader(HttpHeaders.USER_AGENT)
        );

        return ResponseEntity
                .accepted()
                .body(SupportContactResponse.accepted(supportContactService.acceptedMessage(locale)));
    }
}