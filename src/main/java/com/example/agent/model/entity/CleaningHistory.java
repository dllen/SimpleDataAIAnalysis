package com.example.agent.model.entity;

import java.time.LocalDateTime;

public class CleaningHistory {

    private Long id;
    private Long datasetId;
    private Long userId;
    private String issuesJson;
    private String executedSql;
    private Long affectedRows;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;

    public CleaningHistory() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getIssuesJson() { return issuesJson; }
    public void setIssuesJson(String issuesJson) { this.issuesJson = issuesJson; }
    public String getExecutedSql() { return executedSql; }
    public void setExecutedSql(String executedSql) { this.executedSql = executedSql; }
    public Long getAffectedRows() { return affectedRows; }
    public void setAffectedRows(Long affectedRows) { this.affectedRows = affectedRows; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
