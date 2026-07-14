package com.example.agent.service;

import com.example.agent.model.dto.CleaningIssue;
import com.example.agent.model.dto.ColumnInfo;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataQualityScanner {

    private final DuckDbService duckDbService;

    public DataQualityScanner(DuckDbService duckDbService) {
        this.duckDbService = duckDbService;
    }

    public List<CleaningIssue> scan(Long userId, String tableName) throws SQLException {
        List<CleaningIssue> issues = new ArrayList<>();
        List<ColumnInfo> columns = duckDbService.getSchema(userId, tableName);
        long totalRows = getRowCount(userId, tableName);

        for (ColumnInfo col : columns) {
            String name = col.name();
            long nullCount = countNull(userId, tableName, name);
            if (nullCount > 0) {
                String fixSql;
                if (looksLikeNumeric(col.type())) {
                    fixSql = "UPDATE " + tableName + " SET \"" + name + "\" = 0 WHERE \"" + name + "\" IS NULL";
                } else {
                    fixSql = "UPDATE " + tableName + " SET \"" + name + "\" = '' WHERE \"" + name + "\" IS NULL";
                }
                issues.add(new CleaningIssue(
                    "MISSING_VALUE",
                    name,
                    nullCount,
                    "列 " + name + " 存在 " + nullCount + " 个缺失值（共 " + totalRows + " 行）",
                    "将缺失值填充为 0（数值）或空字符串（文本）",
                    fixSql
                ));
            }

            long duplicateCount = countDuplicate(userId, tableName, name);
            if (duplicateCount > 0) {
                issues.add(new CleaningIssue(
                    "DUPLICATE_VALUE",
                    name,
                    duplicateCount,
                    "列 " + name + " 存在重复值",
                    "删除该列完全重复的行，保留第一条",
                    "DELETE FROM " + tableName + " WHERE ctid NOT IN (SELECT MIN(ctid) FROM " + tableName + " GROUP BY \"" + name + "\")"
                ));
            }

            if (looksLikeNumeric(col.type())) {
                long invalidCount = countInvalidNumeric(userId, tableName, name);
                if (invalidCount > 0) {
                    issues.add(new CleaningIssue(
                        "TYPE_MISMATCH",
                        name,
                        invalidCount,
                        "列 " + name + " 存在 " + invalidCount + " 个非数值内容",
                        "将无法转换为数值的内容置为 NULL",
                        "UPDATE " + tableName + " SET \"" + name + "\" = NULL WHERE TRY_CAST(\"" + name + "\" AS DOUBLE) IS NULL"
                    ));
                }
            }
        }

        return issues;
    }

    private long countNull(Long userId, String tableName, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE \"" + column + "\" IS NULL";
        return querySingleLong(userId, sql);
    }

    private long countDuplicate(Long userId, String tableName, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM (SELECT \"" + column + "\", COUNT(*) AS c FROM " + tableName + " GROUP BY \"" + column + "\" HAVING c > 1)";
        return querySingleLong(userId, sql);
    }

    private long countInvalidNumeric(Long userId, String tableName, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE TRY_CAST(\"" + column + "\" AS DOUBLE) IS NULL AND \"" + column + "\" IS NOT NULL";
        return querySingleLong(userId, sql);
    }

    private long querySingleLong(Long userId, String sql) throws SQLException {
        var result = duckDbService.executeReadOnlyQuery(userId, sql);
        if (result.rows().isEmpty()) return 0L;
        Object value = result.rows().get(0).get(0);
        return value == null ? 0L : ((Number) value).longValue();
    }

    private boolean looksLikeNumeric(String type) {
        String t = type.toUpperCase();
        return t.contains("INT") || t.contains("DOUBLE") || t.contains("FLOAT") || t.contains("DECIMAL") || t.contains("NUMERIC");
    }

    private long getRowCount(Long userId, String tableName) throws SQLException {
        var result = duckDbService.executeReadOnlyQuery(userId, "SELECT COUNT(*) FROM " + tableName);
        if (result.rows().isEmpty()) return 0L;
        Object value = result.rows().get(0).get(0);
        return value == null ? 0L : ((Number) value).longValue();
    }
}
