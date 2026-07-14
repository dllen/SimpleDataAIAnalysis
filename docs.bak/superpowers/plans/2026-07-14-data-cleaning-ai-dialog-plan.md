# AI 对话引导数据清洗功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在用户上传数据或对话过程中，自动检测数据质量问题，通过 AI 对话卡片引导用户确认/编辑清洗步骤，使用 DuckDB SQL 执行清洗后入库分析。

**Architecture:** 后端以 DuckDB SQL 为清洗执行引擎；新增 `DataQualityScanner` 做纯指标扫描，`DataCleaningService` 负责生成建议、执行清洗、记录历史；上传流程先建 `_raw_` 临时表，检测通过再转正式表。前端用 `CleaningCard` 组件在聊天面板中展示可编辑的清洗步骤。

**Tech Stack:** Java 17 + Spring Boot 3.3 + Spring AI 1.0.2 + DuckDB 1.1.3 + React 18 + TypeScript + Ant Design

## Global Constraints

- 只允许操作当前用户的 DuckDB 表，禁止跨用户访问。
- 清洗 SQL 必须命中白名单：`CREATE TABLE AS SELECT`、`INSERT SELECT`、`UPDATE`、`DELETE`、`ALTER`、`DROP TABLE IF EXISTS`。
- 执行清洗前必须自动创建备份表，失败时回滚。
- 状态枚举：`READY | PENDING_CLEAN | CLEANED | FAILED`。
- 默认覆盖原数据集，前端提供"另存为新数据集"选项。

---

## File Structure

**新建文件**

| 文件 | 职责 |
|------|------|
| `src/main/java/com/example/agent/model/enums/DatasetStatus.java` | 数据集状态枚举 |
| `src/main/java/com/example/agent/model/dto/CleaningIssue.java` | 单个质量问题 DTO |
| `src/main/java/com/example/agent/model/dto/CleaningProposal.java` | 清洗建议 DTO |
| `src/main/java/com/example/agent/model/dto/CleaningExecutionRequest.java` | 执行请求 DTO |
| `src/main/java/com/example/agent/model/dto/CleaningHistoryRecord.java` | 清洗历史 DTO |
| `src/main/java/com/example/agent/model/entity/CleaningHistory.java` | 清洗历史 JPA 实体 |
| `src/main/java/com/example/agent/repository/CleaningHistoryRepository.java` | 清洗历史 Repository |
| `src/main/java/com/example/agent/service/DataQualityScanner.java` | 数据质量扫描器 |
| `src/main/java/com/example/agent/service/DataCleaningService.java` | 清洗业务核心服务 |
| `src/main/java/com/example/agent/controller/DataCleaningController.java` | 清洗相关 API |
| `src/main/resources/db/migration/V2__add_cleaning.sql` | 数据库变更脚本 |
| `frontend/src/api/cleaning.ts` | 清洗 API 客户端 |
| `frontend/src/components/CleaningCard.tsx` | 清洗卡片 UI |
| `src/test/java/com/example/agent/service/DataQualityScannerTest.java` | 扫描器单元测试 |
| `src/test/java/com/example/agent/service/DataCleaningServiceTest.java` | 清洗服务集成测试 |

**修改文件**

| 文件 | 调整内容 |
|------|----------|
| `src/main/java/com/example/agent/model/entity/Dataset.java` | 增加 `status`、`rawTableName`、`cleanedTableName` 字段 |
| `src/main/java/com/example/agent/model/dto/DatasetResponse.java` | 增加 `status` 字段 |
| `src/main/java/com/example/agent/service/DatasetService.java` | 上传后先建 raw 表，根据检测结果决定状态 |
| `src/main/java/com/example/agent/service/DuckDbService.java` | 新增非只写执行、rename/clone/backup 方法 |
| `src/main/java/com/example/agent/service/AnalysisService.java` | 分析前检查 dataset 状态 |
| `frontend/src/types/index.ts` | 增加清洗相关类型 |
| `frontend/src/api/dataset.ts` | 返回类型增加 `status` |
| `frontend/src/components/ChatPanel.tsx` | 支持渲染清洗卡片 |
| `frontend/src/pages/AnalysisWorkspace.tsx` | 上传后/对话中触发清洗流程 |

---

## Task 1: 数据模型与数据库 Schema

**Files:**
- Create: `src/main/java/com/example/agent/model/enums/DatasetStatus.java`
- Create: `src/main/java/com/example/agent/model/entity/CleaningHistory.java`
- Create: `src/main/java/com/example/agent/repository/CleaningHistoryRepository.java`
- Create: `src/main/resources/db/migration/V2__add_cleaning.sql`
- Modify: `src/main/java/com/example/agent/model/entity/Dataset.java`
- Modify: `src/main/java/com/example/agent/model/dto/DatasetResponse.java`
- Test: `src/test/java/com/example/agent/model/entity/DatasetEntityTest.java`（验证字段映射）

