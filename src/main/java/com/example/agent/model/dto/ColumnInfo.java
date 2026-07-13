package com.example.agent.model.dto;

public record ColumnInfo(
    String name,
    String type,
    boolean nullable
) {}
