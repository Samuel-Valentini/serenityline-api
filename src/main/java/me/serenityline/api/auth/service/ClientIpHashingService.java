package me.serenityline.api.auth.service;

import me.serenityline.api.security.token.TokenHashingService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ClientIpHashingService {

    private final TokenHashingService tokenHashingService;

    public ClientIpHashingService(TokenHashingService tokenHashingService) {
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
    }

    public String hashIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }

        return tokenHashingService.hash("client-ip:" + ipAddress.trim());
    }
}