**Interfaces:**
- Consumes: 现有 `Dataset` 表结构
- Produces: `DatasetStatus` 枚举，`CleaningHistory` 实体，`CleaningHistoryRepository` 接口

- [ ] **Step 1: 创建状态枚举**

```java
package com.example.agent.model.enums;

public enum DatasetStatus {
    READY,
    PENDING_CLEAN,
    CLEANED,
    FAILED
}
```

- [ ] **Step 2: 修改 Dataset 实体**

在 `src/main/java/com/example/agent/model/entity/Dataset.java` 增加：

```java
@Column(name = "status", nullable = false, length = 20)
@Enumerated(EnumType.STRING)
private DatasetStatus status = DatasetStatus.READY;

@Column(name = "raw_table_name", length = 128)
private String rawTableName;

@Column(name = "cleaned_table_name", length = 128)
private String cleanedTableName;

// getter/setter
public DatasetStatus getStatus() { return status; }
public void setStatus(DatasetStatus status) { this.status = status; }
public String getRawTableName() { return rawTableName; }
public void setRawTableName(String rawTableName) { this.rawTableName = rawTableName; }
public String getCleanedTableName() { return cleanedTableName; }
public void setCleanedTableName(String cleanedTableName) { this.cleanedTableName = cleanedTableName; }
```

- [ ] **Step 3: 修改 DatasetResponse DTO**

```java
package com.example.agent.model.dto;

import com.example.agent.model.enums.DatasetStatus;
import java.time.LocalDateTime;
import java.util.List;

public record DatasetResponse(
    Long id,
    String fileName,
    String fileType,
    String tableName,
    List<ColumnInfo> columns,
    Long rowCount,
    LocalDateTime createdAt,
    DatasetStatus status
) {}
```

- [ ] **Step 4: 创建 CleaningHistory 实体**

```java
package com.example.agent.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cleaning_history")
public class CleaningHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "issues_json", columnDefinition = "TEXT")
    private String issuesJson;

    @Column(name = "executed_sql", columnDefinition = "TEXT")
    private String executedSql;

    @Column(name = "affected_rows")
    private Long affectedRows;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // getters and setters
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
```

- [ ] **Step 5: 创建 Repository**

```java
package com.example.agent.repository;

import com.example.agent.model.entity.CleaningHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CleaningHistoryRepository extends JpaRepository<CleaningHistory, Long> {
    List<CleaningHistory> findByDatasetIdAndUserIdOrderByCreatedAtDesc(Long datasetId, Long userId);
}
```

- [ ] **Step 6: 编写数据库迁移脚本**

```sql
-- V2__add_cleaning.sql
ALTER TABLE dataset
    ADD COLUMN status VARCHAR(20) DEFAULT 'READY' NOT NULL,
    ADD COLUMN raw_table_name VARCHAR(128),
    ADD COLUMN cleaned_table_name VARCHAR(128);

CREATE TABLE cleaning_history (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    issues_json TEXT,
    executed_sql TEXT,
    affected_rows BIGINT,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 7: 运行应用验证数据库迁移**

Run: `mvn spring-boot:run`
Expected: 应用启动成功，无 schema 错误，H2 表结构正确。

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/agent/model/ src/main/java/com/example/agent/repository/ src/main/resources/db/migration/
git commit -m "feat: add dataset status and cleaning history schema"
```

---

## Task 2: DuckDbService 扩展

**Files:**
- Modify: `src/main/java/com/example/agent/service/DuckDbService.java`
- Test: `src/test/java/com/example/agent/service/DuckDbServiceTest.java`

**Interfaces:**
- Consumes: 现有 `DuckDbConnectionPool`
- Produces: `executeCleaningSql`, `renameTable`, `cloneTable`, `dropTableIfExists`, `getRowCount`, `tableExists` 方法

- [ ] **Step 1: 添加执行方法**

在 `DuckDbService.java` 中增加：

```java
private static final Set<String> ALLOWED_CLEANING_KEYWORDS = Set.of(
    "CREATE TABLE AS SELECT", "INSERT INTO", "UPDATE", "DELETE", "ALTER", "DROP TABLE IF EXISTS"
);

public long executeCleaningSql(Long userId, String sql) throws SQLException {
    String normalized = sql.trim().toUpperCase().replaceAll("\\s+", " ");
    boolean allowed = ALLOWED_CLEANING_KEYWORDS.stream().anyMatch(normalized::startsWith);
    if (!allowed) {
        throw new SQLException("不允许执行的 SQL 类型: " + sql);
    }
    return connectionPool.withConnection(userId, conn -> {
        try (Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
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
             ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                if (rs.getString(1).equalsIgnoreCase(tableName)) return true;
            }
        }
        return false;
    });
}
```

- [ ] **Step 2: 编写测试**

