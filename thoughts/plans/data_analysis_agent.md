# Spring AI + DuckDB 数据分析 AI Agent 实现计划

## Overview

构建一个生产级数据分析 AI Agent，用户上传表格数据（CSV/Excel/JSON）后，通过自然语言对话完成数据分析。后端使用 Spring AI 调用国产大模型（DeepSeek/MiniMax/Kimi）实现 Text-to-SQL，DuckDB 作为数据分析引擎，支持多轮对话上下文记忆。前端提供完整的文件上传、对话分析、数据预览和图表可视化界面。

## Current State Analysis

当前项目为空白目录（greenfield），需要从零搭建。

### Key Discoveries:
- **Spring AI** 通过 `@Tool` 注解支持函数调用，`ChatClient` 作为 LLM 交互入口，`MessageChatMemoryAdvisor` + `MessageWindowChatMemory` 管理多轮对话
- **DuckDB** 使用标准 JDBC 接口，支持 `jdbc:duckdb:`（内存模式）和 `jdbc:duckdb:/path`（持久化模式），原生支持 `read_csv()` / `read_json_auto()` 直接查询文件
- **国产大模型**（DeepSeek/MiniMax/Kimi）均提供 OpenAI 兼容 API，可通过 Spring AI 的 `spring.ai.openai.base-url` + `spring.ai.openai.api-key` 配置接入
- **两步法 Text-to-SQL**: LLM 生成 SQL → DuckDB 执行 → 结果再次送入 LLM 生成自然语言回答

## Desired End State

一个完整部署的生产级应用，用户通过 Web 界面注册登录、上传数据文件、以对话方式分析数据、查看可视化图表。系统支持多用户数据隔离、多轮对话记忆、多模型切换。

### 验证标准:
- 用户可注册/登录，获取 JWT Token
- 用户可上传 CSV/Excel/JSON 文件，系统自动解析并加载到 DuckDB
- 用户可用自然语言提问，系统返回结构化分析结果和可视化图表
- 支持多轮对话，AI 理解上下文引用
- 多用户数据完全隔离
- 支持切换不同 LLM 提供商

## What We're NOT Doing

- 不实现复杂的权限管理系统（RBAC），仅做用户级别的数据隔离
- 不实现分布式 DuckDB 集群（单机嵌入式足够）
- 不实现实时数据流分析（仅支持文件上传后的静态分析）
- 不实现复杂的数据清洗/ETL 管道（DuckDB SQL 足够应对）
- 不实现移动端原生应用
- 不实现多语言国际化（仅中文界面）
- 不实现复杂的 SQL 注入防护（DuckDB 只读模式 + 沙箱足够）

## Implementation Approach

**核心策略**:
1. 后端先行：先完成核心 API（认证、上传、分析对话），再构建前端
2. 模块化设计：每个服务独立（认证、文件、DuckDB、AI分析、对话记忆）
3. 渐进式增强：先实现基础 CSV 分析，再扩展 Excel/JSON、图表、多模型切换
4. 安全第一：JWT 认证 + 用户数据隔离 + SQL 执行沙箱

**技术栈**:
- **后端**: Spring Boot 3.3+, Spring AI 1.0.x, Spring Security + JWT, DuckDB JDBC, Apache POI (Excel), Maven
- **对话记忆**: Spring AI `MessageWindowChatMemory` + `JdbcChatMemoryRepository`（数据库持久化）
- **通信**: WebSocket (Streaming) + REST API
- **前端**: React 18 + TypeScript + Vite + Ant Design + ECharts
- **数据库**: DuckDB（分析引擎）+ SQLite/H2（用户数据 + 对话记忆存储）

---

## Phase 1: 项目基础架构搭建

### Overview
搭建 Spring Boot 后端项目骨架，配置依赖、数据库、JWT 认证基础。

### Changes Required:

#### 1.1 Maven 项目配置
**File**: `pom.xml`
**Changes**: 配置 Spring Boot 父项目、核心依赖

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
</parent>

<properties>
    <java.version>17</java.version>
    <spring-ai.version>1.0.2</spring-ai.version>
    <duckdb.version>1.1.3</duckdb.version>
