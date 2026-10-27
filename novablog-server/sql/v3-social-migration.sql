-- V3.0 收藏 / 关注 / 站内通知迁移脚本
-- 在 init.sql + v2-rbac-migration.sql 之后执行，脚本幂等，可重复运行
-- 执行方式：mysql -u root -p novablog < v3-social-migration.sql

SET NAMES utf8mb4;

-- ============================================================
-- 1. 既有表补字段
-- ============================================================
DROP PROCEDURE IF EXISTS `add_social_columns`;
DELIMITER //
CREATE PROCEDURE `add_social_columns`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND COLUMN_NAME = 'favorite_count'
    ) THEN
        ALTER TABLE `article` ADD COLUMN `favorite_count` BIGINT NOT NULL DEFAULT 0 COMMENT '收藏数' AFTER `like_count`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'follower_count'
    ) THEN
        ALTER TABLE `user` ADD COLUMN `follower_count` BIGINT NOT NULL DEFAULT 0 COMMENT '粉丝数' AFTER `muted`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'following_count'
    ) THEN
        ALTER TABLE `user` ADD COLUMN `following_count` BIGINT NOT NULL DEFAULT 0 COMMENT '关注数' AFTER `follower_count`;
    END IF;
END //
DELIMITER ;
CALL `add_social_columns`();
DROP PROCEDURE IF EXISTS `add_social_columns`;

-- ============================================================
-- 2. 新建表
-- ============================================================
CREATE TABLE IF NOT EXISTS `article_favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '收藏者ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_favorite_user_article` (`user_id`, `article_id`) COMMENT '幂等：同一用户重复收藏只有一条',
    KEY `idx_favorite_article_id` (`article_id`) COMMENT '按文章统计',
    KEY `idx_favorite_user_time` (`user_id`, `create_time`) COMMENT '我的收藏列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章收藏表';

CREATE TABLE IF NOT EXISTS `user_follow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `follower_id` BIGINT NOT NULL COMMENT '关注者ID',
    `followee_id` BIGINT NOT NULL COMMENT '被关注者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follow_follower_followee` (`follower_id`, `followee_id`) COMMENT '幂等：重复关注只有一条',
    KEY `idx_follow_follower_time` (`follower_id`, `create_time`) COMMENT '我的关注列表',
    KEY `idx_follow_followee_time` (`followee_id`, `create_time`) COMMENT '粉丝列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注表';

CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `receiver_id` BIGINT NOT NULL COMMENT '接收人ID',
    `sender_id` BIGINT DEFAULT NULL COMMENT '触发人ID（系统通知为空）',
    `type` VARCHAR(20) NOT NULL COMMENT '类型：LIKE/COMMENT/REPLY/FOLLOW/SYSTEM',
    `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型：ARTICLE/COMMENT/USER',
    `target_id` BIGINT DEFAULT NULL COMMENT '目标ID（多态，不加外键）',
    `article_id` BIGINT DEFAULT NULL COMMENT '关联文章ID（用于跳转）',
    `content` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '通知文案',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：1-已读 0-未读',
    `read_time` DATETIME DEFAULT NULL COMMENT '已读时间',
    `dedup_key` VARCHAR(100) DEFAULT NULL COMMENT '去重键，点赞/关注填写，评论留空',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notification_receiver_dedup` (`receiver_id`, `dedup_key`) COMMENT '可空唯一键：评论为 NULL 不参与去重',
    KEY `idx_notification_receiver_read_time` (`receiver_id`, `is_read`, `create_time`) COMMENT '未读列表',
    KEY `idx_notification_receiver_time` (`receiver_id`, `create_time`) COMMENT '全部列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';

-- ============================================================
-- 3. 外键（幂等：已存在则跳过）
-- ============================================================
DROP PROCEDURE IF EXISTS `add_fk_if_missing`;
DELIMITER //
CREATE PROCEDURE `add_fk_if_missing`(
    IN p_table VARCHAR(64),
    IN p_constraint VARCHAR(64),
    IN p_ddl VARCHAR(500))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND CONSTRAINT_NAME = p_constraint
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL `add_fk_if_missing`('article_favorite', 'fk_favorite_user',
    'ALTER TABLE `article_favorite` ADD CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE');
CALL `add_fk_if_missing`('article_favorite', 'fk_favorite_article',
    'ALTER TABLE `article_favorite` ADD CONSTRAINT `fk_favorite_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE');

CALL `add_fk_if_missing`('user_follow', 'fk_follow_follower',
    'ALTER TABLE `user_follow` ADD CONSTRAINT `fk_follow_follower` FOREIGN KEY (`follower_id`) REFERENCES `user` (`id`) ON DELETE CASCADE');
CALL `add_fk_if_missing`('user_follow', 'fk_follow_followee',
    'ALTER TABLE `user_follow` ADD CONSTRAINT `fk_follow_followee` FOREIGN KEY (`followee_id`) REFERENCES `user` (`id`) ON DELETE CASCADE');

CALL `add_fk_if_missing`('notification', 'fk_notification_receiver',
    'ALTER TABLE `notification` ADD CONSTRAINT `fk_notification_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `user` (`id`) ON DELETE CASCADE');
CALL `add_fk_if_missing`('notification', 'fk_notification_sender',
    'ALTER TABLE `notification` ADD CONSTRAINT `fk_notification_sender` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`) ON DELETE SET NULL');
CALL `add_fk_if_missing`('notification', 'fk_notification_article',
    'ALTER TABLE `notification` ADD CONSTRAINT `fk_notification_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE');

DROP PROCEDURE IF EXISTS `add_fk_if_missing`;

-- ============================================================
-- 4. 校准已有数据的计数（关系表为空时为 0，安全执行）
-- ============================================================
UPDATE `article` a
SET a.`favorite_count` = (SELECT COUNT(*) FROM `article_favorite` f WHERE f.`article_id` = a.`id`);

UPDATE `user` u
SET u.`follower_count` = (SELECT COUNT(*) FROM `user_follow` f WHERE f.`followee_id` = u.`id`),
    u.`following_count` = (SELECT COUNT(*) FROM `user_follow` f WHERE f.`follower_id` = u.`id`);
