-- 为已有 user 表添加 avatar 字段（头像 URL）
-- 执行方式：mysql -u root -p123456 novablog < sql/alter_add_avatar.sql

-- 先检查字段是否已存在，不存在则添加
SET @exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND column_name = 'avatar'
);

SET @sql = IF(@exists = 0,
    'ALTER TABLE `user` ADD COLUMN `avatar` VARCHAR(500) DEFAULT NULL COMMENT ''头像URL'' AFTER `nickname`',
    'SELECT ''avatar 字段已存在，无需添加'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
