package com.example.agent.model.entity;

import com.example.agent.model.enums.DatasetStatus;
import java.time.LocalDateTime;

public class Dataset {

    private Long id;
    private Long userId;
    private String fileName;
    private String fileType;
    private String tableName;
    private String columnInfo;
    private Long rowCount;
    private LocalDateTime createdAt;
    private DatasetStatus status = DatasetStatus.READY;
    private String rawTableName;
    private String cleanedTableName;

    public Dataset() {}

    public Dataset(Long id, Long userId, String fileName, String fileType, String tableName,
                   String columnInfo, Long rowCount, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.tableName = tableName;
        this.columnInfo = columnInfo;
        this.rowCount = rowCount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getColumnInfo() { return columnInfo; }
    public void setColumnInfo(String columnInfo) { this.columnInfo = columnInfo; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public DatasetStatus getStatus() { return status; }
    public void setStatus(DatasetStatus status) { this.status = status; }
    public String getRawTableName() { return rawTableName; }
    public void setRawTableName(String rawTableName) { this.rawTableName = rawTableName; }
    public String getCleanedTableName() { return cleanedTableName; }
    public void setCleanedTableName(String cleanedTableName) { this.cleanedTableName = cleanedTableName; }
}
