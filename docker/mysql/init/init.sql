-- NovaBlog Docker 初始化脚本（合并 schema + seed）
-- MySQL Docker 容器首次启动时自动执行

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. 基础表
-- ============================================

DROP TABLE IF EXISTS `article_like`;
CREATE TABLE `article_like` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id`     BIGINT NOT NULL COMMENT '点赞用户ID',
    `article_id`  BIGINT NOT NULL COMMENT '被点赞文章ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    UNIQUE KEY `uk_user_article` (`user_id`, `article_id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章点赞记录表';

DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id`     BIGINT NOT NULL COMMENT '收藏用户ID',
    `article_id`  BIGINT NOT NULL COMMENT '收藏文章ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY `uk_user_article` (`user_id`, `article_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章收藏表';

DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `session_id`  BIGINT NOT NULL COMMENT '所属会话ID',
    `role`        VARCHAR(20) NOT NULL COMMENT '角色：user/assistant',
    `content`     LONGTEXT NOT NULL COMMENT '消息内容（Markdown）',
    `sources_json` JSON DEFAULT NULL COMMENT 'AI 回答引用的来源文章',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_message_session_id` (`session_id`),
    KEY `idx_message_session_create_time` (`session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息表';

DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id`       BIGINT NOT NULL COMMENT '用户ID',
    `title`         VARCHAR(200) NOT NULL COMMENT '会话标题（AI 自动生成）',
    `message_count` INT NOT NULL DEFAULT 0 COMMENT '消息数量',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_session_user_id` (`user_id`),
    KEY `idx_session_user_update_time` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话表';

