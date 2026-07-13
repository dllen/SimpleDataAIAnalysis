package com.example.agent.model.dto;

import java.util.List;

public record QueryResult(
    List<String> columns,
    List<List<Object>> rows,
    int totalRows,
    long executionTimeMs
) {}