</properties>

<dependencies>
    <!-- Spring Boot Core -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Spring AI - OpenAI Compatible -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- DuckDB -->
    <dependency>
        <groupId>org.duckdb</groupId>
        <artifactId>duckdb_jdbc</artifactId>
        <version>${duckdb.version}</version>
    </dependency>
    
    <!-- Apache POI for Excel -->
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.3.0</version>
    </dependency>
    
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.6</version>
    </dependency>
    
    <!-- H2 for user data & chat memory -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

#### 1.2 应用配置
**File**: `src/main/resources/application.yml`
**Changes**: 配置服务器、DuckDB、Spring AI、JWT

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/agentdb;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  ai:
    openai:
      base-url: ${AI_BASE_URL:https://api.deepseek.com}
      api-key: ${AI_API_KEY:your-api-key}
      chat:
        options:
          model: ${AI_MODEL:deepseek-chat}
          temperature: 0.3

app:
  jwt:
    secret: ${JWT_SECRET:change_this_in_production_with_at_least_256_bits_key}
    expiration: 86400000  # 24 hours
  duckdb:
    storage-path: ./data/duckdb
  upload:
    max-file-size: 50MB
    storage-path: ./data/uploads
```

#### 1.3 项目包结构
**Files**:
```
src/main/java/com/example/agent/
├── AgentApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── DuckDbConfig.java
│   ├── WebSocketConfig.java
│   └── AsyncConfig.java
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── CustomUserDetailsService.java
├── controller/
│   ├── AuthController.java
│   ├── DatasetController.java
│   └── AnalysisController.java
├── service/
│   ├── AuthService.java
│   ├── DatasetService.java
│   ├── AnalysisService.java
│   ├── DuckDbService.java
│   └── ChatMemoryService.java
├── model/
│   ├── entity/
│   │   ├── User.java
│   │   └── Dataset.java
│   ├── dto/
│   │   ├── AuthRequest.java
│   │   ├── AuthResponse.java
│   │   ├── DatasetResponse.java
│   │   └── AnalysisRequest.java
│   └── enums/
│       └── FileType.java
├── repository/
│   ├── UserRepository.java
│   └── DatasetRepository.java
└── exception/
    ├── GlobalExceptionHandler.java
    └── BusinessException.java
```

#### 1.4 安全配置
**File**: `src/main/java/com/example/agent/config/SecurityConfig.java`
**Changes**: 配置 Spring Security，放行登录注册接口，保护其他端点

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

#### 1.5 JWT 工具类
**File**: `src/main/java/com/example/agent/security/JwtTokenProvider.java`
**Changes**: 实现 JWT Token 生成与验证

- 使用 `io.jsonwebtoken` 库
- `generateToken(UserDetails)` → 返回 JWT 字符串
- `validateToken(String)` → 返回 boolean
- `getUserIdFromToken(String)` → 返回 Long

#### 1.6 用户实体与 Repository
**File**: `src/main/java/com/example/agent/model/entity/User.java`
**Changes**: 用户实体，包含 id, username, password, createdAt

**File**: `src/main/java/com/example/agent/model/entity/Dataset.java`
**Changes**: 数据集实体，包含 id, userId, fileName, fileType, tableName, columnInfo (JSON), rowCount, createdAt

### Success Criteria:

#### Automated Verification:
- [ ] `mvn compile` 编译通过
- [ ] `mvn spring-boot:run` 应用启动成功
- [ ] H2 控制台可访问 `http://localhost:8080/h2-console`
- [ ] `curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"test","password":"123456"}'` 返回 JWT token
- [ ] `curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"test","password":"123456"}'` 返回 JWT token
- [ ] 无 Token 访问受保护接口返回 401

#### Manual Verification:
- [ ] 注册后可用登录接口获取 Token
- [ ] 数据库中用户密码已加密存储

---

## Phase 2: DuckDB 数据引擎服务

### Overview
实现 DuckDB 连接管理、数据加载、Schema 发现、SQL 执行等核心能力。

### Changes Required:

#### 2.1 DuckDB 配置
**File**: `src/main/java/com/example/agent/config/DuckDbConfig.java`
**Changes**: 配置 DuckDB 连接管理

```java
@Configuration
public class DuckDbConfig {
    
    @Value("${app.duckdb.storage-path}")
    private String storagePath;
    
    @Bean
    public DuckDbConnectionPool duckDbConnectionPool() {
        return new DuckDbConnectionPool(storagePath);
    }
}
```

#### 2.2 DuckDB 连接池
**File**: `src/main/java/com/example/agent/service/DuckDbConnectionPool.java`
**Changes**: 管理每个用户的 DuckDB 连接

核心设计：
- 每个用户对应一个 DuckDB 数据库文件: `{storagePath}/user_{userId}.db`
- 使用 `ConcurrentHashMap<Long, Connection>` 缓存连接
- 提供 `getConnection(Long userId)` 和 `closeConnection(Long userId)` 方法
- 内存模式用于临时查询：`jdbc:duckdb:`

```java
public class DuckDbConnectionPool {
    private final ConcurrentHashMap<Long, Connection> connections = new ConcurrentHashMap<>();
    private final String basePath;
    
    public Connection getConnection(Long userId) throws SQLException {
        return connections.computeIfAbsent(userId, id -> {
            try {
                String url = "jdbc:duckdb:" + basePath + "/user_" + id + ".db";
                return DriverManager.getConnection(url);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create DuckDB connection for user: " + id, e);
            }
        });
    }
    
    public void closeConnection(Long userId) { ... }
}
```

#### 2.3 DuckDB 核心服务
**File**: `src/main/java/com/example/agent/service/DuckDbService.java`
**Changes**: 实现数据加载、Schema 发现、SQL 执行

```java
@Service
public class DuckDbService {
    
    private final DuckDbConnectionPool connectionPool;
    
    // 加载 CSV 文件到 DuckDB
    public DatasetMeta loadCsv(Long userId, String filePath, String tableName) {
        // CREATE TABLE {tableName} AS FROM read_csv('{filePath}', auto_detect=true)
        // 返回 column info, row count
    }
    
    // 加载 Excel 文件
    public DatasetMeta loadExcel(Long userId, MultipartFile file, String tableName) {
        // 1. 用 Apache POI 读取 Excel
        // 2. 转为 CSV 临时文件
        // 3. 调用 loadCsv
    }
    
    // 加载 JSON 文件
    public DatasetMeta loadJson(Long userId, String filePath, String tableName) {
        // CREATE TABLE {tableName} AS FROM read_json_auto('{filePath}')
    }
    
    // 获取数据集 Schema
    public List<ColumnInfo> getSchema(Long userId, String tableName) {
        // DESCRIBE {tableName} 或 PRAGMA table_info('{tableName}')
    }
    
    // 执行只读 SQL 查询
    public QueryResult executeQuery(Long userId, String sql) {
        // 安全检查: 只允许 SELECT 语句
        // executeQuery(sql) → 返回 columns + rows
    }
    
    // 获取数据预览 (前100行)
    public QueryResult previewData(Long userId, String tableName) {
        return executeQuery(userId, "SELECT * FROM " + tableName + " LIMIT 100");
    }
}
```

#### 2.4 数据结构 DTO
**File**: `src/main/java/com/example/agent/model/dto/DatasetMeta.java`
**Changes**: 数据集元信息

```java
public record DatasetMeta(
    String tableName,
    String fileType,
    long rowCount,
    List<ColumnInfo> columns
) {}

public record ColumnInfo(
    String name,
    String type,
    boolean nullable
) {}

public record QueryResult(
    List<String> columns,
    List<List<Object>> rows,
    int totalRows,
    long executionTimeMs
) {}
```

### Success Criteria:

#### Automated Verification:
- [ ] `mvn test` 所有测试通过
- [ ] 单元测试: `DuckDbServiceTest` 覆盖 loadCsv, loadJson, executeQuery, getSchema
- [ ] 集成测试: CSV 文件加载后能正确查询数据

#### Manual Verification:
- [ ] 加载测试 CSV 文件，验证 Schema 自动推断正确
- [ ] 执行 `SELECT COUNT(*)` 返回正确行数
- [ ] 多用户数据隔离：用户 A 的表用户 B 看不到

---

## Phase 3: 文件上传与数据集管理

### Overview
实现文件上传 API，支持 CSV/Excel/JSON，解析后加载到 DuckDB，提供数据集列表和管理功能。

### Changes Required:

#### 3.1 文件上传控制器
**File**: `src/main/java/com/example/agent/controller/DatasetController.java`
**Changes**: REST API 实现

```java
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
    
    @PostMapping("/upload")
    public ResponseEntity<DatasetResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Long userId) {
        // 1. 验证文件格式和大小
        // 2. 保存文件到 {uploadPath}/user_{userId}/
        // 3. 调用 DatasetService 加载到 DuckDB
        // 4. 返回数据集元信息
    }
    
    @GetMapping
    public ResponseEntity<List<DatasetResponse>> list(@AuthenticationPrincipal Long userId) {
        // 返回用户所有数据集列表
    }
    
    @GetMapping("/{datasetId}/schema")
    public ResponseEntity<List<ColumnInfo>> schema(
            @PathVariable Long datasetId,
            @AuthenticationPrincipal Long userId) {
        // 返回数据集 Schema
    }
    
    @GetMapping("/{datasetId}/preview")
    public ResponseEntity<QueryResult> preview(
            @PathVariable Long datasetId,
            @AuthenticationPrincipal Long userId) {
        // 返回前100行数据预览
    }
    
    @DeleteMapping("/{datasetId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long datasetId,
            @AuthenticationPrincipal Long userId) {
        // 删除数据集（删除 DuckDB 表 + 文件）
    }
}
```

#### 3.2 数据集服务
**File**: `src/main/java/com/example/agent/service/DatasetService.java`
**Changes**: 文件处理 + DuckDB 加载的业务编排

```java
@Service
public class DatasetService {
    
    private final DuckDbService duckDbService;
    private final DatasetRepository datasetRepository;
    
    @Transactional
    public DatasetResponse uploadDataset(Long userId, MultipartFile file) {
        // 1. 验证: 文件大小、格式扩展名
        // 2. 生成唯一 tableName: dataset_{userId}_{timestamp}
        // 3. 保存原始文件到磁盘
        // 4. 根据文件类型调用对应的 DuckDbService.load* 方法
        // 5. 保存 Dataset 记录到 H2（元信息 + column info JSON）
        // 6. 返回 DatasetResponse
    }
    
    public List<DatasetResponse> listDatasets(Long userId) {
        // 查询用户的所有数据集
    }
    
    public void deleteDataset(Long userId, Long datasetId) {
        // 验证所有权，删除 DuckDB 表 + 文件 + 数据库记录
    }
    
    private String generateTableName(Long userId) {
        return "dataset_" + userId + "_" + System.currentTimeMillis();
    }
}
```

#### 3.3 文件类型检测
**File**: `src/main/java/com/example/agent/model/enums/FileType.java`
**Changes**: 文件类型枚举

```java
public enum FileType {
    CSV("csv"), EXCEL("xlsx"), JSON("json");
    
    public static FileType fromFilename(String filename) {
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (ext) {
            case "csv" -> CSV;
            case "xlsx", "xls" -> EXCEL;
            case "json" -> JSON;
            default -> throw new BusinessException("不支持的文件格式: " + ext);
        };
    }
}
```

### Success Criteria:

#### Automated Verification:
- [ ] `mvn test` 测试通过
- [ ] 单元测试: `DatasetServiceTest` 覆盖文件上传、格式验证、异常处理
- [ ] 集成测试: 上传 CSV 后能查询到正确的数据预览

#### Manual Verification:
- [ ] 通过 curl 上传 CSV 文件，返回数据集 ID 和 Schema
- [ ] 上传 Excel 文件，自动解析工作表和列
- [ ] 上传 JSON 文件（数组格式），正确推断嵌套结构
- [ ] 数据集列表只显示当前用户的数据
- [ ] 数据预览返回前 100 行

---

## Phase 4: Text-to-SQL 核心分析引擎

### Overview
实现基于 Spring AI 的两步法 Text-to-SQL：1) LLM 根据用户问题和表结构生成 SQL → 2) DuckDB 执行 SQL → 3) 结果送入 LLM 生成自然语言回答。

