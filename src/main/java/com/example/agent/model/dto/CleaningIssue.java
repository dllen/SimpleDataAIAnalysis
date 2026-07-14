package com.example.agent.model.dto;

public record CleaningIssue(
    String type,
    String column,
    Long affectedRows,
    String description,
    String suggestion,
    String defaultSql
) {}
