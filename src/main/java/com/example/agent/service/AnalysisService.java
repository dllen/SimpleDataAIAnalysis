package com.example.agent.service;

import com.example.agent.model.dto.ColumnInfo;
import com.example.agent.model.dto.QueryResult;
import com.example.agent.model.dto.AnalysisResponse;
import com.example.agent.model.entity.Dataset;
import com.example.agent.exception.BusinessException;
import com.example.agent.repository.DatasetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final ChatClient chatClient;
    private final DuckDbService duckDbService;
    private final DatasetRepository datasetRepository;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final com.example.agent.repository.UserRepository userRepository;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String currentModel;

    public AnalysisService(ChatClient chatClient, DuckDbService duckDbService,
                          DatasetRepository datasetRepository, MessageChatMemoryAdvisor memoryAdvisor,
                          com.example.agent.repository.UserRepository userRepository) {
        this.chatClient = chatClient;
        this.duckDbService = duckDbService;
        this.datasetRepository = datasetRepository;
        this.memoryAdvisor = memoryAdvisor;
        this.userRepository = userRepository;
    }

    public AnalysisResponse analyze(Long userId, Long datasetId, String question) throws SQLException {
        Dataset dataset = datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException("数据集不存在或无权限"));

        List<ColumnInfo> columns = duckDbService.getSchema(userId, dataset.getTableName());
        String schemaStr = formatSchema(columns);

        String conversationId = buildConversationId(userId, datasetId);

        String sql = generateSql(dataset.getTableName(), schemaStr, question, conversationId);
        sql = extractSql(sql);
        log.debug("Generated SQL for user {}: {}", userId, sql);

        QueryResult result = duckDbService.executeReadOnlyQuery(userId, sql);

        String answer = analyzeResults(question, result, conversationId);

        return new AnalysisResponse(sql, result, answer);
    }

    public Flux<String> analyzeStream(Long userId, Long datasetId, String question) throws SQLException {
        Dataset dataset = datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException("数据集不存在或无权限"));

        List<ColumnInfo> columns = duckDbService.getSchema(userId, dataset.getTableName());
        String schemaStr = formatSchema(columns);
        String conversationId = buildConversationId(userId, datasetId);

        String finalSql = extractSql(generateSql(dataset.getTableName(), schemaStr, question, conversationId));
        log.debug("Generated SQL for user {} (stream): {}", userId, finalSql);

        QueryResult result = duckDbService.executeReadOnlyQuery(userId, finalSql);

        String resultJson = formatQueryResultAsJson(result);

        return Flux.just(
            jsonEvent("sql", finalSql),
            jsonEvent("columns", formatColumnsJson(result.columns())),
            jsonEvent("rowCount", String.valueOf(result.rows().size()))
        ).concatWith(
            chatClient.prompt()
                .system(getResultAnalysisSystemPrompt())
                .user(formatAnalysisUserPrompt(question, resultJson))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .map(token -> jsonEvent("token", escapeJson(token)))
        ).concatWith(
            Flux.just(jsonEvent("done", ""))
        );
    }

    private String generateSql(String tableName, String schema, String question, String conversationId) {
        return chatClient.prompt()
            .system(getTextToSqlSystemPrompt(tableName, schema))
            .user(question)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .content();
    }

    private String analyzeResults(String question, QueryResult result, String conversationId) {
        String resultJson = formatQueryResultAsJson(result);
        return chatClient.prompt()
            .system(getResultAnalysisSystemPrompt())
            .user(formatAnalysisUserPrompt(question, resultJson))
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .content();
    }

    private String getTextToSqlSystemPrompt(String tableName, String schema) {
        return """
            你是一个专业的 DuckDB SQL 专家。用户上传了以下数据表：

            表名: %s

            表结构 (列名 - 类型):
            %s

            你的任务是根据用户的自然语言问题，生成一条 DuckDB 兼容的 SQL 查询语句。

            严格要求:
            1. 只生成 SELECT 查询，不要生成任何修改数据的语句 (INSERT/UPDATE/DELETE/DROP 等)
            2. SQL 必须兼容 DuckDB 语法
            3. 使用给定的表名和列名，不要猜测不存在的列
            4. 对于聚合查询，使用有意义的类别名 (AS)
            5. 只输出纯 SQL 语句本身，不要任何解释、注释或 markdown 格式 (不要 ```sql 标记)
            6. 如果问题需要多表关联但只有一张表，在当前表范围内给出最佳答案
            """.formatted(tableName, schema);
    }

    private String getResultAnalysisSystemPrompt() {
        return """
            你是一个专业的数据分析师，擅长用通俗易懂的语言解释数据。

            用户提出了一个数据分析问题，你已经帮他们执行了 SQL 查询并得到了结果。
            请根据查询结果，用中文给出简洁、专业且有洞察力的分析回答。

            要求:
            1. 直接回答用户的问题
            2. 提取关键数据指标和发现
            3. 如果数据有异常或值得注意的趋势，请指出
            4. 用简洁的中文表达，避免冗长
            5. 数字保留合适精度，大数字可用万/亿单位
            """;
    }

    private String formatAnalysisUserPrompt(String question, String resultJson) {
        return "用户问题: " + question + "\n\nSQL 查询结果:\n" + resultJson;
    }

    private String formatSchema(List<ColumnInfo> columns) {
        StringBuilder sb = new StringBuilder();
        for (ColumnInfo col : columns) {
            sb.append("  - ").append(col.name()).append(": ").append(col.type()).append("\n");
        }
        return sb.toString();
    }

    private String formatQueryResultAsJson(QueryResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("列名: ").append(String.join(", ", result.columns())).append("\n");
        sb.append("数据行数: ").append(result.rows().size()).append("\n");
        sb.append("数据:\n");
        for (int i = 0; i < Math.min(result.rows().size(), 50); i++) {
            List<Object> row = result.rows().get(i);
            sb.append("  行").append(i + 1).append(": ");
            for (int j = 0; j < result.columns().size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(result.columns().get(j)).append("=").append(row.get(j));
            }
            sb.append("\n");
        }
        if (result.rows().size() > 50) {
            sb.append("  ... (共 ").append(result.rows().size()).append(" 行，仅显示前50行)\n");
        }
        return sb.toString();
    }

    private String formatColumnsJson(List<String> columns) {
        return "[" + String.join(", ", columns.stream().map(c -> "\"" + escapeJson(c) + "\"").toList()) + "]";
    }

    private String extractSql(String raw) {
        if (raw == null) return "";
        String sql = raw.trim();
        if (sql.startsWith("```sql")) {
            sql = sql.substring(6);
        } else if (sql.startsWith("```")) {
            sql = sql.substring(3);
        }
        if (sql.endsWith("```")) {
            sql = sql.substring(0, sql.length() - 3);
        }
        return sql.trim();
    }

    private String buildConversationId(Long userId, Long datasetId) {
        return "analysis_" + userId + "_" + datasetId;
    }

    private String jsonEvent(String type, String data) {
        return "{\"type\":\"" + type + "\",\"data\":\"" + escapeJson(data) + "\"}\n";
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    public Long findUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(com.example.agent.model.entity.User::getId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }
}
