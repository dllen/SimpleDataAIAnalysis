package com.example.agent.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    @Value("${app.ai.providers.deepseek.base-url}")
    private String deepseekBaseUrl;

    @Value("${app.ai.providers.kimi.base-url}")
    private String kimiBaseUrl;

    @Value("${app.ai.providers.minimax.base-url}")
    private String minimaxBaseUrl;

    @GetMapping
    public List<Map<String, Object>> listModels() {
        return List.of(
            Map.of("name", "deepseek", "baseUrl", deepseekBaseUrl, "models", List.of("deepseek-chat", "deepseek-reasoner")),
            Map.of("name", "kimi", "baseUrl", kimiBaseUrl, "models", List.of("moonshot-v1-8k", "moonshot-v1-32k")),
            Map.of("name", "minimax", "baseUrl", minimaxBaseUrl, "models", List.of("abab6.5s-chat", "abab6.5t-chat"))
        );
    }
}
