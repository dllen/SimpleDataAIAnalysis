package com.example.agent.model.dto;

import java.util.List;

public record CleaningExecutionRequest(
    List<Integer> selectedIssueIndexes,
    List<String> customSqls,
    boolean saveAsNewDataset,
    String newDatasetName
) {}