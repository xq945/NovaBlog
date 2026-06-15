-- NovaBlog 模拟数据种子脚本
-- 执行方式：mysql -u root -p novablog < seed_data.sql
-- 注意：此脚本假设分类/标签已存在，建议在 init.sql 之后执行

USE novablog;

-- 清理已有模拟数据（保留管理员，清空文章/评论/标签关联/普通用户）
DELETE FROM article_tag;
DELETE FROM comment;
DELETE FROM article;
DELETE FROM user WHERE id > 1;
ALTER TABLE user AUTO_INCREMENT = 2;
ALTER TABLE article AUTO_INCREMENT = 2;
ALTER TABLE comment AUTO_INCREMENT = 2;

-- ============================================
-- 1. 用户数据
-- ============================================
INSERT INTO `user` (`username`, `password`, `nickname`, `avatar`, `email`, `role`, `status`, `create_time`) VALUES
('alice', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '爱丽丝', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'alice@example.com', 'USER', 1, '2026-05-10 10:00:00'),
('bob', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '鲍勃', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'bob@example.com', 'USER', 1, '2026-05-12 14:30:00'),
('carol', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '卡罗尔', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'carol@example.com', 'USER', 1, '2026-05-15 09:15:00'),
('dave', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '戴夫', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'dave@example.com', 'USER', 0, '2026-05-20 16:45:00');

