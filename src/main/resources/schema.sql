-- =============================================
-- AI 文档对话机器人 - 数据库建表脚本
-- 适用: MySQL 5.7.28
-- 数据库: ai_blog (需先手动创建)
-- 执行: mysql -u root -p ai_blog < schema.sql
-- =============================================

-- 知识文档索引记录表
-- 记录每篇博客 Markdown 文件的索引状态
CREATE TABLE IF NOT EXISTS knowledge_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    file_path       VARCHAR(500) NOT NULL COMMENT '博客文档相对路径, 如 java/spring/IoC.md',
    title           VARCHAR(300) DEFAULT NULL COMMENT '文档标题, 从 # 一级标题提取',
    md5_hash        VARCHAR(64)  DEFAULT NULL COMMENT '文件内容 MD5 哈希, 用于变更检测',
    chunk_count     INT          DEFAULT 0 COMMENT '该文档被拆分为多少个切片',
    last_indexed_at DATETIME     DEFAULT NULL COMMENT '最后索引时间',
    UNIQUE KEY uk_file_path (file_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档索引记录表';

-- 对话历史记录表
-- 存储用户与 AI 助手的对话消息
CREATE TABLE IF NOT EXISTS chat_memory (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    conversation_id VARCHAR(64)  NOT NULL COMMENT '会话ID, UUID格式',
    message_type    VARCHAR(16)  NOT NULL COMMENT '消息类型: user(用户) / assistant(助手)',
    content         TEXT         NOT NULL COMMENT '消息内容',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话历史记录表';
