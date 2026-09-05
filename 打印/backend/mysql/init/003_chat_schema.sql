CREATE DATABASE IF NOT EXISTS chatbot_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON `chatbot_db`.* TO 'print_shop'@'%';

USE chatbot_db;

CREATE TABLE IF NOT EXISTS knowledge_categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库分类';

CREATE TABLE IF NOT EXISTS knowledge_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question VARCHAR(500) NOT NULL,
    answer TEXT NOT NULL,
    category VARCHAR(80) NOT NULL DEFAULT '通用',
    tags VARCHAR(1000) DEFAULT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FULLTEXT KEY idx_knowledge_fulltext (question, answer, category, tags),
    KEY idx_knowledge_category (category),
    KEY idx_knowledge_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库条目';

CREATE TABLE IF NOT EXISTS chat_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_type VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(80) NOT NULL,
    question VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL,
    source VARCHAR(40) NOT NULL,
    source_ids VARCHAR(200) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chat_logs_user (user_type, user_id),
    KEY idx_chat_logs_session (session_id),
    KEY idx_chat_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服对话日志';

CREATE TABLE IF NOT EXISTS chat_settings (
    id TINYINT PRIMARY KEY, enabled TINYINT(1) NOT NULL, mode VARCHAR(30) NOT NULL,
    deepseek_enabled TINYINT(1) NOT NULL, allowed_tools TEXT NOT NULL, system_prompt TEXT NOT NULL,
    system_prompt_version VARCHAR(80) NOT NULL DEFAULT 'v1', model VARCHAR(100) NOT NULL,
    temperature DECIMAL(4,2) NOT NULL, max_tokens INT NOT NULL,
    hotline VARCHAR(40) NOT NULL, service_hours VARCHAR(80) NOT NULL,
    handoff_message VARCHAR(500) NOT NULL DEFAULT '当前问题需要人工客服进一步处理。',
    updated_by BIGINT DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服运行策略';

CREATE TABLE IF NOT EXISTS chat_setting_audits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, admin_id BIGINT NOT NULL, before_json LONGTEXT NOT NULL,
    after_json LONGTEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chat_setting_audits_admin (admin_id), KEY idx_chat_setting_audits_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服配置审计';

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, filename VARCHAR(255) NOT NULL, title VARCHAR(255) NOT NULL,
    category VARCHAR(80) NOT NULL, version INT NOT NULL, checksum CHAR(64) NOT NULL,
    storage_path VARCHAR(500) NOT NULL, status VARCHAR(30) NOT NULL, enabled TINYINT(1) NOT NULL DEFAULT 1,
    chunk_count INT NOT NULL DEFAULT 0, error_message VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_knowledge_documents_status (status), KEY idx_knowledge_documents_enabled (enabled),
    KEY idx_knowledge_documents_checksum (checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Markdown 知识文档';

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, document_id BIGINT NOT NULL, chunk_index INT NOT NULL,
    heading VARCHAR(500) NOT NULL DEFAULT '', content MEDIUMTEXT NOT NULL,
    token_estimate INT NOT NULL DEFAULT 0, vector_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_chunks_document_index (document_id, chunk_index),
    KEY idx_knowledge_chunks_document (document_id), FULLTEXT KEY idx_knowledge_chunks_fulltext (heading, content),
    CONSTRAINT fk_knowledge_chunks_document FOREIGN KEY (document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Markdown 知识块';

CREATE TABLE IF NOT EXISTS knowledge_ingestion_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, document_id BIGINT NOT NULL, status VARCHAR(30) NOT NULL,
    progress INT NOT NULL DEFAULT 0, error_message VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ingestion_jobs_document (document_id), KEY idx_ingestion_jobs_status (status),
    CONSTRAINT fk_ingestion_jobs_document FOREIGN KEY (document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档摄取任务';

CREATE TABLE IF NOT EXISTS chat_conversations (
    id CHAR(32) PRIMARY KEY, user_type VARCHAR(20) NOT NULL, user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL, status VARCHAR(30) NOT NULL, summary TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_conversations_owner (user_type, user_id, updated_at), KEY idx_conversations_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服会话';

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, conversation_id CHAR(32) NOT NULL, role VARCHAR(20) NOT NULL,
    content MEDIUMTEXT NOT NULL, source VARCHAR(40) NOT NULL DEFAULT '', sources_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_messages_conversation (conversation_id, id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服会话消息';

CREATE TABLE IF NOT EXISTS agent_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, conversation_id CHAR(32) NOT NULL, status VARCHAR(30) NOT NULL,
    mode VARCHAR(30) NOT NULL, model VARCHAR(100) NOT NULL, steps INT NOT NULL DEFAULT 0,
    duration_ms INT NOT NULL DEFAULT 0, error_message VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_agent_runs_conversation (conversation_id),
    KEY idx_agent_runs_created (created_at),
    CONSTRAINT fk_agent_runs_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 执行记录';

CREATE TABLE IF NOT EXISTS agent_tool_calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, run_id BIGINT NOT NULL, tool_name VARCHAR(80) NOT NULL,
    arguments_json TEXT NOT NULL, status VARCHAR(30) NOT NULL, result_summary VARCHAR(1000) NOT NULL DEFAULT '',
    duration_ms INT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tool_calls_run (run_id), KEY idx_tool_calls_name (tool_name),
    CONSTRAINT fk_tool_calls_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 工具调用审计';

CREATE TABLE IF NOT EXISTS chat_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, message_id BIGINT NOT NULL, user_type VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL, rating SMALLINT NOT NULL, comment VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feedback_message_owner (message_id, user_type, user_id), KEY idx_feedback_rating (rating),
    CONSTRAINT fk_feedback_message FOREIGN KEY (message_id) REFERENCES chat_messages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服回答反馈';

INSERT IGNORE INTO knowledge_categories (name, description) VALUES
('下单打印', '文件上传、打印参数、订单创建与模拟支付'),
('证件照', '证件照生成、换底色、保存相册'),
('课表', '教务账号绑定、课表查询和同步'),
('客服', '人工客服与常见售后问题');

INSERT INTO knowledge_items (question, answer, category, tags, enabled)
SELECT '如何上传文件并下单打印？',
       '在首页选择打印服务，上传 PDF、Word、Excel 或图片文件，选择门店和打印参数后提交订单。当前支付为模拟支付，用于演示订单状态流转。',
       '下单打印',
       '["上传","下单","打印","模拟支付"]',
       1
WHERE NOT EXISTS (SELECT 1 FROM knowledge_items WHERE question = '如何上传文件并下单打印？');

INSERT INTO knowledge_items (question, answer, category, tags, enabled)
SELECT '证件照可以做哪些处理？',
       '证件照功能支持上传照片后生成常用尺寸证件照，也支持白底、蓝底、红底等背景替换。处理完成后可以预览并保存到相册。',
       '证件照',
       '["证件照","换底色","保存"]',
       1
WHERE NOT EXISTS (SELECT 1 FROM knowledge_items WHERE question = '证件照可以做哪些处理？');

INSERT INTO knowledge_items (question, answer, category, tags, enabled)
SELECT '课表同步失败怎么办？',
       '请先确认已绑定正确的教务账号和密码。若提示教务同步适配器未配置，说明当前环境还没有接入真实学校教务系统，可先查看已缓存课表或稍后再试。',
       '课表',
       '["课表","教务","同步"]',
       1
WHERE NOT EXISTS (SELECT 1 FROM knowledge_items WHERE question = '课表同步失败怎么办？');

INSERT INTO knowledge_items (question, answer, category, tags, enabled)
SELECT '如何联系人工客服？',
       '如 AI 客服无法解决问题，请拨打客服电话 400-778-1811，服务时间为 9:00-22:00。',
       '客服',
       '["人工客服","电话","热线"]',
       1
WHERE NOT EXISTS (SELECT 1 FROM knowledge_items WHERE question = '如何联系人工客服？');