### Changes Required:

#### 4.1 Text-to-SQL Prompt 模板
**File**: `src/main/resources/prompt/text-to-sql.st`
**Changes**: SQL 生成提示词模板

```
你是一个专业的数据分析师和 SQL 专家。用户上传了以下数据表：

表名: {tableName}
表结构:
{schema}

用户问题: {question}

请根据用户问题生成一条 DuckDB 兼容的 SQL 查询语句。
要求:
1. 只生成 SELECT 查询，不要生成任何修改数据的语句
2. SQL 必须兼容 DuckDB 语法
3. 使用给定的表名和列名，不要猜测不存在的列
4. 对于聚合查询，使用有意义的别名
5. 只输出 SQL 语句，不要任何解释或 markdown 格式

SQL:
```

#### 4.2 结果分析 Prompt 模板
**File**: `src/main/resources/prompt/result-analysis.st`
**Changes**: 结果分析提示词模板

```
你是一个专业的数据分析师。用户提出了以下问题:

{question}

以下是 SQL 查询结果:
列名: {columns}
数据 (最多前 {rowCount} 行):
{data}

请用中文给出简洁明了的数据分析回答，包括:
1. 直接回答用户的问题
2. 关键数据指标和发现
3. 如果数据有异常或值得注意的地方，请指出

回答:
```

