package com.example.agent.model.dto;

import com.example.agent.model.enums.DatasetStatus;
import java.time.LocalDateTime;

public record DatasetResponse(
    Long id,
    String fileName,
    String fileType,
    String tableName,
    java.util.List<ColumnInfo> columns,
    Long rowCount,
    LocalDateTime createdAt,
    DatasetStatus status
) {}
