-- NovaBlog 数据库初始化脚本
-- 执行方式：mysql -u root -p < init.sql
-- 警告：此脚本会删除所有表并重建，仅用于全新环境初始化！

CREATE DATABASE IF NOT EXISTS novablog
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE novablog;

-- 1. 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` CHAR(60) NOT NULL COMMENT '密码（BCrypt加密，固定60字符）',
    `nickname` VARCHAR(100) NOT NULL COMMENT '昵称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `role` VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_user_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 初始化管理员账号（密码：Admin@123）
-- BCrypt 加密后的密码，强度因子 10
INSERT INTO `user` (`username`, `password`, `nickname`, `avatar`, `role`, `status`) VALUES
('admin', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '管理员', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'ADMIN', 1);

-- 2. 分类表
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '分类描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

-- 初始化分类数据（不指定id，让自增分配）
INSERT INTO `category` (`name`, `description`) VALUES
('技术', '编程、架构、工具'),
('生活', '日常、随笔、感悟'),
('设计', 'UI/UX、视觉、创意'),
('读书笔记', '阅读、学习、思考');

-- 3. 标签表
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- 初始化常用标签
INSERT INTO `tag` (`name`) VALUES
('Java'),
('SpringBoot'),
('Vue'),
('JavaScript'),
('MySQL'),
('Redis'),
('Docker'),
('Linux'),
('设计模式'),
('读书笔记');

-- 4. 文章表
DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `content` LONGTEXT NOT NULL COMMENT '正文（Markdown）',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要',
    `cover` VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `user_id` BIGINT NOT NULL COMMENT '作者ID',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-已发布 0-草稿',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_article_user_id` (`user_id`),
    KEY `idx_article_category_id` (`category_id`),
    KEY `idx_article_status_time` (`status`, `create_time`) COMMENT '首页已发布文章排序',
    KEY `idx_article_status_category_time` (`status`, `category_id`, `create_time`) COMMENT '按分类筛选+排序',
    FULLTEXT KEY `idx_article_fulltext` (`title`, `summary`) COMMENT '标题摘要全文搜索'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- 5. 文章-标签关联表
DROP TABLE IF EXISTS `article_tag`;
CREATE TABLE `article_tag` (
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`article_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章-标签关联表';

-- 6. 评论表
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID（一级评论为NULL）',
    `reply_to_id` BIGINT DEFAULT NULL COMMENT '被回复用户ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_comment_article_status_parent_time` (`article_id`, `status`, `parent_id`, `create_time`) COMMENT '一级评论查询',
    KEY `idx_comment_parent_status_time` (`parent_id`, `status`, `create_time`) COMMENT '二级回复查询',
    KEY `idx_comment_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ============================================
-- 外键约束（保证数据完整性）
-- ============================================
-- 文章表外键
ALTER TABLE `article`
    ADD CONSTRAINT `fk_article_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
    ADD CONSTRAINT `fk_article_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT;

-- 文章-标签关联表外键
ALTER TABLE `article_tag`
    ADD CONSTRAINT `fk_at_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_at_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE RESTRICT;

-- 评论表外键
ALTER TABLE `comment`
    ADD CONSTRAINT `fk_comment_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
    ADD CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_comment_reply_to` FOREIGN KEY (`reply_to_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;