#### 4.3 Spring AI ChatClient 配置
**File**: `src/main/java/com/example/agent/config/AiConfig.java`
**Changes**: 配置 ChatClient 和 ChatMemory

```java
@Configuration
public class AiConfig {
    
    @Bean
    public ChatClient chatClient(ChatModel chatModel, 
                                  MessageChatMemoryAdvisor memoryAdvisor) {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(memoryAdvisor)
            .build();
    }
    
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(
            JdbcChatMemoryRepository chatMemoryRepository) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRepository)
            .maxMessages(20)
            .build();
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
```

#### 4.4 分析服务 - 两步法实现
**File**: `src/main/java/com/example/agent/service/AnalysisService.java`
**Changes**: 核心分析服务

```java
@Service
public class AnalysisService {
    
    private final ChatClient chatClient;
    private final DuckDbService duckDbService;
    private final DatasetRepository datasetRepository;
    
    // 第一步: 生成 SQL
    public String generateSql(Long userId, Long datasetId, String question) {
        Dataset dataset = datasetRepository.findByIdAndUserId(datasetId, userId)
            .orElseThrow(() -> new BusinessException("数据集不存在"));
        
        String schema = formatSchema(duckDbService.getSchema(userId, dataset.getTableName()));
        
        return chatClient.prompt()
            .system(getTextToSqlPrompt(dataset.getTableName(), schema))
            .user(question)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, buildConversationId(userId, datasetId)))
            .call()
            .content();
    }
    
    // 第二步: 用结果生成自然语言回答
    public String analyzeResults(String question, QueryResult queryResult) {
        return chatClient.prompt()
            .system(getResultAnalysisPrompt())
            .user(formatAnalysisPrompt(question, queryResult))
            .call()
            .content();
    }
    
    // 完整分析流程（非流式）
    public AnalysisResponse analyze(Long userId, Long datasetId, String question) {
        // 1. 生成 SQL
        String sql = generateSql(userId, datasetId, question);
        sql = extractSql(sql); // 清理 markdown 标记等
        
        // 2. 执行 SQL
        QueryResult result = duckDbService.executeReadOnlyQuery(userId, sql);
        
        // 3. 生成自然语言回答
        String answer = analyzeResults(question, result);
        
        return new AnalysisResponse(sql, result, answer);
    }
    
    // 流式分析（用于 WebSocket）
    public Flux<String> analyzeStream(Long userId, Long datasetId, String question) {
        // 1. 生成 SQL → 发射 "sql" 事件
        // 2. 执行 SQL → 发射 "data" 事件  
        // 3. 流式生成回答 → 发射 "token" 事件
        return Flux.create(sink -> {
            try {
                String sql = generateSql(userId, datasetId, question);
                sink.next(jsonEvent("sql", sql));
                
                QueryResult result = duckDbService.executeReadOnlyQuery(userId, sql);
                sink.next(jsonEvent("data", result));
                
                // 流式生成回答
                chatClient.prompt()
                    .system(getResultAnalysisPrompt())
                    .user(formatAnalysisPrompt(question, result))
                    .stream()
                    .content()
                    .doOnNext(token -> sink.next(jsonEvent("token", token)))
                    .doOnComplete(sink::complete)
                    .subscribe();
            } catch (Exception e) {
                sink.next(jsonEvent("error", e.getMessage()));
                sink.complete();
            }
        });
    }
}
```

