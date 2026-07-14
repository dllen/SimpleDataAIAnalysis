package com.example.agent.service;

import com.example.agent.model.dto.ColumnInfo;
import com.example.agent.model.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DuckDbService {

    private static final Logger log = LoggerFactory.getLogger(DuckDbService.class);
    private static final int MAX_RESULT_ROWS = 10000;
    private static final Set<String> ALLOWED_CLEANING_KEYWORDS = Set.of(
        "CREATE TABLE AS SELECT", "INSERT INTO SELECT", "UPDATE", "DELETE", "ALTER", "DROP TABLE IF EXISTS"
    );

    private final DuckDbConnectionPool connectionPool;

    public DuckDbService(DuckDbConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public record DatasetMeta(String tableName, String fileType, long rowCount, List<ColumnInfo> columns) {}

    public DatasetMeta loadCsv(Long userId, String filePath, String tableName) throws SQLException {
        return connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE " + tableName + " AS FROM read_csv('" + filePath + "', auto_detect=true)");
            }
            return loadDatasetMeta(userId, tableName, "csv");
        });
    }

    public DatasetMeta loadJson(Long userId, String filePath, String tableName) throws SQLException {
        return connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE " + tableName + " AS FROM read_json_auto('" + filePath + "')");
            }
            return loadDatasetMeta(userId, tableName, "json");
        });
    }

    public DatasetMeta loadFromSql(Long userId, String sql, String tableName, String fileType) throws SQLException {
        return connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE " + tableName + " AS " + sql);
            }
            return loadDatasetMeta(userId, tableName, fileType);
        });
    }

    private DatasetMeta loadDatasetMeta(Long userId, String tableName, String fileType) throws SQLException {
        List<ColumnInfo> columns = getSchema(userId, tableName);
        long rowCount = getRowCount(userId, tableName);
        return new DatasetMeta(tableName, fileType, rowCount, columns);
    }

    public long getRowCount(Long userId, String tableName) throws SQLException {
        return connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0L;
        });
    }

    public List<ColumnInfo> getSchema(Long userId, String tableName) throws SQLException {
        return connectionPool.withConnection(userId, conn -> {
            List<ColumnInfo> columns = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("DESCRIBE " + tableName)) {
                while (rs.next()) {
                    String name = rs.getString("column_name");
                    String type = rs.getString("column_type");
                    columns.add(new ColumnInfo(name, type, true));
                }
            }
            return columns;
        });
    }

    public QueryResult executeReadOnlyQuery(Long userId, String sql) throws SQLException {
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("WITH") && !trimmed.startsWith("EXPLAIN")
            && !trimmed.startsWith("DESCRIBE") && !trimmed.startsWith("SHOW")) {
            throw new SQLException("只允许执行查询语句");
        }

        int semicolonIndex = sql.indexOf(';');
        if (semicolonIndex != -1 && semicolonIndex != sql.length() - 1) {
            throw new SQLException("不允许执行多条语句");
        }

        return connectionPool.withConnection(userId, conn -> {
            long start = System.currentTimeMillis();
            try (Statement stmt = conn.createStatement()) {
                stmt.setMaxRows(MAX_RESULT_ROWS);
                ResultSet rs = stmt.executeQuery(sql);
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }

                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }

                long executionTime = System.currentTimeMillis() - start;
                return new QueryResult(columns, rows, rows.size(), executionTime);
            }
        });
    }

    public QueryResult previewData(Long userId, String tableName) throws SQLException {
        return executeReadOnlyQuery(userId, "SELECT * FROM " + tableName + " LIMIT 100");
    }

    public void dropTable(Long userId, String tableName) throws SQLException {
        connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
            }
        });
    }

    public List<String> listTables(Long userId) throws SQLException {
        return connectionPool.withConnection(userId, conn -> {
            List<String> tables = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            return tables;
        });
    }

    public long executeCleaningSql(Long userId, String sql) throws SQLException {
        String normalized = sql.trim().toUpperCase().replaceAll("\\s+", " ");
        boolean allowed = ALLOWED_CLEANING_KEYWORDS.stream().anyMatch(kw -> {
            if (kw.equals("CREATE TABLE AS SELECT")) {
                return normalized.startsWith("CREATE TABLE") && normalized.contains("AS SELECT");
            }
            return normalized.startsWith(kw);
        });
        if (!allowed) {
            throw new SQLException("不允许执行的 SQL 类型: " + sql);
        }
        return connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement()) {
                return (long) stmt.executeUpdate(sql);
            }
        });
    }

    public void cloneTable(Long userId, String sourceTable, String targetTable) throws SQLException {
        connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE " + targetTable + " AS SELECT * FROM " + sourceTable);
            }
        });
    }

    public void renameTable(Long userId, String oldName, String newName) throws SQLException {
        connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE " + oldName + " RENAME TO " + newName);
            }
        });
    }

    public void dropTableIfExists(Long userId, String tableName) throws SQLException {
        connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
            }
        });
    }

    public boolean tableExists(Long userId, String tableName) throws SQLException {
        return connectionPool.withConnection(userId, conn -> {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'main'")) {
                while (rs.next()) {
                    if (rs.getString("table_name").equalsIgnoreCase(tableName)) return true;
                }
            }
            return false;
        });
    }
}
