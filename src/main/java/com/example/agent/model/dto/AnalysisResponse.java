package com.example.agent.model.dto;

public record AnalysisResponse(
    String sql,
    QueryResult result,
    String answer
) {}
