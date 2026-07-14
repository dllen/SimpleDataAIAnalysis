package com.example.agent.service;

import com.example.agent.model.dto.*;
import com.example.agent.model.entity.CleaningHistory;
import com.example.agent.model.entity.Dataset;
import com.example.agent.model.enums.DatasetStatus;
import com.example.agent.repository.CleaningHistoryRepository;
import com.example.agent.repository.DatasetRepository;
import com.example.agent.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Service
public class DataCleaningService {

    private final DataQualityScanner scanner;
    private final DuckDbService duckDbService;
    private final CleaningHistoryRepository historyRepository;
    private final DatasetRepository datasetRepository;

    public DataCleaningService(DataQualityScanner scanner, DuckDbService duckDbService,
                               CleaningHistoryRepository historyRepository, DatasetRepository datasetRepository) {
        this.scanner = scanner;
        this.duckDbService = duckDbService;
        this.historyRepository = historyRepository;
        this.datasetRepository = datasetRepository;
    }

    public CleaningProposal analyze(Long userId, Long datasetId, String tableName) throws SQLException {
        List<CleaningIssue> issues = scanner.scan(userId, tableName);
        long totalRows = duckDbService.getRowCount(userId, tableName);
        String summary = generateSummary(issues, totalRows);
        return new CleaningProposal(datasetId, tableName, totalRows, issues, summary);
    }

    private String generateSummary(List<CleaningIssue> issues, long totalRows) {
        if (issues.isEmpty()) {
            return "未检测到明显的数据质量问题，可以直接分析。";
        }
        return "检测到 " + issues.size() + " 类数据质量问题，建议清洗后再分析。";
    }

    @Transactional
    public CleaningHistoryRecord execute(Long userId, Long datasetId, String tableName,
                                         CleaningExecutionRequest request) throws SQLException {
        CleaningHistory history = new CleaningHistory();
        history.setDatasetId(datasetId);
        history.setUserId(userId);
        history.setStatus("EXECUTING");
        history = historyRepository.save(history);

        String backupTable = "_backup_" + tableName;
        try {
            duckDbService.cloneTable(userId, tableName, backupTable);

            StringBuilder executedSql = new StringBuilder();
            long affectedRows = 0;

            if (request.selectedIssueIndexes() != null && !request.selectedIssueIndexes().isEmpty()) {
                CleaningProposal proposal = analyze(userId, datasetId, tableName);
                for (int idx : request.selectedIssueIndexes()) {
                    CleaningIssue issue = proposal.issues().get(idx);
                    String sql = issue.defaultSql();
                    if (request.customSqls() != null && request.customSqls().size() > idx) {
                        String custom = request.customSqls().get(idx);
                        if (custom != null && !custom.isBlank()) sql = custom;
                    }
                    long rows = duckDbService.executeCleaningSql(userId, sql);
                    affectedRows += rows;
                    executedSql.append(sql).append(";\n");
                }
            }

            history.setExecutedSql(executedSql.toString());
            history.setAffectedRows(affectedRows);
            history.setStatus("SUCCESS");
            historyRepository.save(history);

            Dataset dataset = datasetRepository.findById(datasetId)
                    .orElseThrow(() -> new BusinessException("数据集不存在"));
            dataset.setStatus(DatasetStatus.CLEANED);
            dataset.setCleanedTableName(tableName);
            datasetRepository.save(dataset);

            duckDbService.dropTableIfExists(userId, backupTable);
            return toRecord(history);

        } catch (Exception e) {
            history.setStatus("FAILED");
            history.setErrorMessage(e.getMessage());
            historyRepository.save(history);
            try {
                if (duckDbService.tableExists(userId, backupTable)) {
                    duckDbService.dropTableIfExists(userId, tableName);
                    duckDbService.renameTable(userId, backupTable, tableName);
                }
            } catch (SQLException ex) {
            }
            throw new BusinessException("清洗执行失败: " + e.getMessage());
        }
    }

    @Transactional
    public Dataset saveAs(Long userId, Long datasetId, String tableName,
                          CleaningExecutionRequest request) throws SQLException {
        CleaningHistoryRecord record = execute(userId, datasetId, tableName, request);
        if (!"SUCCESS".equals(record.status())) {
            throw new BusinessException("清洗失败，无法另存");
        }

        Dataset source = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new BusinessException("数据集不存在"));

        String newTableName = tableName + "_cleaned_" + System.currentTimeMillis();
        String newRawTableName = "_raw_" + newTableName;
        duckDbService.cloneTable(userId, tableName, newTableName);
        duckDbService.renameTable(userId, tableName, newRawTableName);

        Dataset newDataset = new Dataset();
        newDataset.setUserId(userId);
        newDataset.setFileName("(cleaned) " + source.getFileName());
        newDataset.setFileType(source.getFileType());
        newDataset.setTableName(newTableName);
        newDataset.setRawTableName(newRawTableName);
        newDataset.setColumnInfo(source.getColumnInfo());
        newDataset.setRowCount(source.getRowCount());
        newDataset.setStatus(DatasetStatus.CLEANED);
        newDataset = datasetRepository.save(newDataset);

        String backupTable = "_backup_" + tableName;
        if (duckDbService.tableExists(userId, backupTable)) {
            duckDbService.dropTableIfExists(userId, tableName);
            duckDbService.renameTable(userId, backupTable, tableName);
        }
        source.setStatus(DatasetStatus.PENDING_CLEAN);
        datasetRepository.save(source);

        return newDataset;
    }

    private CleaningHistoryRecord toRecord(CleaningHistory h) {
        return new CleaningHistoryRecord(
            h.getId(), h.getDatasetId(), h.getStatus(),
            h.getAffectedRows(), h.getErrorMessage(), h.getCreatedAt()
        );
    }

    public List<CleaningHistoryRecord> history(Long userId, Long datasetId) {
        return historyRepository.findByDatasetIdAndUserIdOrderByCreatedAtDesc(datasetId, userId)
                .stream().map(this::toRecord).toList();
    }
}