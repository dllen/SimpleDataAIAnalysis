package com.example.agent.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalysisRequest(
    @NotBlank(message = "问题不能为空")
    String question
) {}
