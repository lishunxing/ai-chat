个人技术博客：<https://blog.lishunxing.cn>

# AI Chat — 博客知识库 RAG 对话后端服务

基于 Spring Boot + Spring AI 构建的 RAG（检索增强生成）对话服务，使用 Milvus 向量数据库存储博客知识，通过 DeepSeek 大模型提供流式 AI 问答，并为前端博客站点提供 SSE 流式对话 API。

## 技术选型

| 类别 | 技术 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.3.5 | Java 17，Web 层基于 Spring MVC + Reactor |
| AI 引擎 | DeepSeek | LLM，通过 OpenAI 兼容接口调用（spring-ai-openai） |
| 向量嵌入 | 千问 Embedding | 文本向量化，1,536 维，通过 OpenAI 兼容接口调用 |
| 向量数据库 | Milvus | 存储文档向量索引，支持相似度检索 |
| 持久化 | MySQL 8.x + MyBatis Plus 3.5.9 + Druid 1.2.23 | 对话记忆、文档元数据 |
| 配置中心 | Nacos | 多环境配置管理 |
| 文档解析 | PDFBox 3.x / POI 5.x | PDF、Word、Excel 解析 |
| ETL 管道 | 自研多格式解析 → 文本分割 → 向量化入库 | Markdown / PDF / Word / Excel |

## 技术架构

```
blog/ (前端 VitePress + AiChat.vue)
  │  POST /api/chat (SSE stream)
  ▼
┌─────────────────────────────────────────────────────────┐
│                   AI Chat Backend                        │
│                                                         │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Controller│  │   Service    │  │    ETL Pipeline   │  │
│  │          │  │              │  │                    │  │
│  │ Chat     │──│ RagChat      │  │ Source → Parser   │  │
│  │ Controller│  │ Service (SSE)│  │   → Splitter      │  │
│  │          │  │              │  │   → Indexer        │  │
│  │ Knowledge│  │ Ingestion    │  │   → Milvus         │  │
│  │ Controller│──│ Service      │  │                    │  │
│  └──────────┘  └──────┬───────┘  └──────────────────┘  │
│                       │                                 │
│         ┌─────────────┼─────────────┐                  │
│         ▼             ▼             ▼                  │
│  ┌────────────┐ ┌──────────┐ ┌──────────┐             │
│  │ DeepSeek   │ │  Milvus  │ │  MySQL   │             │
│  │ (LLM)      │ │  (向量DB) │ │  (元数据) │             │
│  └────────────┘ └──────────┘ └──────────┘             │
│                                                         │
│  ┌────────────┐  ┌──────────────────────────────────┐  │
│  │   千问      │  │       Data Sources                │  │
│  │ Embedding  │  │  GitHub API  │  Local FileSystem  │  │
│  └────────────┘  └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 交互流程

### 1. 文档索引流程

```
GitHub API / 本地文件
    │
    ▼
BlogDataSource.fetchAll()
    │  返回 BlogDocument (path, content, fingerprint)
    ▼
DocumentParser (按格式分发)
    │  MarkdownParser  → 按 ## 标题分节
    │  PdfParser       → PDFBox 提取文本
    │  WordParser      → POI 提取段落
    │  ExcelParser     → POI 提取单元格
    ▼
ParsedSection (heading, content, sourcePath, title)
    │
    ▼
TextSplitter.split()
    │  按句子边界分割，500 字/块，50 字重叠，保护代码块完整
    ▼
Chunk (text, metadata{source, title, heading, chunk_index})
    │
    ▼
DocumentIndexer.indexChunks()
    │  千问 Embedding 向量化（每批 ≤10 条）
    │  写入 Milvus Collection
    ▼
KnowledgeDocument 元数据写入 MySQL
```

### 2. RAG 对话流程

```
用户提问 "Spring 循环依赖怎么解决？"
    │
    ▼
RagChatService.chatStream(conversationId, message)
    │
    ├─ 1. 向量检索
    │     userMessage → 千问 Embedding → Milvus 相似度搜索 (Top K=3)
    │     返回：相关文档片段 + metadata(source, title)
    │
    ├─ 2. 上下文组装
    │     知识片段 (截断 ≤300 字/条) + 历史对话 (最近 5 轮)
    │     + System Prompt → Prompt
    │
    ├─ 3. DeepSeek 流式生成
    │     chatModel.stream(prompt)
    │     每个 chunk → SSE data: {conversationId, content}
    │
    └─ 4. SSE 流收尾
         最终帧 → SSE data: {conversationId, content, sources}
         保存对话记忆到 MySQL
