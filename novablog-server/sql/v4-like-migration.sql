-- V4.0 点赞明细迁移
-- 在 init.sql + v2-rbac-migration.sql + v3-social-migration.sql 之后执行
-- 脚本幂等，可重复运行；执行方式：mysql -u root -p novablog < v4-like-migration.sql

SET NAMES utf8mb4;

-- ============================================================
-- 1. 点赞关系表
--    此前点赞明细只存在 Redis Set，Redis 重启或数据淘汰后无法恢复；
--    这里把「谁点了哪篇文章」落库，Redis 只保留计数与快速判断的缓存角色。
-- ============================================================
CREATE TABLE IF NOT EXISTS `article_like` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '点赞用户ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_like_user_article` (`user_id`, `article_id`) COMMENT '幂等：同一用户重复点赞只有一条',
    KEY `idx_like_article_id` (`article_id`) COMMENT '按文章统计与重建缓存',
    KEY `idx_like_user_time` (`user_id`, `create_time`) COMMENT '我的点赞列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章点赞关系表';

-- ============================================================
-- 2. 外键（幂等：已存在则跳过）
-- ============================================================
DROP PROCEDURE IF EXISTS `add_like_fk_if_missing`;
DELIMITER //
CREATE PROCEDURE `add_like_fk_if_missing`(
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

CALL `add_like_fk_if_missing`('article_like', 'fk_like_user',
    'ALTER TABLE `article_like` ADD CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE');
CALL `add_like_fk_if_missing`('article_like', 'fk_like_article',
    'ALTER TABLE `article_like` ADD CONSTRAINT `fk_like_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE');

DROP PROCEDURE IF EXISTS `add_like_fk_if_missing`;

-- ============================================================
-- 3. 不做计数校准
--    升级时 article_like 还是空表，直接按 COUNT(*) 校准会把历史 like_count 清零。
--    应用启动时会把 Redis 中已有的点赞成员回填到本表，再按关系表校准计数。
-- ============================================================
