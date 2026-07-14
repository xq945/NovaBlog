-- V2.0 RBAC 迁移脚本
-- 在已有 init.sql 基础上执行，新增 RBAC 五表 + 日志表 + 数据迁移
-- 执行方式：mysql -u root -p123456 novablog < v2-rbac-migration.sql

SET NAMES utf8mb4;

-- 1. 角色表
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(50)  NOT NULL UNIQUE COMMENT '角色标识，如 ADMIN/USER/MODERATOR',
    `description` VARCHAR(200) DEFAULT '' COMMENT '角色描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '角色表';

-- 2. 权限表
DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL UNIQUE COMMENT '权限标识，如 article:create',
    `description` VARCHAR(200) DEFAULT '' COMMENT '权限描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '权限表';

-- 3. 用户-角色关联
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
    `id`      BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) COMMENT '用户角色关联表';

-- 4. 角色-权限关联
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `role_id`       BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_permission_id` (`permission_id`)
) COMMENT '角色权限关联表';

-- 5. 登录日志表
DROP TABLE IF EXISTS `login_log`;
CREATE TABLE `login_log` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT       DEFAULT NULL COMMENT '登录用户ID（失败可能为空）',
    `username`   VARCHAR(50)  NOT NULL COMMENT '登录时使用的用户名',
    `ip`         VARCHAR(50)  DEFAULT '' COMMENT '登录IP',
    `user_agent` VARCHAR(500) DEFAULT '' COMMENT '浏览器UA',
    `success`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否成功: 0=失败, 1=成功',
    `reason`     VARCHAR(200) DEFAULT '' COMMENT '失败原因',
    `login_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_login_time` (`login_time`)
) COMMENT '登录日志表';

-- 6. 操作日志表
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       DEFAULT NULL COMMENT '操作用户ID',
    `username`    VARCHAR(50)  DEFAULT '' COMMENT '操作用户名',
    `operation`   VARCHAR(50)  NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/OTHER',
    `target`      VARCHAR(50)  NOT NULL COMMENT '操作对象：ARTICLE/COMMENT/USER/ROLE/PERMISSION',
    `target_id`   BIGINT       DEFAULT NULL COMMENT '操作对象ID',
    `detail`      VARCHAR(500) DEFAULT '' COMMENT '操作详情',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_target` (`target`, `target_id`),
    KEY `idx_create_time` (`create_time`)
) COMMENT '操作日志表';

-- 7. 用户表增加 bio 字段（通过存储过程确保幂等）
DROP PROCEDURE IF EXISTS `add_bio_column`;
DELIMITER //
CREATE PROCEDURE `add_bio_column`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'bio'
    ) THEN
        ALTER TABLE `user` ADD COLUMN `bio` VARCHAR(200) DEFAULT '' COMMENT '个人简介' AFTER `email`;
    END IF;
END //
DELIMITER ;
CALL `add_bio_column`();
DROP PROCEDURE IF EXISTS `add_bio_column`;

-- ============================================================
-- 种子数据
-- ============================================================

-- 预设角色
INSERT INTO `role` (`name`, `description`) VALUES ('ADMIN', '管理员，拥有全部权限');
INSERT INTO `role` (`name`, `description`) VALUES ('USER', '普通用户，可发布文章和评论');
INSERT INTO `role` (`name`, `description`) VALUES ('MODERATOR', '内容审核员，可管理文章和评论');

-- 完整权限列表
INSERT INTO `permission` (`name`, `description`) VALUES
-- 用户模块
('user:list',             '查询用户列表'),
('user:detail',           '查看用户详情'),
('user:disable',          '停用/启用用户'),
('user:delete',           '删除用户'),
-- 文章模块
('article:create',        '发布文章'),
('article:update',        '修改所有文章'),
('article:update:own',    '修改自己的文章'),
('article:delete',        '删除所有文章'),
('article:delete:own',    '删除自己的文章'),
-- 评论模块
('comment:create',        '发表评论'),
('comment:delete',        '删除所有评论'),
('comment:delete:own',    '删除自己的评论'),
-- 分类/标签模块
('category:create',       '创建分类'),
('category:update',       '修改分类'),
('category:delete',       '删除分类'),
('tag:create',            '创建标签'),
('tag:update',            '修改标签'),
('tag:delete',            '删除标签'),
-- 系统模块
('system:config',         '系统配置');

-- ============================================================
-- 角色-权限映射
-- ============================================================

-- ADMIN: 拥有所有权限（通过将 ADMIN 角色 ID 与所有权限关联）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT (SELECT `id` FROM `role` WHERE `name` = 'ADMIN'), `id` FROM `permission`;

-- USER: 可发布/修改/删除自己的文章，发表/删除自己的评论，查看/修改个人资料
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'article:create')),
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'article:update:own')),
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'article:delete:own')),
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'comment:create')),
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'comment:delete:own'));

-- MODERATOR: 可修改/删除所有文章，删除所有评论
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
((SELECT `id` FROM `role` WHERE `name` = 'MODERATOR'), (SELECT `id` FROM `permission` WHERE `name` = 'article:update')),
((SELECT `id` FROM `role` WHERE `name` = 'MODERATOR'), (SELECT `id` FROM `permission` WHERE `name` = 'article:delete')),
((SELECT `id` FROM `role` WHERE `name` = 'MODERATOR'), (SELECT `id` FROM `permission` WHERE `name` = 'comment:delete'));

-- ============================================================
-- 数据迁移：将现有 user.role 映射到 user_role
-- ============================================================
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `user` u, `role` r
WHERE r.name = u.role;