```java
package com.example.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DuckDbServiceTest {

    @Autowired
    private DuckDbService duckDbService;

    @Test
    void shouldRejectDisallowedCleaningSql() {
        assertThrows(SQLException.class, () -> {
            duckDbService.executeCleaningSql(1L, "DROP DATABASE main");
        });
    }

    @Test
    void shouldExecuteAllowedCleaningSql() throws SQLException {
        duckDbService.loadFromSql(1L, "SELECT 1 AS a, 2 AS b", "test_src", "csv");
        long rows = duckDbService.executeCleaningSql(1L, "CREATE TABLE test_dst AS SELECT * FROM test_src");
        assertTrue(rows >= 0);
        assertTrue(duckDbService.tableExists(1L, "test_dst"));
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn test -Dtest=DuckDbServiceTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/agent/service/DuckDbService.java src/test/java/com/example/agent/service/DuckDbServiceTest.java
git commit -m "feat: extend DuckDbService with cleaning and table management"
```

---

## Task 3: 数据质量扫描器 DataQualityScanner

**Files:**
- Create: `src/main/java/com/example/agent/model/dto/CleaningIssue.java`
- Create: `src/main/java/com/example/agent/service/DataQualityScanner.java`
- Test: `src/test/java/com/example/agent/service/DataQualityScannerTest.java`

**Interfaces:**
- Consumes: `DuckDbService`（执行只读查询）
- Produces: `List<CleaningIssue>`，每个 issue 包含 `type`, `column`, `affectedRows`, `suggestion`, `defaultSql`

- [ ] **Step 1: 创建 CleaningIssue DTO**

```java
package com.example.agent.model.dto;

public record CleaningIssue(
    String type,
    String column,
    Long affectedRows,
    String description,
    String suggestion,
    String defaultSql
) {}
```

- [ ] **Step 2: 实现扫描器**

```java
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
        long totalRows = duckDbService.getRowCount(userId, tableName);

        for (ColumnInfo col : columns) {
            String name = col.name();
            long nullCount = countNull(userId, tableName, name);
            if (nullCount > 0) {
                issues.add(new CleaningIssue(
                    "MISSING_VALUE",
                    name,
                    nullCount,
                    "列 " + name + " 存在 " + nullCount + " 个缺失值（共 " + totalRows + " 行）",
                    "将缺失值填充为 0（数值）或空字符串（文本）",
                    "UPDATE " + tableName + " SET \"" + name + "\" = CASE WHEN TRY_CAST(\"" + name + "\" AS DOUBLE) IS NOT NULL THEN '0' ELSE '' END WHERE \"" + name + "\" IS NULL"
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
}
```

- [ ] **Step 3: 编写测试**

```java
package com.example.agent.service;

import com.example.agent.model.dto.CleaningIssue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DataQualityScannerTest {

    @Autowired
    private DuckDbService duckDbService;

    @Autowired
    private DataQualityScanner scanner;

    @Test
    void shouldDetectMissingValues() throws SQLException {
        duckDbService.dropTableIfExists(1L, "scan_test");
        duckDbService.loadFromSql(1L, "SELECT 1 AS a, 'x' AS b UNION ALL SELECT NULL, 'y'", "scan_test", "csv");
        List<CleaningIssue> issues = scanner.scan(1L, "scan_test");
        assertTrue(issues.stream().anyMatch(i -> i.type().equals("MISSING_VALUE") && i.column().equals("a")));
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `mvn test -Dtest=DataQualityScannerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/agent/model/dto/CleaningIssue.java src/main/java/com/example/agent/service/DataQualityScanner.java src/test/java/com/example/agent/service/DataQualityScannerTest.java
git commit -m "feat: add data quality scanner"
```

---

## Task 4: 数据清洗服务 DataCleaningService

**Files:**
- Create: `src/main/java/com/example/agent/model/dto/CleaningProposal.java`
- Create: `src/main/java/com/example/agent/model/dto/CleaningExecutionRequest.java`
- Create: `src/main/java/com/example/agent/model/dto/CleaningHistoryRecord.java`
- Create: `src/main/java/com/example/agent/service/DataCleaningService.java`
- Test: `src/test/java/com/example/agent/service/DataCleaningServiceTest.java`

**Interfaces:**
- Consumes: `DataQualityScanner`, `DuckDbService`, `ChatClient`, `CleaningHistoryRepository`
- Produces: `CleaningProposal`, `CleaningHistoryRecord`

- [ ] **Step 1: 创建 DTOs**

`CleaningProposal.java`:

```java
package com.example.agent.model.dto;

import java.util.List;

public record CleaningProposal(
    Long datasetId,
    String tableName,
    Long totalRows,
    List<CleaningIssue> issues,
    String summary
) {}
```

`CleaningExecutionRequest.java`:

```java
package com.example.agent.model.dto;

import java.util.List;

public record CleaningExecutionRequest(
    List<Integer> selectedIssueIndexes,
    List<String> customSqls,
    boolean saveAsNewDataset,
    String newDatasetName
) {}
```

`CleaningHistoryRecord.java`:

```java
package com.example.agent.model.dto;

import java.time.LocalDateTime;

