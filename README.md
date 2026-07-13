# 数据分析 AI Agent

基于 Spring AI + DuckDB 的生产级数据分析 AI Agent。用户上传表格数据（CSV/Excel/JSON），通过自然语言对话完成数据分析。

## 功能特性

- **多格式数据支持**: CSV、Excel (.xlsx/.xls)、JSON
- **自然语言分析**: 用户用中文提问，AI 自动生成 SQL 并返回分析结果
- **两步法 Text-to-SQL**: LLM 生成 SQL → DuckDB 执行 → LLM 生成自然语言回答
- **多轮对话**: 支持上下文感知的连续数据分析
- **数据可视化**: 自动生成图表（柱状图、饼图等）
- **流式响应**: SSE 流式推送分析结果
- **多模型支持**: DeepSeek、Kimi(Moonshot)、MiniMax 等国产大模型
- **用户认证**: JWT 认证，多用户数据隔离
- **SQL 安全**: 只读查询沙箱，防止 SQL 注入

## 技术栈

### 后端
- Java 17 + Spring Boot 3.3
- Spring AI 1.0 (OpenAI 兼容 API)
- DuckDB 1.1 (嵌入式分析引擎)
- Spring Security + JWT
- H2 (用户数据 + 元数据存储)
- Apache POI (Excel 解析)

### 前端
- React 18 + TypeScript
- Vite 构建工具
- Ant Design 5 (UI 组件)
- ECharts (数据可视化)
- Zustand (状态管理)
- Axios (HTTP 客户端)

## 快速开始

### 前置要求
- JDK 17+
- Maven 3.8+
- Node.js 18+

### 1. 启动后端

```bash
# 配置 API Key (使用 DeepSeek 示例)
export AI_BASE_URL=https://api.deepseek.com
export AI_API_KEY=your-deepseek-api-key
export AI_MODEL=deepseek-chat

# 编译运行
mvn spring-boot:run
```

后端启动在 `http://localhost:8080`

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动在 `http://localhost:3000`

### 3. 使用

1. 访问 `http://localhost:3000`
2. 注册/登录账号
3. 上传 CSV/Excel/JSON 文件
4. 用自然语言提问，如：
   - "总共有多少条数据？"
   - "各类别数量分布如何？"
   - "数量最多的是什么？"

## 项目结构

```
├── src/main/java/com/example/agent/
│   ├── AgentApplication.java       # 应用入口
│   ├── config/                     # 配置类
│   │   ├── SecurityConfig.java     # Spring Security 配置
│   │   ├── DuckDbConfig.java       # DuckDB 配置
│   │   └── AiConfig.java           # Spring AI 配置
│   ├── security/                   # JWT 认证
│   │   ├── JwtTokenProvider.java   # JWT Token 生成/验证
│   │   ├── JwtAuthenticationFilter.java
│   │   └── CustomUserDetailsService.java
│   ├── controller/                 # REST API
│   │   ├── AuthController.java     # 认证接口
│   │   ├── DatasetController.java  # 数据集接口
│   │   ├── AnalysisController.java # 分析接口
│   │   └── ModelController.java    # 模型接口
│   ├── service/                    # 业务逻辑
│   │   ├── AuthService.java        # 认证服务
│   │   ├── DatasetService.java     # 数据集管理
│   │   ├── DuckDbService.java      # DuckDB 引擎
│   │   ├── DuckDbConnectionPool.java
│   │   └── AnalysisService.java    # 分析引擎 (核心)
│   ├── model/                      # 数据模型
│   │   ├── entity/                 # 实体
│   │   ├── dto/                    # 数据传输对象
│   │   └── enums/                  # 枚举
│   ├── repository/                 # 数据访问
│   └── exception/                  # 异常处理
├── src/main/resources/
│   ├── application.yml             # 应用配置
│   └── schema.sql                  # 数据库初始化
└── frontend/                       # React 前端
    ├── src/
    │   ├── api/                    # API 客户端
    │   ├── components/             # 组件
    │   ├── pages/                  # 页面
    │   ├── store/                  # 状态管理
    │   └── types/                  # 类型定义
    └── package.json
```

## 支持的 LLM 提供商

| 提供商 | Base URL | 模型 |
|--------|----------|------|
| DeepSeek | https://api.deepseek.com | deepseek-chat, deepseek-reasoner |
| Kimi | https://api.moonshot.cn/v1 | moonshot-v1-8k, moonshot-v1-32k |
| MiniMax | https://api.minimax.chat/v1 | abab6.5s-chat, abab6.5t-chat |

通过环境变量切换：
```bash
export AI_BASE_URL=https://api.moonshot.cn/v1
export AI_API_KEY=your-kimi-api-key
export AI_MODEL=moonshot-v1-8k
```

## API 文档

### 认证
- `POST /api/auth/register` - 注册
- `POST /api/auth/login` - 登录

### 数据集
- `POST /api/datasets/upload` - 上传文件
- `GET /api/datasets` - 数据集列表
- `GET /api/datasets/{id}` - 数据集详情
- `GET /api/datasets/{id}/schema` - 表结构
- `GET /api/datasets/{id}/preview` - 数据预览
- `DELETE /api/datasets/{id}` - 删除数据集

### 分析
- `POST /api/analysis/{datasetId}` - 同步分析
- `POST /api/analysis/{datasetId}/stream` - 流式分析 (SSE)

### 模型
- `GET /api/models` - 可用模型列表

## License

MIT