```

### 3. 前端 SSE 渲染流程

```
AiChat.vue
    │  POST /api/chat (Accept: text/event-stream)
    ▼
┌─ 思考中动画 ──────────────────────────────────────────┐
│  后端正在检索 + LLM 生成                                │
│  前端显示 "思考中···" 跳动圆点动画                      │
└───────────────────────────────────────────────────────┘
    │  收到第一个 content chunk                               │
    ▼
┌─ 逐行渲染 ────────────────────────────────────────────┐
│  每 25ms 追加一行，marked() 实时解析 Markdown           │
│  自动滚动到底部                                         │
└───────────────────────────────────────────────────────┘
    │  收到最终帧 (sources)                                    │
    ▼
┌─ 渲染引用来源 ────────────────────────────────────────┐
│  可点击跳转到博客对应文章                               │
└───────────────────────────────────────────────────────┘
```

## 项目结构

```
ai-chat/
├── src/main/java/cn/lishunxing/aichat/
│   ├── AiChatApplication.java          # 启动入口
│   ├── config/
│   │   ├── CorsConfig.java             # 跨域配置
│   │   ├── DataSourceConfig.java       # 数据源策略 (GitHub/Local/Prod)
│   │   ├── DeepSeekConfig.java         # DeepSeek LLM 配置
│   │   ├── DruidConfig.java            # 数据库连接池配置
│   │   ├── MilvusConfig.java           # Milvus 向量存储配置
│   │   └── QwenEmbeddingConfig.java    # 千问 Embedding 配置
│   ├── controller/
│   │   ├── ChatController.java         # SSE 对话 API
│   │   └── KnowledgeController.java    # 知识库管理 API
│   ├── dto/
│   │   ├── ChatRequest.java            # 对话请求
│   │   └── ChatResponse.java           # 对话响应 (含 SourceRef)
│   ├── entity/
│   │   ├── ChatMemory.java             # 对话记忆实体
│   │   └── KnowledgeDocument.java      # 知识文档实体
│   ├── etl/
│   │   ├── DocumentIndexer.java        # 向量化入库
│   │   ├── DocumentParser.java         # 解析器接口
│   │   ├── MarkdownParser.java         # Markdown 解析
│   │   ├── PdfParser.java              # PDF 解析
│   │   ├── WordParser.java             # Word 解析
│   │   ├── ExcelParser.java            # Excel 解析
│   │   └── TextSplitter.java           # 文本分割
│   ├── mapper/
│   │   ├── ChatMemoryMapper.java       # 对话记忆 Mapper
│   │   └── KnowledgeDocumentMapper.java # 知识文档 Mapper
│   ├── service/
│   │   ├── IngestionService.java       # 文档摄取接口
│   │   ├── RagChatService.java         # RAG 对话接口
│   │   └── impl/
│   │       ├── IngestionServiceImpl.java
│   │       └── RagChatServiceImpl.java
│   ├── source/
│   │   ├── BlogDataSource.java         # 数据源接口
│   │   ├── BlogDocument.java           # 博客文档模型
│   │   ├── GitHubBlogDataSource.java   # GitHub API 数据源
│   │   └── LocalFileBlogDataSource.java # 本地文件数据源
│   └── watch/
│       └── BlogSyncScheduler.java      # 定时增量索引
├── src/main/resources/
│   ├── application.yml                 # 主配置
│   └── bootstrap.yml                   # Nacos 配置引导
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x
- Milvus 2.x
- Nacos 2.x (可选，不使用时可禁用)

### 配置

在 Nacos（或本地 `application.yml`）中配置以下关键参数：

```yaml
# DeepSeek
deepseek:
  api-key: your-deepseek-api-key
  base-url: https://api.deepseek.com
  chat:
    model: deepseek-chat
    temperature: 0.7

# 千问 Embedding
qwen:
  api-key: your-qwen-api-key
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  embedding:
    model: text-embedding-v2

# Milvus
milvus:
  host: localhost
  port: 19530
  collection: blog_knowledge
  vector-dimension: 1536

# 数据源
blog:
  source: github  # github | local | prod
  github:
    owner: lishunxing
    repo: blog
    branch: master
  local:
    path: ../blog
```

### 构建与运行

```bash
# 构建
mvn clean package -DskipTests

# 启动
java -jar target/ai-chat.jar --server.port=8080
```

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat` | SSE 流式对话，`Content-Type: text/event-stream` |
| `GET` | `/api/knowledge/status` | 知识库状态（已索引文档数） |
| `GET` | `/api/knowledge/documents` | 已索引文档列表 |
| `POST` | `/api/knowledge/reindex` | 触发全量重索引 |

## License

MIT
