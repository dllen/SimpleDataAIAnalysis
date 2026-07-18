package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties("app.auth")
public record AuthProperties(
    String jwtSecret,
    Duration jwtExpiration
) {
    public AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("app.auth.jwt-secret must be configured");
        }
    }
}