#### 4.5 SQL 安全执行
**File**: `src/main/java/com/example/agent/service/DuckDbService.java` (修改)
**Changes**: 增加只读 SQL 执行方法

```java
public QueryResult executeReadOnlyQuery(Long userId, String sql) {
    // 安全检查: 只允许 SELECT/WITH/EXPLAIN 语句
    String trimmed = sql.trim().toUpperCase();
    if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("WITH") && !trimmed.startsWith("EXPLAIN")
        && !trimmed.startsWith("DESCRIBE") && !trimmed.startsWith("SHOW")) {
        throw new BusinessException("只允许执行查询语句");
    }
    
    // 禁止多语句
    if (sql.contains(";") && sql.indexOf(';') != sql.length() - 1) {
        throw new BusinessException("不允许执行多条语句");
    }
    
    try (Statement stmt = connection.createStatement()) {
        stmt.setMaxRows(10000); // 限制最大返回行数
        long start = System.currentTimeMillis();
        ResultSet rs = stmt.executeQuery(sql);
        // 转换为 QueryResult...
    }
}
```

#### 4.6 分析控制器
**File**: `src/main/java/com/example/agent/controller/AnalysisController.java`
**Changes**: 分析 REST API + WebSocket 端点

```java
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    
    // 同步分析
    @PostMapping("/{datasetId}")
    public ResponseEntity<AnalysisResponse> analyze(
            @PathVariable Long datasetId,
            @RequestBody AnalysisRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(analysisService.analyze(userId, datasetId, request.question()));
    }
    
    // 流式分析 - SSE
    @PostMapping(value = "/{datasetId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> analyzeStream(
            @PathVariable Long datasetId,
            @RequestBody AnalysisRequest request,
            @AuthenticationPrincipal Long userId) {
        return analysisService.analyzeStream(userId, datasetId, request.question())
            .map(data -> ServerSentEvent.builder(data).build());
    }
}
```

