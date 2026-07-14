package com.example.agent.model.dto;

import com.example.agent.model.enums.DatasetStatus;

import java.time.LocalDateTime;

public record CleaningHistoryRecord(
    Long id,
    Long datasetId,
    DatasetStatus status,
    Long affectedRows,
    String errorMessage,
    LocalDateTime createdAt
) {}