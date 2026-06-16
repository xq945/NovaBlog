-- 2026-06-15 RAG 知识库问答功能数据库迁移
-- 适用于已存在的 NovaBlog 数据库，不会删除数据

USE novablog;

-- 1. 文章表新增向量索引状态字段
ALTER TABLE `article`
    ADD COLUMN `indexed` TINYINT NOT NULL DEFAULT 0 COMMENT '向量索引状态：0-未索引 1-已索引 2-索引失败';

-- 2. 新增文章向量分片表
CREATE TABLE IF NOT EXISTS `article_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `chunk_index` INT NOT NULL COMMENT '片段序号',
    `content` TEXT NOT NULL COMMENT '纯文本片段内容',
    `embedding` JSON NOT NULL COMMENT '向量数组（Float32列表）',
    `token_count` INT DEFAULT 0 COMMENT '片段token估算数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_chunk` (`article_id`, `chunk_index`),
    KEY `idx_article_chunk_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章向量分片表';