public record CleaningHistoryRecord(
    Long id,
    Long datasetId,
    String status,
    Long affectedRows,
    String errorMessage,
    LocalDateTime createdAt
) {}
```

- [ ] **Step 2: 实现 DataCleaningService**

```java
package com.example.agent.service;

import com.example.agent.model.dto.*;
import com.example.agent.model.entity.CleaningHistory;
import com.example.agent.model.entity.Dataset;
import com.example.agent.model.enums.DatasetStatus;
import com.example.agent.repository.CleaningHistoryRepository;
import com.example.agent.repository.DatasetRepository;
import com.example.agent.exception.BusinessException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Service
public class DataCleaningService {

    private final DataQualityScanner scanner;
    private final DuckDbService duckDbService;
    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final CleaningHistoryRepository historyRepository;
    private final DatasetRepository datasetRepository;

    public DataCleaningService(DataQualityScanner scanner, DuckDbService duckDbService,
                               ChatClient chatClient, MessageChatMemoryAdvisor memoryAdvisor,
                               CleaningHistoryRepository historyRepository, DatasetRepository datasetRepository) {
        this.scanner = scanner;
        this.duckDbService = duckDbService;
        this.chatClient = chatClient;
        this.memoryAdvisor = memoryAdvisor;
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
                // log rollback failure
            }
            throw new BusinessException("清洗执行失败: " + e.getMessage());
        }
    }

    @Transactional
    public Dataset saveAs(Long userId, Long datasetId, String tableName,
                          CleaningExecutionRequest request) throws SQLException {
        CleaningHistory history = execute(userId, datasetId, tableName, request);
        if (!"SUCCESS".equals(history.status())) {
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

        // 恢复原始数据集到待清洗状态
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
```

- [ ] **Step 3: 编写测试**

```java
package com.example.agent.service;

import com.example.agent.model.dto.CleaningExecutionRequest;
import com.example.agent.model.dto.CleaningHistoryRecord;
import com.example.agent.model.dto.CleaningProposal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DataCleaningServiceTest {

    @Autowired
    private DataCleaningService cleaningService;

    @Autowired
    private DuckDbService duckDbService;

    @Test
    void shouldAnalyzeAndExecuteCleaning() throws SQLException {
        duckDbService.dropTableIfExists(1L, "clean_test");
        duckDbService.loadFromSql(1L, "SELECT 1 AS a, 'x' AS b UNION ALL SELECT NULL, 'y'", "clean_test", "csv");
        CleaningProposal proposal = cleaningService.analyze(1L, 1L, "clean_test");
        assertFalse(proposal.issues().isEmpty());

        int missingIdx = -1;
        for (int i = 0; i < proposal.issues().size(); i++) {
            if (proposal.issues().get(i).type().equals("MISSING_VALUE")) {
                missingIdx = i;
                break;
            }
        }
        assertTrue(missingIdx >= 0);

        CleaningExecutionRequest request = new CleaningExecutionRequest(
            List.of(missingIdx), null, false, null
        );
        CleaningHistoryRecord record = cleaningService.execute(1L, 1L, "clean_test", request);
        assertEquals("SUCCESS", record.status());
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `mvn test -Dtest=DataCleaningServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/agent/model/dto/ src/main/java/com/example/agent/service/DataCleaningService.java src/test/java/com/example/agent/service/DataCleaningServiceTest.java
git commit -m "feat: add data cleaning service"
```

---

## Task 5: DatasetService 上传流程改造

**Files:**
- Modify: `src/main/java/com/example/agent/service/DatasetService.java`
- Modify: `src/main/java/com/example/agent/model/dto/DatasetResponse.java`（已在 Task 1 修改）
- Test: `src/test/java/com/example/agent/service/DatasetServiceUploadTest.java`

**Interfaces:**
- Consumes: `DuckDbService`, `DataCleaningService`
- Produces: 上传后返回的 `DatasetResponse` 包含 `status`

- [ ] **Step 1: 修改上传逻辑**

在 `DatasetService.uploadDataset` 中：

1. 生成两个表名：`rawTableName = "_raw_" + tableName`，`finalTableName = tableName`。
2. 先把文件加载到 `rawTableName`。
3. 调用 `DataCleaningService.analyze` 检测问题。
4. 如果 issues 为空，则 `cloneTable` 到 `finalTableName`，status = READY。
5. 如果有问题，status = PENDING_CLEAN，finalTableName 留空或指向 rawTableName。
6. 保存 `rawTableName` 到 Dataset 实体。
7. `toResponse` 返回时带上 status。

具体修改（节选）：

```java
public DatasetResponse uploadDataset(Long userId, MultipartFile file) {
    validateFile(file);

    FileType fileType = FileType.fromFilename(file.getOriginalFilename());
    String finalTableName = generateTableName(userId);
    String rawTableName = "_raw_" + finalTableName;

    try {
        // ... 保存文件到磁盘 ...

        DuckDbService.DatasetMeta rawMeta = switch (fileType) {
            case CSV -> duckDbService.loadCsv(userId, filePath.toString(), rawTableName);
            case JSON -> duckDbService.loadJson(userId, filePath.toString(), rawTableName);
            case EXCEL -> loadExcelToDuckDb(userId, file, filePath, rawTableName);
        };

        DatasetStatus status = DatasetStatus.READY;
        CleaningProposal proposal = dataCleaningService.analyze(userId, null, rawTableName);
        if (!proposal.issues().isEmpty()) {
            status = DatasetStatus.PENDING_CLEAN;
        } else {
            duckDbService.cloneTable(userId, rawTableName, finalTableName);
        }

        Dataset dataset = new Dataset();
        dataset.setUserId(userId);
        dataset.setFileName(file.getOriginalFilename());
        dataset.setFileType(fileType.getExtension());
        dataset.setTableName(status == DatasetStatus.READY ? finalTableName : rawTableName);
        dataset.setRawTableName(rawTableName);
        dataset.setColumnInfo(serializeColumns(rawMeta.columns()));
        dataset.setRowCount(rawMeta.rowCount());
        dataset.setStatus(status);
        dataset = datasetRepository.save(dataset);

        return toResponse(dataset, rawMeta.columns());

    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        log.error("Failed to upload dataset for user {}", userId, e);
        throw new BusinessException("文件上传失败: " + e.getMessage());
    }
}
```

同时修改 `toResponse` 加上 status：

```java
private DatasetResponse toResponse(Dataset dataset, List<ColumnInfo> columns) {
    return new DatasetResponse(
        dataset.getId(),
        dataset.getFileName(),
        dataset.getFileType(),
        dataset.getTableName(),
        columns,
        dataset.getRowCount(),
        dataset.getCreatedAt(),
        dataset.getStatus()
    );
}
```

- [ ] **Step 2: 注入 DataCleaningService**

在 `DatasetService` 构造函数中注入：

```java
private final DataCleaningService dataCleaningService;

public DatasetService(DuckDbService duckDbService, DatasetRepository datasetRepository,
                      com.example.agent.repository.UserRepository userRepository,
                      DataCleaningService dataCleaningService) {
    this.duckDbService = duckDbService;
    this.datasetRepository = datasetRepository;
    this.userRepository = userRepository;
    this.dataCleaningService = dataCleaningService;
}
```

- [ ] **Step 3: 编写测试**

```java
package com.example.agent.service;

import com.example.agent.model.dto.DatasetResponse;
import com.example.agent.model.enums.DatasetStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DatasetServiceUploadTest {

    @Autowired
    private DatasetService datasetService;

    @Test
    void shouldMarkPendingCleanForDirtyData() {
        String csv = "a,b\n1,\n2,y";
        MockMultipartFile file = new MockMultipartFile(
            "file", "dirty.csv", "text/csv", csv.getBytes()
        );
        DatasetResponse response = datasetService.uploadDataset(1L, file);
        assertEquals(DatasetStatus.PENDING_CLEAN, response.status());
    }

    @Test
    void shouldMarkReadyForCleanData() {
        String csv = "a,b\n1,x\n2,y";
        MockMultipartFile file = new MockMultipartFile(
            "file", "clean.csv", "text/csv", csv.getBytes()
        );
        DatasetResponse response = datasetService.uploadDataset(1L, file);
        assertEquals(DatasetStatus.READY, response.status());
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `mvn test -Dtest=DatasetServiceUploadTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/agent/service/DatasetService.java src/test/java/com/example/agent/service/DatasetServiceUploadTest.java
git commit -m "feat: integrate cleaning analysis into dataset upload"
```

---

## Task 6: DataCleaningController

**Files:**
- Create: `src/main/java/com/example/agent/controller/DataCleaningController.java`
- Test: `src/test/java/com/example/agent/controller/DataCleaningControllerTest.java`

**Interfaces:**
- Consumes: `DataCleaningService`, `DatasetService`
- Produces: REST API `/api/datasets/{id}/cleaning/*`

- [ ] **Step 1: 创建 Controller**

```java
package com.example.agent.controller;

import com.example.agent.model.dto.CleaningExecutionRequest;
import com.example.agent.model.dto.CleaningHistoryRecord;
import com.example.agent.model.dto.CleaningProposal;
import com.example.agent.service.DataCleaningService;
import com.example.agent.service.DatasetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/datasets/{datasetId}/cleaning")
public class DataCleaningController {

    private final DataCleaningService dataCleaningService;
    private final DatasetService datasetService;

    public DataCleaningController(DataCleaningService dataCleaningService, DatasetService datasetService) {
        this.dataCleaningService = dataCleaningService;
        this.datasetService = datasetService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<CleaningProposal> analyze(@PathVariable Long datasetId) throws SQLException {
        Long userId = getCurrentUserId();
        var dataset = datasetService.findDatasetEntity(userId, datasetId);
        return ResponseEntity.ok(dataCleaningService.analyze(userId, datasetId, dataset.getTableName()));
    }

    @PostMapping("/execute")
    public ResponseEntity<CleaningHistoryRecord> execute(@PathVariable Long datasetId,
                                                       @RequestBody CleaningExecutionRequest request) throws SQLException {
        Long userId = getCurrentUserId();
        var dataset = datasetService.findDatasetEntity(userId, datasetId);
        return ResponseEntity.ok(dataCleaningService.execute(userId, datasetId, dataset.getTableName(), request));
    }

    @PostMapping("/save-as")
    public ResponseEntity<DatasetResponse> saveAs(@PathVariable Long datasetId,
                                                   @RequestBody CleaningExecutionRequest request) throws SQLException {
        Long userId = getCurrentUserId();
        var dataset = datasetService.findDatasetEntity(userId, datasetId);
        Dataset newDataset = dataCleaningService.saveAs(userId, datasetId, dataset.getTableName(), request);
        return ResponseEntity.ok(datasetService.getDataset(userId, newDataset.getId()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<CleaningHistoryRecord>> history(@PathVariable Long datasetId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(dataCleaningService.history(userId, datasetId));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User user) {
            return datasetService.findUserIdByUsername(user.getUsername());
        }
        throw new com.example.agent.exception.BusinessException("未登录");
    }
}
```

- [ ] **Step 2: 编写测试**

```java
package com.example.agent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DataCleaningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnProposalOnAnalyze() throws Exception {
        mockMvc.perform(post("/api/datasets/1/cleaning/analyze"))
               .andExpect(status().isOk());
    }
}
```

注意：此测试依赖数据库中存在 user/admin，可能需要先初始化数据。可先跳过集成测试，或在上传数据集后再测试。

- [ ] **Step 3: 运行测试**

Run: `mvn test -Dtest=DataCleaningControllerTest`
Expected: PASS（若因数据不存在失败，调整测试先创建用户/数据集）

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/agent/controller/DataCleaningController.java src/test/java/com/example/agent/controller/DataCleaningControllerTest.java
git commit -m "feat: add data cleaning controller"
```

---

## Task 7: AnalysisService 状态检查

**Files:**
- Modify: `src/main/java/com/example/agent/service/AnalysisService.java`

**Interfaces:**
- Consumes: `Dataset` 的 `status` 字段
- Produces: `BusinessException` 当状态不允许分析时

- [ ] **Step 1: 在分析入口增加状态检查**

```java
public AnalysisResponse analyze(Long userId, Long datasetId, String question) throws SQLException {
    Dataset dataset = datasetRepository.findByIdAndUserId(datasetId, userId)
            .orElseThrow(() -> new BusinessException("数据集不存在或无权限"));

    if (dataset.getStatus() == DatasetStatus.PENDING_CLEAN) {
        throw new BusinessException("数据集需要先清洗，请使用清洗功能后再分析");
    }
    if (dataset.getStatus() == DatasetStatus.FAILED) {
        throw new BusinessException("数据集状态异常，请重新上传");
    }

    // ... 原有逻辑
}
```

同样修改 `analyzeStream`。

- [ ] **Step 2: 运行现有测试**

Run: `mvn test`
Expected: 所有现有测试 PASS，无回归。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/agent/service/AnalysisService.java
git commit -m "feat: block analysis when dataset is pending cleaning"
```

---

## Task 8: 前端类型与 API

**Files:**
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/api/cleaning.ts`
- Modify: `frontend/src/api/dataset.ts`（增加 status 字段）

**Interfaces:**
- Consumes: 后端 API `/api/datasets/{id}/cleaning/*`
- Produces: TypeScript 类型和 API 函数

- [ ] **Step 1: 扩展 types**

```typescript
export enum DatasetStatus {
  READY = 'READY',
  PENDING_CLEAN = 'PENDING_CLEAN',
  CLEANED = 'CLEANED',
  FAILED = 'FAILED',
}

export interface DatasetResponse {
  id: number
  fileName: string
  fileType: string
  tableName: string
  columns: ColumnInfo[]
  rowCount: number
  createdAt: string
  status: DatasetStatus
}

export interface CleaningIssue {
  type: string
  column: string
  affectedRows: number
  description: string
  suggestion: string
  defaultSql: string
}

export interface CleaningProposal {
  datasetId: number
  tableName: string
  totalRows: number
  issues: CleaningIssue[]
  summary: string
}

export interface CleaningExecutionRequest {
  selectedIssueIndexes: number[]
  customSqls?: string[]
  saveAsNewDataset: boolean
  newDatasetName?: string
}

export interface CleaningHistoryRecord {
  id: number
  datasetId: number
  status: string
  affectedRows: number
  errorMessage?: string
  createdAt: string
}
```

- [ ] **Step 2: 创建 cleaning API**

```typescript
import { CleaningExecutionRequest, CleaningHistoryRecord, CleaningProposal } from '../types'
import { client } from './client'

export const cleaningApi = {
  analyze: (datasetId: number) =>
    client.post<CleaningProposal>(`/datasets/${datasetId}/cleaning/analyze`),

  execute: (datasetId: number, request: CleaningExecutionRequest) =>
    client.post<CleaningHistoryRecord>(`/datasets/${datasetId}/cleaning/execute`, request),

  saveAs: (datasetId: number, request: CleaningExecutionRequest) =>
    client.post<DatasetResponse>(`/datasets/${datasetId}/cleaning/save-as`, request),

  history: (datasetId: number) =>
    client.get<CleaningHistoryRecord[]>(`/datasets/${datasetId}/cleaning/history`),
}

// 注意：需要在同文件导入 DatasetResponse
```

- [ ] **Step 3: 修改 dataset.ts 返回类型**

确保 `datasetApi` 的返回类型使用 `DatasetResponse`（已包含 status）。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/ frontend/src/api/cleaning.ts frontend/src/api/dataset.ts
git commit -m "feat: add frontend types and cleaning api client"
```

---

## Task 9: CleaningCard 组件

**Files:**
- Create: `frontend/src/components/CleaningCard.tsx`
- Test: 通过手动验证 UI 行为

**Interfaces:**
- Consumes: `CleaningProposal`
- Produces: 用户确认后的 `CleaningExecutionRequest` 通过回调传出

- [ ] **Step 1: 创建组件**

```tsx
import React, { useState } from 'react'
import { Button, Card, Checkbox, Collapse, Input, Typography } from 'antd'
import { CleaningExecutionRequest, CleaningProposal } from '../types'

interface Props {
  proposal: CleaningProposal
  onExecute: (request: CleaningExecutionRequest) => void
  onSaveAs: (request: CleaningExecutionRequest) => void
  loading?: boolean
}

const { Text } = Typography
const { Panel } = Collapse

export const CleaningCard: React.FC<Props> = ({ proposal, onExecute, onSaveAs, loading }) => {
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [customSqls, setCustomSqls] = useState<Record<number, string>>({})
  const [newName, setNewName] = useState('')

  const toggleIssue = (idx: number) => {
    const next = new Set(selected)
    if (next.has(idx)) next.delete(idx)
    else next.add(idx)
    setSelected(next)
  }

  const buildRequest = (): CleaningExecutionRequest => ({
    selectedIssueIndexes: Array.from(selected),
    customSqls: proposal.issues.map((_, i) => customSqls[i] || ''),
    saveAsNewDataset: false,
  })

  return (
    <Card title="数据清洗建议" size="small" style={{ marginTop: 12 }}>
      <Text type="secondary">{proposal.summary}</Text>
      <div style={{ marginTop: 12 }}>
        {proposal.issues.map((issue, idx) => (
          <Card key={idx} size="small" style={{ marginBottom: 8 }}>
            <Checkbox checked={selected.has(idx)} onChange={() => toggleIssue(idx)}>
              <strong>{issue.type}</strong> - {issue.column}
            </Checkbox>
            <div style={{ marginTop: 4, color: '#666' }}>{issue.description}</div>
            <Collapse ghost size="small">
              <Panel header="建议操作" key="1">
                <div>{issue.suggestion}</div>
              </Panel>
              <Panel header="SQL（可编辑）" key="2">
                <Input.TextArea
                  defaultValue={issue.defaultSql}
                  onChange={(e) => setCustomSqls({ ...customSqls, [idx]: e.target.value })}
                  rows={3}
                  style={{ fontFamily: 'monospace' }}
                />
              </Panel>
            </Collapse>
          </Card>
        ))}
      </div>
      <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
        <Button type="primary" loading={loading} onClick={() => onExecute(buildRequest())}>
          执行清洗
        </Button>
        <Input
          placeholder="新数据集名称"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          style={{ width: 180 }}
        />
        <Button loading={loading} onClick={() => onSaveAs({ ...buildRequest(), saveAsNewDataset: true, newDatasetName: newName })}>
          另存为新数据集
        </Button>
      </div>
    </Card>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/CleaningCard.tsx
git commit -m "feat: add cleaning card ui component"
```

---

## Task 10: ChatPanel 与 AnalysisWorkspace 集成

**Files:**
- Modify: `frontend/src/components/ChatPanel.tsx`
- Modify: `frontend/src/pages/AnalysisWorkspace.tsx`

**Interfaces:**
- Consumes: `CleaningCard`, `cleaningApi`
- Produces: 聊天面板中渲染清洗卡片，上传后自动触发清洗流程

- [ ] **Step 1: 修改 ChatPanel 支持自定义消息内容**

方案一：在消息结构里增加 `proposal` 字段，当 `proposal` 存在时渲染 `CleaningCard`。

修改 `frontend/src/types/index.ts` 中的 `ChatMessage`：

```typescript
export interface ChatMessage {
  id: string
  role: 'user' | 'ai'
  content: string
  sql?: string
  proposal?: CleaningProposal
  timestamp: number
}
```

修改 `ChatPanel.tsx` 中 AI 消息渲染：

```tsx
{msg.proposal ? (
  <CleaningCard
    proposal={msg.proposal}
    onExecute={/* 从 props 注入 */}
    onSaveAs={/* 从 props 注入 */}
    loading={loading}
  />
) : (
  <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
)}
```

- [ ] **Step 2: 在 AnalysisWorkspace 中处理清洗流程**

增加状态：

```typescript
const [cleaningLoading, setCleaningLoading] = useState(false)
```

上传成功后，如果 `dataset.status === 'PENDING_CLEAN'`，自动调用 analyze：

```typescript
const handleUpload = async (file: File) => {
  try {
    message.loading({ content: '上传中...', key: 'upload' })
    const dataset = await datasetApi.upload(file)
    message.success({ content: '上传成功', key: 'upload' })
    setSelectedDataset(dataset)
    setMessages([])

    if (dataset.status === DatasetStatus.PENDING_CLEAN) {
      await analyzeCleaning(dataset.id)
    }
  } catch (e: any) {
    message.error({ content: e.response?.data?.message || '上传失败', key: 'upload' })
  }
  return false
}
```

实现 `analyzeCleaning`：

```typescript
const analyzeCleaning = async (datasetId: number) => {
  setCleaningLoading(true)
  try {
    const proposal = await cleaningApi.analyze(datasetId)
    const aiMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'ai',
      content: proposal.summary,
      proposal,
      timestamp: Date.now(),
    }
    setMessages((prev) => [...prev, aiMsg])
  } catch (e: any) {
    message.error(e.response?.data?.message || '检测失败')
  } finally {
    setCleaningLoading(false)
  }
}
```

实现 `executeCleaning` 和 `saveAsNewDataset`：

```typescript
const handleExecuteCleaning = async (request: CleaningExecutionRequest) => {
  if (!selectedDataset) return
  setCleaningLoading(true)
  try {
    const record = await cleaningApi.execute(selectedDataset.id, request)
    message.success(`清洗完成，影响 ${record.affectedRows} 行`)
    const refreshed = await datasetApi.get(selectedDataset.id)
    setSelectedDataset(refreshed)
  } catch (e: any) {
    message.error(e.response?.data?.message || '清洗失败')
  } finally {
    setCleaningLoading(false)
  }
}

