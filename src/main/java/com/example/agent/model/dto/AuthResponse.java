package com.example.agent.model.dto;

public record AuthResponse(
    String token,
    String username,
    Long userId
) {}