DROP TABLE IF EXISTS `article_tag`;
CREATE TABLE `article_tag` (
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `tag_id`     BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`article_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章-标签关联表';

DROP TABLE IF EXISTS `article_chunk`;
CREATE TABLE `article_chunk` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `article_id`  BIGINT NOT NULL COMMENT '文章ID',
    `chunk_index` INT NOT NULL COMMENT '片段序号',
    `content`     TEXT NOT NULL COMMENT '纯文本片段内容',
    `embedding`   JSON NOT NULL COMMENT '向量数组（Float32列表）',
    `token_count` INT DEFAULT 0 COMMENT '片段token估算数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_article_chunk` (`article_id`, `chunk_index`),
    KEY `idx_article_chunk_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章向量分片表';

DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `content`     TEXT NOT NULL COMMENT '评论内容',
    `article_id`  BIGINT NOT NULL COMMENT '文章ID',
    `user_id`     BIGINT NOT NULL COMMENT '评论者ID',
    `parent_id`   BIGINT DEFAULT NULL COMMENT '父评论ID（一级评论为NULL）',
    `reply_to_id` BIGINT DEFAULT NULL COMMENT '被回复用户ID',
    `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_comment_article_status_parent_time` (`article_id`, `status`, `parent_id`, `create_time`),
    KEY `idx_comment_parent_status_time` (`parent_id`, `status`, `create_time`),
    KEY `idx_comment_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '标题',
    `content`     LONGTEXT NOT NULL COMMENT '正文（Markdown）',
    `summary`     VARCHAR(500) DEFAULT NULL COMMENT '摘要',
    `cover`       VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `view_count`  BIGINT NOT NULL DEFAULT 0 COMMENT '浏览量',
    `like_count`  BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `user_id`     BIGINT NOT NULL COMMENT '作者ID',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-已发布 0-草稿',
    `indexed`     TINYINT NOT NULL DEFAULT 0 COMMENT '向量索引状态：0-未索引 1-已索引 2-索引失败',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_article_user_id` (`user_id`),
    KEY `idx_article_category_id` (`category_id`),
    KEY `idx_article_status_time` (`status`, `create_time`),
    KEY `idx_article_status_category_time` (`status`, `category_id`, `create_time`),
    FULLTEXT KEY `idx_article_fulltext` (`title`, `summary`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `name`        VARCHAR(50) NOT NULL COMMENT '标签名称',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '分类描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `username`    VARCHAR(50) NOT NULL COMMENT '用户名',
    `password`    CHAR(60) NOT NULL COMMENT '密码（BCrypt加密，固定60字符）',
    `nickname`    VARCHAR(100) NOT NULL COMMENT '昵称',
    `avatar`      VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `bio`         VARCHAR(200) DEFAULT '' COMMENT '个人简介',
    `role`        VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
    `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_user_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 2. RBAC + 日志表
-- ============================================

DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       DEFAULT NULL COMMENT '操作用户ID',
    `username`    VARCHAR(50)  DEFAULT '' COMMENT '操作用户名',
    `operation`   VARCHAR(50)  NOT NULL COMMENT '操作类型',
    `target`      VARCHAR(50)  NOT NULL COMMENT '操作对象',
    `target_id`   BIGINT       DEFAULT NULL COMMENT '操作对象ID',
    `detail`      VARCHAR(500) DEFAULT '' COMMENT '操作详情',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_target` (`target`, `target_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

DROP TABLE IF EXISTS `login_log`;
CREATE TABLE `login_log` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT       DEFAULT NULL COMMENT '登录用户ID',
    `username`   VARCHAR(50)  NOT NULL COMMENT '登录时使用的用户名',
    `ip`         VARCHAR(50)  DEFAULT '' COMMENT '登录IP',
    `user_agent` VARCHAR(500) DEFAULT '' COMMENT '浏览器UA',
    `success`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否成功: 0=失败, 1=成功',
    `reason`     VARCHAR(200) DEFAULT '' COMMENT '失败原因',
    `login_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `role_id`       BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
    `id`      BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL UNIQUE COMMENT '权限标识',
    `description` VARCHAR(200) DEFAULT '' COMMENT '权限描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(50)  NOT NULL UNIQUE COMMENT '角色标识',
    `description` VARCHAR(200) DEFAULT '' COMMENT '角色描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ============================================
-- 3. 外键约束
-- ============================================

ALTER TABLE `article`
    ADD CONSTRAINT `fk_article_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
    ADD CONSTRAINT `fk_article_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT;

ALTER TABLE `article_tag`
    ADD CONSTRAINT `fk_at_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_at_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE RESTRICT;

ALTER TABLE `comment`
    ADD CONSTRAINT `fk_comment_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
    ADD CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_comment_reply_to` FOREIGN KEY (`reply_to_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;

ALTER TABLE `chat_session`
    ADD CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

ALTER TABLE `chat_message`
    ADD CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE;

ALTER TABLE `favorite`
    ADD CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_favorite_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE;

ALTER TABLE `article_like`
    ADD CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_like_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 4. 种子数据
-- ============================================

-- 4a. 角色
INSERT INTO `role` (`name`, `description`) VALUES
('ADMIN', '管理员，拥有全部权限'),
('USER', '普通用户，可发布文章和评论'),
('MODERATOR', '内容审核员，可管理文章和评论');

-- 4b. 权限
INSERT INTO `permission` (`name`, `description`) VALUES
('user:list',        '查询用户列表'),
('user:detail',      '查看用户详情'),
('user:disable',     '停用/启用用户'),
('user:delete',      '删除用户'),
('article:create',   '发布文章'),
('article:update',   '修改所有文章'),
('article:update:own', '修改自己的文章'),
('article:delete',   '删除所有文章'),
('article:delete:own', '删除自己的文章'),
('comment:create',   '发表评论'),
('comment:delete',   '删除所有评论'),
('comment:delete:own', '删除自己的评论'),
('category:create',  '创建分类'),
('category:update',  '修改分类'),
('category:delete',  '删除分类'),
('tag:create',       '创建标签'),
('tag:update',       '修改标签'),
('tag:delete',       '删除标签'),
('system:config',    '系统配置');

-- 4c. 角色-权限映射
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT (SELECT `id` FROM `role` WHERE `name` = 'ADMIN'), `id` FROM `permission`;

INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'article:create')),
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'article:update:own')),
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'article:delete:own')),
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'comment:create')),
((SELECT `id` FROM `role` WHERE `name` = 'USER'), (SELECT `id` FROM `permission` WHERE `name` = 'comment:delete:own'));

INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
((SELECT `id` FROM `role` WHERE `name` = 'MODERATOR'), (SELECT `id` FROM `permission` WHERE `name` = 'article:update')),
((SELECT `id` FROM `role` WHERE `name` = 'MODERATOR'), (SELECT `id` FROM `permission` WHERE `name` = 'article:delete')),
((SELECT `id` FROM `role` WHERE `name` = 'MODERATOR'), (SELECT `id` FROM `permission` WHERE `name` = 'comment:delete'));

-- 4d. 管理员账号（密码：Admin@123）
INSERT INTO `user` (`username`, `password`, `nickname`, `avatar`, `email`, `bio`, `role`, `status`) VALUES
('admin', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '管理员', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'admin@novablog.com', 'NovaBlog 管理员', 'ADMIN', 1);

-- 4e. 将 admin 映射到 ADMIN 角色
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `user` u, `role` r
WHERE u.username = 'admin' AND r.name = 'ADMIN';

-- 4f. 测试用户（密码均为 Admin@123）
INSERT INTO `user` (`username`, `password`, `nickname`, `avatar`, `email`, `bio`, `role`, `status`) VALUES
('alice', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '爱丽丝', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'alice@example.com', 'Java 后端开发', 'USER', 1),
('bob', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '鲍勃', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'bob@example.com', '全栈工程师', 'USER', 1),
('carol', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '卡罗尔', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'carol@example.com', '学生', 'USER', 1),
('dave', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '戴夫', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'dave@example.com', 'C++ 选手', 'USER', 1);

-- 4g. 测试用户角色映射
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `user` u, `role` r
WHERE u.username IN ('alice', 'bob', 'carol', 'dave') AND r.name = 'USER';

-- 4h. 分类
INSERT INTO `category` (`name`, `description`) VALUES
('面经', '面试经验、八股文、复习笔记'),
('项目笔记', '项目实战中的设计思路与踩坑记录'),
('面试复盘', '真实面试流程回顾与反思总结');

-- 4i. 标签
INSERT INTO `tag` (`name`) VALUES
('Java基础'), ('集合框架'), ('JVM'), ('并发编程'), ('Spring'),
('SpringBoot'), ('SpringMVC'), ('MyBatis'), ('MySQL'), ('Redis'),
('消息队列'), ('Kafka'), ('RocketMQ'), ('RabbitMQ'), ('微服务'),
('分布式'), ('Netty'), ('计算机网络'), ('操作系统'), ('设计模式'),
('数据结构与算法'), ('LeetCode'), ('场景题'), ('项目深挖'), ('八股文');

-- 4j. 文章
INSERT INTO `article` (`id`, `title`, `content`, `summary`, `cover`, `view_count`, `like_count`, `user_id`, `category_id`, `status`, `indexed`) VALUES
(1, 'Java 基础：== 与 equals 的区别及重写 equals 为什么必须重写 hashCode', '## 引言\n\n在 Java 面试中，`==` 和 `equals()` 是最基础也最容易被追问的知识点。\n\n## == 运算符\n\n`==` 对于基本数据类型比较的是值，对于引用类型比较的是对象的内存地址。\n\n## equals 方法\n\n`equals()` 是 Object 类的方法，默认实现与 `==` 相同，即比较地址。但 String、Integer 等包装类都重写了 equals，用于比较内容。\n\n## 为什么重写 equals 必须重写 hashCode\n\n当自定义对象需要按业务字段比较时，也要重写 equals。一旦重写 equals，就必须同时重写 hashCode，原因是为了保证：两个 equals 相等的对象，hashCode 也必须相等。否则在使用 HashMap、HashSet 等基于哈希的集合时会出现逻辑错误。\n\n## 最佳实践\n\n使用 IDE 自动生成，或者借助 Lombok 的 `@EqualsAndHashCode` 注解。', '深入讲解 == 与 equals 的区别，以及重写 equals 必须重写 hashCode 的原因', 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800', 1250, 86, 2, 1, 1, 1),
(2, '集合框架：ArrayList 与 LinkedList 的区别及扩容机制', '## ArrayList\n\n基于数组实现，查询快、增删慢（中间位置需要移动元素），扩容时默认增长为原来的 1.5 倍。\n\n## LinkedList\n\n基于双向链表实现，增删快、查询慢，不需要扩容，但每个节点需要额外存储前后指针，内存占用更大。\n\n## 扩容源码\n\nArrayList 扩容源码：`newCapacity = oldCapacity + (oldCapacity >> 1)`，即 1.5 倍。扩容时会创建新数组并调用 `Arrays.copyOf` 复制元素，因此频繁扩容会有性能开销。建议在创建时指定大致容量。\n\n## fail-fast 机制\n\n并发修改时通过 modCount 检测并抛出 ConcurrentModificationException。', '对比 ArrayList 和 LinkedList，剖析 ArrayList 的 1.5 倍扩容机制', 'https://images.unsplash.com/photo-1555099962-4199c345e5dd?w=800', 980, 62, 2, 1, 1, 1),
(3, 'JVM 面试八股：内存模型、垃圾回收与类加载机制', '## JVM 内存模型\n\n主要包括程序计数器、虚拟机栈、本地方法栈、堆、方法区。其中堆是 GC 的主要区域，分为年轻代和老年代。\n\n## 垃圾回收器\n\n- Serial：单线程，适合小型应用。\n- Parallel：吞吐量优先，JDK 8 默认。\n- CMS：低停顿，但碎片多，JDK 14 已移除。\n- G1：区域化分代，兼顾吞吐和延迟，JDK 9+ 默认。\n- ZGC/Shenandoah：超低延迟，适合大堆。\n\n## 类加载过程\n\n加载 -> 验证 -> 准备 -> 解析 -> 初始化。双亲委派模型是类加载的核心机制。\n\n## OOM 排查\n\n通过 `jps` 找到进程，再用 `jmap -heap` 或 `jmap -dump` 生成堆转储，最后用 MAT/VisualVM 分析大对象。', '系统梳理 JVM 内存模型、垃圾回收器对比和类加载机制', 'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=800', 1560, 112, 3, 1, 1, 1),
(4, '并发编程：synchronized 与 ReentrantLock 的区别', '## synchronized\n\nJVM 层面的隐式锁，使用简单，自动释放锁。\n\n## ReentrantLock\n\nAPI 层面的显式锁，需要手动 lock/unlock，但功能更强大。\n\n## 主要区别\n\n1. ReentrantLock 可以尝试非阻塞获取锁（tryLock）、可中断获取锁（lockInterruptibly）、可超时获取锁。\n2. ReentrantLock 支持公平锁和非公平锁，synchronized 只能是非公平锁。\n3. ReentrantLock 可以绑定多个 Condition，实现更精细的线程通信。\n4. synchronized 在 JDK 6 之后经过锁升级优化（无锁 -> 偏向锁 -> 轻量级锁 -> 重量级锁），性能已经大幅提升。\n\n## 选择建议\n\n需求简单优先使用 synchronized；需要灵活控制时再选择 ReentrantLock。', '对比 synchronized 和 ReentrantLock 的实现与使用场景', 'https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800', 870, 58, 2, 1, 1, 1),
(5, 'MySQL 索引：B+Tree、最左前缀与覆盖索引', '## B+Tree\n\nMySQL InnoDB 使用 B+Tree 作为索引结构。与 B-Tree 相比，B+Tree 的非叶子节点只存键值，所有数据都存储在叶子节点，并且叶子节点通过链表相连，更适合范围查询和顺序读取。\n\n## 最左前缀原则\n\n联合索引 `(a, b, c)` 可以被 `a`、`a,b`、`a,b,c` 查询用到，但 `b` 或 `c` 单独查询无法命中。\n\n## 覆盖索引\n\n如果查询所需的所有字段都在索引中，就无需回表，能显著提升性能。\n\n## 索引失效场景\n\n对索引列使用函数、隐式类型转换、like 以 % 开头、不符合最左前缀、使用 != 或 is not null 等。', '讲解 MySQL B+Tree 索引、最左前缀原则和覆盖索引优化', 'https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?w=800', 1320, 94, 3, 1, 1, 1),
(6, 'Redis 高频面试题：缓存穿透、击穿、雪崩及解决方案', '## 缓存穿透\n\n查询一个数据库中也不存在的数据，导致每次请求都打到数据库。解决方案是布隆过滤器，或者对空值也进行短时间的缓存。\n\n## 缓存击穿\n\n某个热点 key 过期瞬间，大量请求同时访问数据库。解决方案是加互斥锁，或者设置热点 key 永不过期并异步更新。\n\n## 缓存雪崩\n\n大量 key 同时过期，数据库压力骤增。解决方案是过期时间加随机值，或者使用多级缓存。\n\n## 持久化\n\nRDB 适合做全量备份，AOF 数据更完整但文件较大，生产环境通常两者结合使用。', '详解 Redis 缓存三件套及持久化机制', 'https://images.unsplash.com/photo-1512820790803-83ca734da794?w=800', 1680, 136, 2, 1, 1, 1),
(7, 'SpringBoot 自动配置原理：@SpringBootApplication 拆解', '## @SpringBootApplication\n\n三个注解的组合：\n- `@Configuration`：标记该类为配置类。\n- `@ComponentScan`：开启组件扫描，默认扫描当前包及其子包。\n- `@EnableAutoConfiguration`：开启自动配置。\n\n## 自动配置流程\n\n通过 `@Import` 导入 `AutoConfigurationImportSelector`，读取 spring.factories 中的自动配置类。\n\n每个自动配置类上有条件注解：`@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty`。\n\n属性绑定通过 `@EnableConfigurationProperties` 将 yml 配置映射到 XxxProperties 类。', '拆解 SpringBoot 自动配置的核心注解与加载流程', 'https://images.unsplash.com/photo-1605745341112-85968b19335b?w=800', 1100, 78, 2, 1, 1, 1),
(8, 'MyBatis 面试题：#{} 与 ${} 的区别及一级/二级缓存', '## #{} vs ${}\n\n`#{}` 是预编译处理，会将参数替换为 `?`，能有效防止 SQL 注入；`${}` 是字符串替换，有 SQL 注入风险。\n\n## 一级缓存\n\n默认开启，作用范围是 SqlSession。\n\n## 二级缓存\n\n需要手动开启，作用范围是 Mapper 命名空间。实际项目中更常用 Redis 作为分布式缓存。\n\n## 插件机制\n\n通过实现 Interceptor 接口，在 Executor、StatementHandler 等层面拦截。', '讲解 MyBatis 参数占位符区别和缓存机制', 'https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=800', 760, 48, 3, 1, 1, 1);

-- 4k. 文章-标签关联
INSERT INTO `article_tag` (`article_id`, `tag_id`) VALUES
(1, 1), (2, 2), (3, 3), (4, 4), (5, 9), (6, 10),
(7, 6), (8, 8);

-- 4m. 评论
INSERT INTO `comment` (`content`, `article_id`, `user_id`, `parent_id`, `reply_to_id`, `status`) VALUES
('讲得很清楚，hashCode 那块终于懂了！', 1, 3, NULL, NULL, 1),
('补充一下，Integer 的 equals 比较的是 intValue。', 1, 4, NULL, NULL, 1),
('ArrayList 扩容为什么是 1.5 倍而不是 2 倍？', 2, 2, NULL, NULL, 1),
('1.5 倍是在内存占用和扩容频率之间取平衡，2 倍可能浪费更多内存。', 2, 3, 3, 2, 1),
('CMS 为什么会产生浮动垃圾？', 3, 2, NULL, NULL, 1),
('Redis 缓存击穿和穿透确实容易混淆，这篇区分得很清楚。', 6, 4, NULL, NULL, 1),
('SpringBoot 自动配置源码那部分可以再展开讲讲吗？', 7, 5, NULL, NULL, 1);