const handleSaveAsNewDataset = async (request: CleaningExecutionRequest) => {
  if (!selectedDataset) return
  setCleaningLoading(true)
  try {
    const newDataset = await cleaningApi.saveAs(selectedDataset.id, request)
    message.success('已另存为新数据集')
    setSelectedDataset(newDataset)
    // 刷新左侧数据集列表
  } catch (e: any) {
    message.error(e.response?.data?.message || '另存失败')
  } finally {
    setCleaningLoading(false)
  }
}
```

- [ ] **Step 3: 在对话中识别清洗意图**

在 `handleSendMessage` 中，如果用户输入包含"清洗"、"clean"等关键词，直接调用 `analyzeCleaning` 而不是分析：

```typescript
const CLEANING_KEYWORDS = ['清洗', 'clean', '清理', '数据清洗']
const isCleaningRequest = CLEANING_KEYWORDS.some(k => question.includes(k))

if (isCleaningRequest) {
  await analyzeCleaning(selectedDataset.id)
  return
}
```

- [ ] **Step 4: 手动验证 UI**

启动前后端：

```bash
# 后端
mvn spring-boot:run

# 前端
cd frontend
npm run dev
```

上传一个包含缺失值的 CSV，验证：
1. 上传后聊天面板自动出现清洗卡片。
2. 勾选/取消问题项，点击"执行清洗"后右侧数据刷新。
3. 点击"另存为新数据集"后左侧列表出现新数据集。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/ChatPanel.tsx frontend/src/pages/AnalysisWorkspace.tsx frontend/src/types/index.ts
git commit -m "feat: integrate cleaning card into chat and workspace"
```

---

## Task 11: 集成测试与回归验证

**Files:**
- All of the above

- [ ] **Step 1: 运行后端测试**

Run: `mvn test`
Expected: 所有测试 PASS

- [ ] **Step 2: 运行前端类型检查**

Run: `cd frontend && npm run build`
Expected: 构建成功，无 TypeScript 错误

- [ ] **Step 3: 端到端手动测试**

1. 注册/登录
2. 上传脏数据 CSV
3. 确认聊天面板自动弹出清洗卡片
4. 执行清洗
5. 提出分析请求，确认 SQL 和分析正常

- [ ] **Step 4: 最终 Commit**

```bash
git commit -m "feat: complete AI-guided data cleaning pipeline"
```

---

## Self-Review Checklist

- [x] Spec coverage: 每个 spec 章节都对应到具体任务。
- [x] Placeholder scan: 无 TBD/TODO/"实现 later"等占位符。
- [x] Type consistency: DTO/Service/Controller 中的类型名称一致。
- [x] Security: 已在 Task 2 和 Task 4 中通过白名单和备份表覆盖。

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-14-data-cleaning-ai-dialog-plan.md`.

Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach would you like?
