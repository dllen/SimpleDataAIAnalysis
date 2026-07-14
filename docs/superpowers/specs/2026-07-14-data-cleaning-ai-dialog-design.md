# AI 对话引导数据清洗功能设计文档

## 1. 背景与目标

当前系统用户上传 CSV/Excel/JSON 后，会直接把文件加载到 DuckDB 正式表并进入分析阶段。当原始数据存在格式错乱、缺失值、异常值、类型错误、列名不规范等问题时，AI 生成的 SQL 和分析结果质量会显著下降。

本设计引入** AI 对话引导的数据清洗流程**：上传后或对话中，系统自动检测数据质量问题，通过聊天面板向用户展示清洗建议，用户可一键确认、编辑或取消具体步骤，系统自动执行 DuckDB SQL 清洗脚本，最终把清洗后的数据用于分析。

## 2. 需求摘要

- **触发时机**：上传后自动检测；对话中也可随时发起。
- **覆盖范围**：格式/解析类、内容质量类、结构整理类问题。
- **执行方式**：默认全自动生成并执行 DuckDB SQL 清洗脚本；用户可干预每一步。
- **保存方式**：默认覆盖原数据集；支持另存为新数据集。

## 3. 整体流程

```
用户上传文件
  │
  ▼
[解析阶段] DatasetService 尝试把文件加载到 DuckDB 临时表 _raw_xxx
  │
  ▼
[质量检测阶段] DataCleaningService 扫描原始表，生成质量报告
  │            （缺失率、异常值、重复行、类型推断、列名规范化等）
  │
  ▼
  ├─ 无问题：直接转正式表，dataset 状态为 READY
  │
  ▼
  └─ 有问题：dataset 状态为 PENDING_CLEAN，AI 在聊天面板主动推送
           "发现 X 个问题，建议清洗" 卡片
              │
              ▼
        用户确认 / 编辑 / 取消清洗步骤
              │
              ▼
        [执行阶段] DataCleaningService 执行 SQL 清洗脚本
              │
              ▼
        状态变为 CLEANED，正式表可分析
              │
              ▼
        用户可选择 "覆盖原数据集" 或 "另存为新数据集"
```

## 4. 后端架构

### 4.1 新增 DTO

| DTO | 说明 |
|-----|------|
| `CleaningIssue` | 单个质量问题：类型、列名、影响行数、建议操作、默认 SQL 片段 |
| `CleaningProposal` | 一次完整清洗建议：问题列表、预览 SQL、预计影响行数 |
| `CleaningExecutionRequest` | 用户确认后的执行请求：选中的步骤、自定义 SQL 调整 |
| `CleaningHistoryRecord` | 单次清洗历史记录 |

### 4.2 新增 Service

**`DataCleaningService`**
1. 对原始表执行质量扫描，生成 `CleaningProposal`。
2. 调用 ChatClient 根据扫描结果生成清洗建议。
3. 执行用户确认后的 SQL 清洗脚本。
4. 记录清洗历史到 `cleaning_history` 表。

**`DataQualityScanner`**
- 纯 Java 实现，不依赖 LLM。
- 输出可量化指标：缺失率、异常值、重复行数、类型一致性、列名合法性等。

### 4.3 调整现有 Service

| 服务 | 调整内容 |
|------|----------|
| `DatasetService` | 上传后先建 `_raw_xxx` 临时表；根据检测结果决定状态为 READY 或 PENDING_CLEAN。 |
| `DuckDbService` | 新增 `executeCleaningSql`、`renameTable`、`cloneTable` 等方法。 |
| `AnalysisService` | dataset 状态不是 READY/CLEANED 时，返回友好提示引导用户先清洗。 |

### 4.4 新增 Controller

**`DataCleaningController`**

| 接口 | 作用 |
|------|------|
| `POST /api/datasets/{id}/cleaning/analyze` | 生成或刷新清洗建议 |
| `POST /api/datasets/{id}/cleaning/execute` | 执行用户确认的清洗步骤 |
| `POST /api/datasets/{id}/cleaning/save-as` | 把清洗结果另存为新数据集 |
| `GET /api/datasets/{id}/cleaning/history` | 查询清洗历史 |

## 5. 数据模型

### 5.1 扩展 `dataset` 表

```sql
ALTER TABLE dataset
  ADD COLUMN status VARCHAR(20) DEFAULT 'READY',
  ADD COLUMN raw_table_name VARCHAR(128),
  ADD COLUMN cleaned_table_name VARCHAR(128);
```

状态枚举：`READY | PENDING_CLEAN | CLEANED | FAILED`。

### 5.2 新增 `cleaning_history` 表

```sql
CREATE TABLE cleaning_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    issues_json TEXT,
    executed_sql TEXT,
    affected_rows BIGINT,
    status VARCHAR(20),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 6. 前端交互

### 6.1 上传后自动检测

- 上传成功后，若状态为 `PENDING_CLEAN`，AI 自动在聊天面板推送清洗邀请消息。
- 消息中包含可展开的 `CleaningCard` 组件：
  - 问题摘要（如"发现 3 列缺失值、1 列类型异常"）
  - 建议操作清单（每行可勾选）
  - 每项操作可展开查看默认 SQL
  - "执行清洗" 和 "另存为新数据集" 按钮
  - 用户可编辑 SQL 或取消某一步

### 6.2 对话中发起清洗

- 用户输入"帮我清洗数据"等触发词，或点击工具栏"清洗"按钮。
- 前端调用 `/cleaning/analyze`，把返回的 `CleaningProposal` 渲染成清洗卡片。

### 6.3 清洗执行后

- **执行清洗**：覆盖原数据集，AI 回复"清洗完成，共影响 X 行，当前数据已可用于分析"。
- **另存为新数据集**：生成新 dataset，左侧列表刷新，AI 提示"已另存为 X，新数据集已选中"。

### 6.4 数据预览联动

- `PENDING_CLEAN` 状态下 `DataPreview` 显示原始数据，并高亮问题单元格。
- 清洗完成后 `DataPreview` 自动刷新为清洗后数据。

## 7. 安全与错误处理

### 7.1 SQL 安全

- `executeCleaningSql` 白名单：只允许 `CREATE TABLE AS SELECT`、`INSERT SELECT`、`UPDATE`、`DELETE`、`ALTER`、`DROP TABLE IF EXISTS`。
- 表名校验：只能操作当前用户的临时表/正式表，禁止跨用户访问。
- 用户自定义 SQL 在执行前先做语法解析和表名校验。

### 7.2 事务与回滚

- 执行清洗前自动创建备份表 `_backup_xxx`。
- 清洗失败时回滚到备份状态。
- 清洗成功后清理备份表。

### 7.3 错误处理

- 检测失败：AI 提示"无法自动检测，请手动描述数据问题"。
- 执行失败：回滚到备份，AI 解释失败原因并让用户调整 SQL。
- LLM 生成 SQL 不符合规范：直接拒绝执行，不修改任何数据。

## 8. 测试策略

| 类型 | 测试内容 |
|------|----------|
| 单元测试 | 各类脏数据（CSV/Excel/JSON）能否正确生成清洗建议 |
| 集成测试 | 上传 -> 检测 -> 执行 -> 分析完整流程 |
| 安全测试 | 跨用户表访问、危险 SQL 被拦截 |
| 回滚测试 | 清洗失败时数据是否能恢复 |

## 9. 风险与后续扩展

- **风险**：LLM 生成的 SQL 可能不符合预期，必须依赖白名单和备份机制兜底。
- **后续扩展**：支持更复杂的清洗模板、接入 Python 脚本清洗、清洗过程可视化等。