#### 4.7 WebSocket 配置（可选，后续可换 SSE）
**File**: `src/main/java/com/example/agent/config/WebSocketConfig.java`
**Changes**: 配置 WebSocket 端点用于流式分析

### Success Criteria:

#### Automated Verification:
- [ ] `mvn test` 测试通过
- [ ] 单元测试: `AnalysisServiceTest` 覆盖 SQL 生成、结果解析、安全校验
- [ ] Mock LLM 测试: 验证 prompt 构造正确

#### Manual Verification:
- [ ] 上传销售数据 CSV，问"总销售额是多少"，返回正确结果
- [ ] 问"每个月的销售趋势"，生成 GROUP BY 查询并返回趋势分析
- [ ] SQL 注入尝试被安全层拦截
- [ ] 多轮对话: 先问"有哪些产品"，再问"哪个卖得好"，AI 理解上下文

---

## Phase 5: 多轮对话与上下文管理

### Overview
实现基于 Spring AI ChatMemory 的多轮对话，支持上下文感知的连续数据分析。

### Changes Required:

#### 5.1 对话记忆数据库初始化
**File**: `src/main/resources/schema.sql`
**Changes**: 创建 ChatMemory 表和会话管理表

```sql
-- Spring AI JDBC ChatMemory 需要的表
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chat_memory_conversation 
    ON SPRING_AI_CHAT_MEMORY(conversation_id);

-- 会话管理: 每个数据集对应对话线程
CREATE TABLE IF NOT EXISTS analysis_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 5.2 ChatMemory 服务
**File**: `src/main/java/com/example/agent/service/ChatMemoryService.java`
**Changes**: 管理对话线程和上下文

```java
@Service
public class ChatMemoryService {
    