-- ============================================
-- 2. 文章数据
-- ============================================
INSERT INTO `article` (`title`, `content`, `summary`, `cover`, `view_count`, `like_count`, `user_id`, `category_id`, `status`, `create_time`, `update_time`) VALUES
('Spring Boot 3 实战入门：从零搭建个人博客', 'Spring Boot 3 带来了许多新特性，包括对 Java 17 的支持、GraalVM 原生镜像的更好支持等。本文将带你从零开始，使用 Spring Boot 3 + MyBatis + MySQL 搭建一个完整的个人博客后端。\n\n首先，我们需要创建一个新的 Maven 项目，引入 Spring Boot 3.2.0 的 parent。然后配置数据源、MyBatis 和 Redis。接下来定义实体类、Mapper 接口和 Service 层。最后编写 Controller 提供 RESTful API。\n\n在开发过程中，建议遵循分层架构，将业务逻辑写在 Service 中，Controller 只负责接收请求和返回结果。同时注意异常处理和参数校验，可以使用 `@RestControllerAdvice` 统一处理异常。', '使用 Spring Boot 3 搭建个人博客后端的完整指南', 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800', 328, 24, 2, 1, 1, '2026-05-11 09:00:00', '2026-05-11 09:00:00'),
('Vue 3 + Element Plus 暗色模式实现详解', '暗色模式已经成为现代 Web 应用的标配。本文介绍如何在 Vue 3 + Element Plus 项目中实现优雅的暗色模式切换。\n\nElement Plus 内置了暗黑主题 CSS 变量，只需引入 `dark/css-vars.css` 并在根元素切换 `dark` 类即可。我们可以结合 `useDark` 或手动监听系统主题偏好来实现切换。\n\n为了保存用户偏好，可以在 localStorage 中记录主题状态，并在应用启动时读取。如果用户没有手动设置，则默认跟随系统。', 'Vue 3 项目中暗色模式的实现方案与最佳实践', 'https://images.unsplash.com/photo-1555099962-4199c345e5dd?w=800', 256, 18, 3, 1, 1, '2026-05-13 15:30:00', '2026-05-13 15:30:00'),
('MySQL 8 索引优化：让博客查询快 10 倍', '数据库性能是博客系统的重要指标。本文从实际案例出发，讲解如何为文章表、评论表设计合适的索引。\n\n对于文章列表查询，最常用的场景是按状态和创建时间倒序排列，因此可以创建联合索引 `(status, create_time)`。如果还需要按分类筛选，可以扩展为 `(status, category_id, create_time)`。\n\n对于评论查询，通常需要按文章 ID 查询一级评论及其回复，可以创建 `(article_id, status, parent_id, create_time)` 和 `(parent_id, status, create_time)` 两个索引。', '针对博客场景的 MySQL 索引优化实战', 'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=800', 412, 31, 2, 1, 1, '2026-05-14 11:20:00', '2026-05-14 11:20:00'),
('Redis 在博客系统中的应用：浏览量与点赞', 'Redis 的高性能特性非常适合做实时计数。本文介绍如何使用 Redis 实现文章浏览量、点赞数和热门排行。\n\n对于浏览量，可以使用 String 类型 `article:view:{id}`，每次访问时执行 `INCR`。对于点赞，可以使用 Set 类型 `article:like:{id}` 存储点赞用户 ID，同时使用 `ZADD` 维护热门文章排行。\n\n为了保证数据安全，需要定时将 Redis 中的浏览量同步回 MySQL，避免 Redis 故障导致数据丢失。', 'Redis 实时计数与排行榜在博客系统中的实践', 'https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800', 189, 15, 2, 1, 1, '2026-05-16 08:45:00', '2026-05-16 08:45:00'),
('设计模式浅谈：观察者模式与事件驱动', '观察者模式是软件设计中最常用的模式之一。本文通过博客系统的评论通知场景，讲解观察者模式的实现。\n\n当用户 A 评论了用户 B 的文章时，系统可以发布一个"新评论"事件，由多个观察者处理：发送邮件通知、更新消息中心、刷新热门排行等。这样各个模块之间解耦，便于扩展。', '通过博客评论通知讲解观察者模式', 'https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?w=800', 145, 12, 3, 1, 1, '2026-05-18 19:10:00', '2026-05-18 19:10:00'),
('我的 2026 年阅读清单：技术与成长', '阅读是程序员成长的重要途径。本文分享我今年读过的一些技术书籍和成长类书籍。\n\n技术类：《深入理解 Java 虚拟机》《Redis 深度历险》《凤凰架构》。这些书帮助我更好地理解底层原理和分布式系统设计。\n\n成长类：《深度工作》《原子习惯》《非暴力沟通》。它们让我在时间管理和沟通表达上有了明显提升。', '2026 年技术阅读清单与个人成长感悟', 'https://images.unsplash.com/photo-1512820790803-83ca734da794?w=800', 98, 8, 4, 4, 1, '2026-05-21 13:25:00', '2026-05-21 13:25:00'),
('Docker 部署 SpringBoot 博客完整教程', '容器化部署可以大大简化环境配置。本文介绍如何使用 Docker 和 Docker Compose 部署 Spring Boot 博客。\n\n首先编写 Dockerfile，使用多阶段构建减小镜像体积。然后编写 docker-compose.yml，同时启动后端、MySQL 和 Redis 服务。最后配置 Nginx 反向代理，将前端静态资源和后端 API 统一暴露。', '使用 Docker 部署 Spring Boot 博客的完整步骤', 'https://images.unsplash.com/photo-1605745341112-85968b19335b?w=800', 267, 22, 2, 1, 1, '2026-05-22 16:00:00', '2026-05-22 16:00:00'),
('前端性能优化：首屏加载速度提升 50%', '性能是用户体验的关键。本文分享我在博客前端优化中的一些实践经验，包括代码分割、懒加载、图片优化等。\n\nVue Router 支持懒加载，可以将不同页面打包为独立 chunk。Element Plus 可以按需引入组件，减少打包体积。图片方面，可以使用 WebP 格式并设置合适的尺寸，避免加载过大图片。', '博客前端性能优化的实践经验总结', 'https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=800', 178, 14, 3, 1, 1, '2026-05-25 10:30:00', '2026-05-25 10:30:00'),
('Linux 常用命令速查：服务器运维篇', '对于个人开发者来说，掌握基本的 Linux 命令是必备技能。本文整理了博客服务器运维中最常用的命令。\n\n进程管理：ps、top、htop、kill。网络诊断：netstat、ss、curl、ping。日志查看：tail -f、journalctl、grep。文件操作：find、rsync、tar。掌握这些命令可以大大提高排查问题的效率。', '博客服务器运维常用 Linux 命令速查', 'https://images.unsplash.com/photo-1629654297299-c8506221ca97?w=800', 134, 11, 2, 2, 1, '2026-05-27 14:15:00', '2026-05-27 14:15:00'),
('JavaScript 异步编程：Promise 与 async/await', '异步编程是前端开发的核心技能。本文从 Promise 基础讲起，逐步深入到 async/await 的最佳实践。\n\nPromise 解决了回调地狱的问题，提供了链式调用。async/await 则让异步代码看起来像同步代码，可读性更强。处理多个并发请求时，可以使用 Promise.all；需要顺序执行时，使用 for...of 配合 await。', 'JavaScript 异步编程从 Promise 到 async/await', 'https://images.unsplash.com/photo-1579468118864-1b9ea3c0db4a?w=800', 223, 19, 3, 1, 1, '2026-05-28 09:45:00', '2026-05-28 09:45:00'),
('设计灵感：如何打造有质感的博客界面', '一个好的博客不仅要内容好，界面也要赏心悦目。本文分享一些博客 UI 设计的灵感和原则。\n\n首先是留白，适当的留白可以让页面呼吸。其次是字体层级，通过字号和字重区分标题、正文和辅助信息。最后是配色，暗色主题可以使用深灰而非纯黑，搭配一个醒目的强调色。', '博客界面设计灵感与视觉设计原则', 'https://images.unsplash.com/photo-1561070791-2526d30994b5?w=800', 87, 6, 4, 3, 1, '2026-05-30 11:00:00', '2026-05-30 11:00:00'),
('未完成的草稿：微服务架构改造思考', '这篇还在构思中，先记录一些初步想法...\n\n目前博客是单体架构，随着功能增加，可以考虑将用户服务、文章服务、评论服务拆分为独立微服务。但需要权衡运维复杂度和开发效率，对于个人博客来说可能过度设计。', '关于博客系统微服务架构改造的思考草稿', NULL, 0, 0, 2, 1, 0, '2026-06-01 20:00:00', '2026-06-01 20:00:00'),
('周末的咖啡馆与生活随想', '周末的午后，我喜欢找一家安静的咖啡馆，带上笔记本和一本书，度过几个小时。\n\n这种慢节奏的时光让我从代码和 deadlines 中抽离出来，重新感受生活。有时候灵感恰恰来自这些看似无意义的时刻。希望每个人都能找到属于自己的充电方式。', '关于周末、咖啡与慢生活的随想', 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800', 76, 5, 4, 2, 1, '2026-06-02 16:30:00', '2026-06-02 16:30:00'),
('极简主义设计：少即是多', '极简主义不是简单地减少元素，而是保留最本质的部分。\n\n在博客设计中，极简主义意味着清晰的导航、克制的配色和聚焦的内容。每一个元素都应该有存在的理由，避免为了装饰而装饰。好的设计是看不见的，它让用户专注于阅读本身。', '探讨极简主义设计在博客界面中的应用', 'https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?w=800', 112, 9, 3, 3, 1, '2026-06-03 10:00:00', '2026-06-03 10:00:00'),
('远程工作一年的心得', '远程工作已经一年了，想记录一些体会。\n\n最大的挑战是自律。没有办公室的约束，需要自己安排工作和休息的边界。我养成了固定作息、番茄工作法和每日复盘的习惯。另一个挑战是沟通，异步沟通要求表达更清晰，文档要写得足够详细。', '远程工作一年后的自律与沟通心得', 'https://images.unsplash.com/photo-1593642632823-8f78536788c6?w=800', 95, 7, 2, 2, 1, '2026-06-04 14:20:00', '2026-06-04 14:20:00');

-- ============================================
-- 3. 文章-标签关联数据
-- ============================================
INSERT INTO `article_tag` (`article_id`, `tag_id`) VALUES
(2, 1), (2, 2),   -- Spring Boot 实战：Java, SpringBoot
(3, 3), (3, 4),   -- Vue 暗色模式：Vue, JavaScript
(4, 5),           -- MySQL 索引优化：MySQL
(5, 6), (5, 2),   -- Redis 应用：Redis, SpringBoot
(6, 9),           -- 观察者模式：设计模式
(7, 10),          -- 阅读清单：读书笔记
(8, 7),           -- Docker 部署：Docker
(9, 3), (9, 4),   -- 前端性能：Vue, JavaScript
(10, 8),          -- Linux 命令：Linux
(11, 4),          -- JS 异步：JavaScript
(12, 9),          -- 设计灵感：设计模式
(15, 9);          -- 极简主义设计：设计模式

-- ============================================
-- 4. 评论数据
-- ============================================
INSERT INTO `comment` (`content`, `article_id`, `user_id`, `parent_id`, `reply_to_id`, `status`, `create_time`) VALUES
('写得很清晰，对 Spring Boot 3 的新特性有了更深的理解！', 2, 3, NULL, NULL, 1, '2026-05-11 10:30:00'),
('感谢分享，已收藏。', 2, 4, NULL, NULL, 1, '2026-05-12 08:20:00'),
('暗色模式的实现方案很实用，我也准备给项目加上。', 3, 2, NULL, NULL, 1, '2026-05-14 09:15:00'),
('请问如何保存用户的主题偏好？', 3, 4, NULL, NULL, 1, '2026-05-14 16:40:00'),
('可以配合 localStorage 和系统主题偏好一起处理。', 3, 3, 4, 4, 1, '2026-05-14 17:00:00'),
('索引优化确实很重要，我之前就踩过全表扫描的坑。', 4, 2, NULL, NULL, 1, '2026-05-15 11:20:00'),
('Redis 做热门排行很合适，感谢分享实现思路。', 5, 3, NULL, NULL, 1, '2026-05-17 14:30:00'),
('那如果 Redis 挂了，浏览量会不会丢失？', 5, 4, NULL, NULL, 1, '2026-05-17 15:00:00'),
('Docker 部署部分写得非常详细，跟着做就成功了。', 8, 4, NULL, NULL, 1, '2026-05-23 10:00:00'),
('Promise 的链式调用容易出错，async/await 确实清晰很多。', 10, 2, NULL, NULL, 1, '2026-05-29 13:45:00'),
('博客界面设计的建议很有启发，界面质感确实重要。', 12, 3, NULL, NULL, 1, '2026-05-31 15:20:00'),
('期待这篇微服务改造的最终版本！', 13, 2, NULL, NULL, 1, '2026-06-02 09:30:00'),
('这条评论用于测试软删除状态。', 2, 2, NULL, NULL, 0, '2026-05-12 12:00:00');
