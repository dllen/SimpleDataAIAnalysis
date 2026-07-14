package com.example.agent.model.dto;

import java.util.List;

public record CleaningProposal(
    Long datasetId,
    String tableName,
    Long totalRows,
    List<CleaningIssue> issues,
    String summary
) {}