    private final ChatMemory chatMemory;
    private final JdbcChatMemoryRepository repository;
    
    // 创建或获取会话
    public String getOrCreateConversation(Long userId, Long datasetId) {
        // 查找现有会话，不存在则创建新会话
        // conversationId格式: analysis_{userId}_{datasetId}_{timestamp}
    }
    
    // 获取对话历史
    public List<Message> getHistory(String conversationId) {
        return chatMemory.get(conversationId);
    }
    
    // 清除对话
    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
    }
    
    // 获取用户的对话列表
    public List<ConversationSummary> listConversations(Long userId) {
        // 返回用户的所有分析会话摘要
    }
}
```

#### 5.3 上下文增强的 SQL 生成
**File**: `src/main/java/com/example/agent/service/AnalysisService.java` (修改)
**Changes**: 在 SQL 生成时注入表结构上下文

```java
// 增强: 每次 SQL 生成时，在系统 prompt 中包含完整表结构
// 对话历史通过 MessageChatMemoryAdvisor 自动注入
// 这样即使用户说"再按产品类别分组"，AI 也能理解"产品类别"指哪个列
```

### Success Criteria:

#### Automated Verification:
- [ ] `mvn test` 测试通过
- [ ] 验证对话历史能跨请求保持

#### Manual Verification:
- [ ] 连续多次提问，AI 能引用之前的问题和结果
- [ ] 切换数据集后，对话上下文正确切换
- [ ] 清除对话后，重新开始上下文

---

## Phase 6: 前端完整实现

### Overview
构建 React 前端，包含用户认证、文件上传、对话分析界面、数据预览和图表可视化。

### Changes Required:

#### 6.1 前端项目结构
**Files**: `frontend/` 目录
```
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── api/
│   │   ├── client.ts          # Axios 实例 + JWT 拦截器
│   │   ├── auth.ts
│   │   ├── dataset.ts
│   │   └── analysis.ts
│   ├── components/
│   │   ├── Layout.tsx
│   │   ├── FileUpload.tsx
│   │   ├── ChatMessage.tsx
│   │   ├── DataTable.tsx
│   │   └── ChartView.tsx
│   ├── pages/
│   │   ├── Login.tsx
│   │   ├── Register.tsx
│   │   ├── DatasetList.tsx
│   │   ├── AnalysisWorkspace.tsx
│   │   └── ConversationHistory.tsx
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useAnalysis.ts
│   │   └── useSSE.ts
│   ├── store/
│   │   └── authStore.ts
│   └── types/
│       └── index.ts
```

#### 6.2 核心组件
**File**: `src/pages/AnalysisWorkspace.tsx`
**Changes**: 分析工作区 - 核心界面

布局结构：
- 左侧面板：数据集列表 + 数据预览
- 中间主区域：对话聊天窗口
- 右侧/底部：图表可视化区

#### 6.3 SSE Hook（流式分析）
**File**: `src/hooks/useAnalysis.ts`
**Changes**: 使用 EventSource 或 fetch 流处理 SSE

```typescript
export function useAnalysis(datasetId: number) {
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    
    const sendMessage = async (question: string) => {
        setIsLoading(true);
        // 添加到消息列表
        setMessages(prev => [...prev, { role: 'user', content: question }]);
        
        // SSE 流式接收
        const response = await fetch(`/api/analysis/${datasetId}/stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ question })
        });
        
        // 处理流: sql事件 → data事件 → token事件
        const reader = response.body?.getReader();
        // ...
    };
    
    return { messages, sendMessage, isLoading };
}
```

#### 6.4 图表可视化
**File**: `src/components/ChartView.tsx`
**Changes**: 基于 ECharts 的数据可视化

```typescript
// 根据查询结果自动推荐图表类型
// 数值列 → 柱状图/折线图
// 分类 + 数值 → 饼图
// 多数值 → 散点图
```

### Success Criteria:

#### Automated Verification:
- [ ] `npm run build` 构建成功
- [ ] `npm run lint` 无错误

#### Manual Verification:
- [ ] 完整的注册/登录流程
- [ ] 文件上传进度显示
- 对话式分析:
  - [ ] 用户提问后显示生成的 SQL
  - [ ] 流式显示 AI 分析回答
  - [ ] 数据表格正确渲染
  - [ ] 图表根据数据类型自动推荐
- 多轮对话:
  - [ ] 连续提问保持上下文
  - [ ] 对话历史可查看
- 响应式布局:
  - [ ] 桌面端三栏布局正常
  - [ ] 平板/手机端可正常使用

---

## Phase 7: 多模型支持与生产优化

### Overview
配置多模型切换能力，优化生产部署配置。

### Changes Required:

#### 7.1 多模型配置
**File**: `src/main/resources/application.yml`
**Changes**: 支持模型配置

```yaml
spring:
  ai:
    openai:
      base-url: ${AI_BASE_URL:https://api.deepseek.com}
      api-key: ${AI_API_KEY:}
      chat:
        options:
          model: ${AI_MODEL:deepseek-chat}
          temperature: 0.3

# 预配置的模型提供商
app:
  ai:
    providers:
      deepseek:
        base-url: https://api.deepseek.com
        models: [deepseek-chat, deepseek-reasoner]
      kimi:
        base-url: https://api.moonshot.cn/v1
        models: [moonshot-v1-8k, moonshot-v1-32k]
      minimax:
        base-url: https://api.minimax.chat/v1
        models: [abab6.5s-chat, abab6.5t-chat]
```

#### 7.2 动态模型选择
**File**: `src/main/java/com/example/agent/service/ModelSwitchService.java`
**Changes**: 允许用户在分析时选择模型

### Success Criteria:

#### Automated Verification:
- [ ] `mvn test` 测试通过

#### Manual Verification:
- [ ] 可在 DeepSeek/Kimi/MiniMax 之间切换
- [ ] 不同模型的分析质量符合预期

---

## Testing Strategy

### Unit Tests:
- `UserServiceTest` - 注册、登录、密码加密
- `DuckDbServiceTest` - CSV/JSON 加载、SQL 执行、Schema 发现
- `DatasetServiceTest` - 文件验证、上传流程
- `AnalysisServiceTest` - Prompt 构造、SQL 解析、结果格式化
- `ChatMemoryServiceTest` - 对话上下文管理

### Integration Tests:
- 端到端: 注册 → 登录 → 上传 → 分析 → 查看结果
- 多用户隔离测试
- SSE 流式响应测试

### Manual Testing Steps:
1. 注册新用户，验证 JWT 返回
2. 上传示例 CSV（销售数据），确认 Schema 识别
3. 提问"总销售额是多少"，验证 SQL 生成和结果
4. 提问"按月份分组的销售趋势"，验证聚合查询
5. 提问"哪个产品卖得最好"，验证排序和 LIMIT
6. 连续提问"那上个月呢"，验证上下文理解
7. 上传 Excel 和 JSON 文件，验证格式兼容
8. 切换 LLM 提供商，验证模型切换
9. 打开两个浏览器，验证用户数据隔离

## Performance Considerations

- **DuckDB 连接复用**: 使用连接池避免频繁创建连接
- **大文件处理**: 超过 10MB 的文件使用流式解析
- **SQL 超时**: 单次查询限制 30 秒超时
- **结果集限制**: 单次查询最多返回 10,000 行
- **聊天记忆窗口**: 保留最近 20 条消息，避免 token 溢出
- **前端虚拟滚动**: 大数据表格使用虚拟滚动渲染

## Migration Notes

- 初期使用 H2 存储用户和元数据，后续可迁移到 PostgreSQL
- DuckDB 文件格式升级时需注意向后兼容
- 对话记忆表结构变更时需清理旧数据

## References

- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/
- DuckDB Java API: https://duckdb.org/docs/api/java
- DeepSeek API: https://platform.deepseek.com/api-docs/
- Kimi API: https://platform.moonshot.cn/docs/
