-- AI 对话记录功能数据库迁移
-- 执行方式：mysql -u root -p novablog < sql/migration/20250616-chat-session.sql

USE novablog;

-- AI 对话会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '会话标题（AI 自动生成）',
    `message_count` INT NOT NULL DEFAULT 0 COMMENT '消息数量',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_user_id` (`user_id`) COMMENT '查询用户会话列表',
    KEY `idx_session_user_update_time` (`user_id`, `update_time`) COMMENT '用户会话按更新时间排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话表';

-- AI 对话消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id` BIGINT NOT NULL COMMENT '所属会话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色：user/assistant',
    `content` LONGTEXT NOT NULL COMMENT '消息内容（Markdown）',
    `sources_json` JSON DEFAULT NULL COMMENT 'AI 回答引用的来源文章',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_message_session_id` (`session_id`) COMMENT '查询会话消息列表',
    KEY `idx_message_session_create_time` (`session_id`, `create_time`) COMMENT '会话消息按时间排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息表';

-- 外键约束
ALTER TABLE `chat_session`
    ADD CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

ALTER TABLE `chat_message`
    ADD CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE;
