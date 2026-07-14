package com.example.agent.model.dto;

import java.time.LocalDateTime;

public record CleaningHistoryRecord(
    Long id,
    Long datasetId,
    String status,
    Long affectedRows,
    String errorMessage,
    LocalDateTime createdAt
) {}