-- NovaBlog 面经模拟数据种子脚本
-- 执行方式：mysql -u root -p novablog < seed_data.sql
-- 说明：此脚本假设 init.sql 已执行，保留 id=1 的管理员账号，其余测试数据会先清空再重新插入

USE novablog;

-- 强制当前连接使用 utf8mb4，避免 Windows 命令行默认字符集导致中文乱码或截断
SET NAMES utf8mb4;

-- 按外键依赖顺序清空旧数据（保留管理员账号）
DELETE FROM `article_tag`;
DELETE FROM `comment`;
DELETE FROM `article`;
DELETE FROM `tag`;
DELETE FROM `category`;
DELETE FROM `user` WHERE `id` > 1;

-- ============================================
-- 1. 分类数据
-- ============================================
INSERT INTO `category` (`id`, `name`, `description`) VALUES
(1, '面经', '面试经验、八股文、复习笔记'),
(2, '项目笔记', '项目实战中的设计思路与踩坑记录'),
(3, '面试复盘', '真实面试流程回顾与反思总结');

-- ============================================
-- 2. 标签数据
-- ============================================
INSERT INTO `tag` (`id`, `name`) VALUES
(1, 'Java基础'),
(2, '集合框架'),
(3, 'JVM'),
(4, '并发编程'),
(5, 'Spring'),
(6, 'SpringBoot'),
(7, 'SpringMVC'),
(8, 'MyBatis'),
(9, 'MySQL'),
(10, 'Redis'),
(11, '消息队列'),
(12, 'Kafka'),
(13, 'RocketMQ'),
(14, 'RabbitMQ'),
(15, '微服务'),
(16, '分布式'),
(17, 'Netty'),
(18, '计算机网络'),
(19, '操作系统'),
(20, '设计模式'),
(21, '数据结构与算法'),
(22, 'LeetCode'),
(23, '场景题'),
(24, '项目深挖'),
(25, '八股文');

-- ============================================
-- 3. 用户数据（密码均为 Admin@123，创建/更新时间由数据库默认值自动填充）
-- ============================================
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `email`, `role`, `status`) VALUES
(2, 'alice', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '爱丽丝', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'alice@example.com', 'USER', 1),
(3, 'bob', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '鲍勃', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'bob@example.com', 'USER', 1),
(4, 'carol', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '卡罗尔', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'carol@example.com', 'USER', 1),
(5, 'dave', '$2b$10$nrQe9FEddVuHjxFIfSnKge3VS3UCBgMg0QDa9mTDNDBirHMYyxs6m', '戴夫', 'https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg', 'dave@example.com', 'USER', 1);

-- ============================================
-- 4. 文章数据（id 显式指定，避免依赖自增起始值；创建/更新时间由数据库默认值自动填充）
-- ============================================
INSERT INTO `article` (`id`, `title`, `content`, `summary`, `cover`, `view_count`, `like_count`, `user_id`, `category_id`, `status`, `indexed`) VALUES
(1, 'Java 基础：== 与 equals 的区别及重写 equals 为什么必须重写 hashCode', '在 Java 面试中，`==` 和 `equals()` 是最基础也最容易被追问的知识点。\n\n`==` 对于基本数据类型比较的是值，对于引用类型比较的是对象的内存地址。\n\n`equals()` 是 Object 类的方法，默认实现与 `==` 相同，即比较地址。但 String、Integer 等包装类都重写了 equals，用于比较内容。\n\n当自定义对象需要按业务字段比较时，也要重写 equals。一旦重写 equals，就必须同时重写 hashCode，原因是为了保证：两个 equals 相等的对象，hashCode 也必须相等。否则在使用 HashMap、HashSet 等基于哈希的集合时会出现逻辑错误。\n\n最佳实践是使用 IDE 自动生成，或者借助 Lombok 的 `@EqualsAndHashCode` 注解。', '深入讲解 == 与 equals 的区别，以及重写 equals 必须重写 hashCode 的原因', 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800', 1250, 86, 2, 1, 1, 1),
(2, '集合框架：ArrayList 与 LinkedList 的区别及扩容机制', 'ArrayList 基于数组实现，查询快、增删慢（中间位置需要移动元素），扩容时默认增长为原来的 1.5 倍。\n\nLinkedList 基于双向链表实现，增删快、查询慢，不需要扩容，但每个节点需要额外存储前后指针，内存占用更大。\n\nArrayList 扩容源码：`newCapacity = oldCapacity + (oldCapacity >> 1)`，即 1.5 倍。扩容时会创建新数组并调用 `Arrays.copyOf` 复制元素，因此频繁扩容会有性能开销。建议在创建时指定大致容量。\n\n面试中常考的还有 fail-fast 机制，即并发修改时通过 modCount 检测并抛出 ConcurrentModificationException。', '对比 ArrayList 和 LinkedList，剖析 ArrayList 的 1.5 倍扩容机制', 'https://images.unsplash.com/photo-1555099962-4199c345e5dd?w=800', 980, 62, 2, 1, 1, 1),
(3, 'JVM 面试八股：内存模型、垃圾回收与类加载机制', 'JVM 内存模型主要包括程序计数器、虚拟机栈、本地方法栈、堆、方法区。其中堆是 GC 的主要区域，分为年轻代和老年代。\n\n常见垃圾回收器：\n- Serial：单线程，适合小型应用。\n- Parallel：吞吐量优先，JDK 8 默认。\n- CMS：低停顿，但碎片多，JDK 14 已移除。\n- G1：区域化分代，兼顾吞吐和延迟，JDK 9+ 默认。\n- ZGC/Shenandoah：超低延迟，适合大堆。\n\n类加载过程：加载 -> 验证 -> 准备 -> 解析 -> 初始化。双亲委派模型是类加载的核心机制，即先交给父加载器加载，避免核心类被篡改。\n\n面试常问的 OOM 排查：先通过 `jps` 找到进程，再用 `jmap -heap` 或 `jmap -dump` 生成堆转储，最后用 MAT/VisualVM 分析大对象。', '系统梳理 JVM 内存模型、垃圾回收器对比和类加载机制', 'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=800', 1560, 112, 3, 1, 1, 1),
(4, '并发编程：synchronized 与 ReentrantLock 的区别', '`synchronized` 是 JVM 层面的隐式锁，使用简单，自动释放锁；`ReentrantLock` 是 API 层面的显式锁，需要手动 lock/unlock，但功能更强大。\n\n主要区别：\n1. ReentrantLock 可以尝试非阻塞获取锁（tryLock）、可中断获取锁（lockInterruptibly）、可超时获取锁。\n2. ReentrantLock 支持公平锁和非公平锁，synchronized 只能是非公平锁。\n3. ReentrantLock 可以绑定多个 Condition，实现更精细的线程通信。\n4. synchronized 在 JDK 6 之后经过锁升级优化（无锁 -> 偏向锁 -> 轻量级锁 -> 重量级锁），性能已经大幅提升。\n\n实际项目中，如果需求简单优先使用 synchronized；需要灵活控制时再选择 ReentrantLock。', '对比 synchronized 和 ReentrantLock 的实现与使用场景', 'https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800', 870, 58, 2, 1, 1, 1),
(5, 'MySQL 索引：B+Tree、最左前缀与覆盖索引', 'MySQL InnoDB 使用 B+Tree 作为索引结构。与 B-Tree 相比，B+Tree 的非叶子节点只存键值，所有数据都存储在叶子节点，并且叶子节点通过链表相连，更适合范围查询和顺序读取。\n\n最左前缀原则：联合索引 `(a, b, c)` 可以被 `a`、`a,b`、`a,b,c` 查询用到，但 `b` 或 `c` 单独查询无法命中。\n\n覆盖索引：如果查询所需的所有字段都在索引中，就无需回表，能显著提升性能。\n\n索引失效的常见场景：对索引列使用函数、隐式类型转换、like 以 % 开头、不符合最左前缀、使用 != 或 is not null 等。', '讲解 MySQL B+Tree 索引、最左前缀原则和覆盖索引优化', 'https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?w=800', 1320, 94, 3, 1, 1, 1),
(6, 'Redis 高频面试题：缓存穿透、击穿、雪崩及解决方案', 'Redis 作为缓存使用时，有三个经典问题：\n\n1. 缓存穿透：查询一个数据库中也不存在的数据，导致每次请求都打到数据库。解决方案是布隆过滤器，或者对空值也进行短时间的缓存。\n\n2. 缓存击穿：某个热点 key 过期瞬间，大量请求同时访问数据库。解决方案是加互斥锁，或者设置热点 key 永不过期并异步更新。\n\n3. 缓存雪崩：大量 key 同时过期，数据库压力骤增。解决方案是过期时间加随机值，或者使用多级缓存。\n\n另外 Redis 持久化有 RDB 和 AOF 两种方式。RDB 适合做全量备份，AOF 数据更完整但文件较大，生产环境通常两者结合使用。', '详解 Redis 缓存三件套及持久化机制', 'https://images.unsplash.com/photo-1512820790803-83ca734da794?w=800', 1680, 136, 2, 1, 1, 1),
(7, 'SpringBoot 自动配置原理：@SpringBootApplication 拆解', '`@SpringBootApplication` 是三个注解的组合：\n- `@Configuration`：标记该类为配置类。\n- `@ComponentScan`：开启组件扫描，默认扫描当前包及其子包。\n- `@EnableAutoConfiguration`：开启自动配置，这是 SpringBoot 最核心的注解。\n\n`@EnableAutoConfiguration` 通过 `@Import` 导入 `AutoConfigurationImportSelector`，该类会读取所有 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 文件中配置的自动配置类。\n\n每个自动配置类上通常有 `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 等条件注解，只有条件满足时才会生效。\n\n自动配置类的属性绑定通过 `@EnableConfigurationProperties` 将 application.yml 中的配置映射到 XxxProperties 类中。', '拆解 SpringBoot 自动配置的核心注解与加载流程', 'https://images.unsplash.com/photo-1605745341112-85968b19335b?w=800', 1100, 78, 2, 1, 1, 1),
(8, 'MyBatis 面试题：#{} 与 ${} 的区别及一级/二级缓存', '`#{}` 是预编译处理，会将参数替换为 `?`，能有效防止 SQL 注入；`${}` 是字符串替换，直接拼接到 SQL 中，有 SQL 注入风险，一般用于动态表名、列名等场景。\n\nMyBatis 一级缓存默认开启，作用范围是 SqlSession，同一个 SqlSession 内相同的查询会命中缓存。\n\n二级缓存需要手动开启，作用范围是 Mapper 命名空间。开启后，多个 SqlSession 可以共享缓存，但数据更新时需要保证缓存一致性。实际项目中，更常用 Redis 作为分布式缓存替代 MyBatis 二级缓存。\n\n面试中还要注意 MyBatis 的插件机制：通过实现 Interceptor 接口并配置 @Intercepts 注解，可以在 Executor、StatementHandler、ParameterHandler、ResultSetHandler 四个层面进行拦截。', '讲解 MyBatis 参数占位符区别和缓存机制', 'https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=800', 760, 48, 3, 1, 1, 1),
(9, '消息队列面试：Kafka 的高吞吐与可靠性设计', 'Kafka 之所以能做到高吞吐，主要依靠以下设计：\n\n1. 顺序写磁盘：Kafka 采用追加写日志的方式，顺序 IO 性能接近内存。\n2. 零拷贝：通过 sendfile 系统调用减少数据在内核态和用户态之间的拷贝次数。\n3. 批量处理：Producer 和 Consumer 都支持批量发送/拉取消息。\n4. 分区并行：Topic 分为多个 Partition，不同 Partition 可以并行读写。\n\n可靠性方面：\n- Producer 可配置 acks=all，确保 ISR 中所有副本都写入成功。\n- 通过副本机制保证数据不丢，Leader 故障后会从 ISR 中选举新 Leader。\n- Consumer 通过 offset 管理消费进度，支持手动提交和自动提交。', '剖析 Kafka 高性能与可靠性实现原理', 'https://images.unsplash.com/photo-1629654297299-c8506221ca97?w=800', 920, 64, 2, 1, 1, 1),
(10, '计算机网络：TCP 三次握手与四次挥手', 'TCP 是面向连接的可靠传输协议，连接建立需要三次握手，断开需要四次挥手。\n\n三次握手：\n1. 客户端发送 SYN，进入 SYN_SENT 状态。\n2. 服务端回复 SYN+ACK，进入 SYN_RECV 状态。\n3. 客户端回复 ACK，双方进入 ESTABLISHED 状态。\n\n为什么是三次？为了防止历史重复连接初始化造成混乱，同时同步双方初始序列号。\n\n四次挥手：\n1. 客户端发送 FIN，进入 FIN_WAIT_1。\n2. 服务端回复 ACK，进入 CLOSE_WAIT。\n3. 服务端发送 FIN，进入 LAST_ACK。\n4. 客户端回复 ACK，进入 TIME_WAIT，等待 2MSL 后关闭。\n\nTIME_WAIT 的作用：确保最后一个 ACK 能到达对方，以及让旧连接的报文在网络中消失。', '图解 TCP 三次握手与四次挥手的状态转换', 'https://images.unsplash.com/photo-1579468118864-1b9ea3c0db4a?w=800', 1180, 82, 3, 1, 1, 1),
(11, '操作系统：进程与线程的区别，以及进程间通信方式', '进程是资源分配的基本单位，拥有独立的地址空间；线程是 CPU 调度的基本单位，同一进程内的线程共享进程资源。\n\n进程间通信（IPC）方式：\n- 管道：半双工，适合父子进程通信。\n- 命名管道（FIFO）：可以在无亲缘关系的进程间通信。\n- 消息队列：消息的链表，适合异步通信。\n- 共享内存：最快的 IPC 方式，需要配合信号量进行同步。\n- 信号量：用于进程/线程同步。\n- 信号：用于通知进程发生了某个事件。\n- Socket：可用于不同机器间的进程通信。\n\n线程间同步方式：互斥锁、读写锁、条件变量、信号量。', '对比进程与线程，梳理常见 IPC 和线程同步方式', 'https://images.unsplash.com/photo-1561070791-2526d30994b5?w=800', 650, 42, 4, 1, 1, 1),
(12, '设计模式：单例模式的几种写法及线程安全问题', '单例模式确保一个类只有一个实例，并提供一个全局访问点。常见写法：\n\n1. 饿汉式：类加载时就创建实例，线程安全，但可能造成资源浪费。\n2. 懒汉式（线程不安全）：首次使用时创建，多线程下可能创建多个实例。\n3. 懒汉式（synchronized）：线程安全，但同步粒度大，性能差。\n4. 双重检查锁定（DCL）：使用 volatile + 双重 if 检查，既线程安全又高性能。\n5. 静态内部类：利用类加载机制保证线程安全，延迟加载，是推荐使用的方式。\n6. 枚举：最简洁、线程安全、防止反射和反序列化破坏单例，Effective Java 推荐。\n\n破坏单例的方式：反射调用私有构造器、反序列化。可以通过构造器判断、readResolve 方法或枚举来防御。', '详解单例模式的 6 种写法及线程安全分析', 'https://images.unsplash.com/photo-1593642632823-8f78536788c6?w=800', 890, 56, 2, 1, 1, 1),
(13, '分布式面试：CAP 理论与 BASE 理论', 'CAP 理论指出分布式系统无法同时满足一致性（Consistency）、可用性（Availability）、分区容错性（Partition Tolerance），最多只能同时满足其中两项。\n\n由于网络分区不可避免，分区容错性 P 必须保证，因此实际只能在 C 和 A 之间做权衡：\n- CP：保证一致性，分区时牺牲可用性，如 ZooKeeper、etcd。\n- AP：保证可用性，分区时允许数据不一致，如 Eureka、Cassandra。\n\nBASE 理论是对 AP 的延伸：\n- Basically Available：基本可用，允许部分功能降级。\n- Soft state：软状态，允许中间状态存在。\n- Eventually consistent：最终一致性，不要求实时一致。\n\n实际业务中，很多场景通过最终一致性换取高可用，例如电商库存扣减、订单状态异步同步等。', '解读 CAP 和 BASE 理论及在分布式系统中的应用', 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800', 1050, 74, 2, 1, 1, 1),
(14, '场景题：如何设计一个高并发的短链系统？', '短链系统面试常考，核心是把长 URL 映射成短 URL，并支持高效跳转。\n\n设计要点：\n1. 短码生成：可以使用自增 ID + Base62 编码，或者分布式 ID（雪花算法）+ Base62。\n2. 映射存储：Redis 缓存热点短链，数据库持久化全量映射。\n3. 跳转：根据短码从 Redis 查询长链，命中则 302 跳转，未命中回源数据库。\n4. 防重复：生成后先查库确认是否冲突，雪花算法可极大降低冲突概率。\n5. 限流：防止恶意刷短链，对生成接口做限流。\n6. 统计：记录点击次数、地域、时间等，用于数据分析。\n\n面试加分项：布隆过滤器防止缓存穿透、短链过期策略、一致性哈希做分库分表。', '短链系统设计：生成、存储、跳转与防刷策略', 'https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?w=800', 730, 50, 4, 1, 1, 1),
(15, '八股文速记：Spring 中的 Bean 生命周期', 'Spring Bean 从创建到销毁经历以下阶段：\n\n1. 实例化：通过构造器创建对象。\n2. 属性赋值：注入依赖。\n3. 如果实现了 Aware 接口，调用 setBeanName、setBeanFactory、setApplicationContext。\n4. 调用 BeanPostProcessor.postProcessBeforeInitialization。\n5. 如果实现了 InitializingBean，调用 afterPropertiesSet。\n6. 如果配置了 init-method，调用自定义初始化方法。\n7. 调用 BeanPostProcessor.postProcessAfterInitialization（AOP 代理多在此阶段生成）。\n8. Bean 处于就绪状态，可被使用。\n9. 容器关闭时，如果实现了 DisposableBean，调用 destroy。\n10. 如果配置了 destroy-method，调用自定义销毁方法。\n\n理解 Bean 生命周期对排查依赖注入问题、理解 AOP 原理都很有帮助。', '图解 Spring Bean 生命周期各阶段', 'https://images.unsplash.com/photo-1605745341112-85968b19335b?w=800', 580, 38, 3, 1, 1, 1),
(16, '项目笔记：个人博客系统的全栈架构设计', '本项目 NovaBlog 采用前后端分离架构，后端使用 SpringBoot 3 + MyBatis + MySQL + Redis，前端使用 Vue 3 + Vite + Element Plus。\n\n核心模块划分：\n- 用户认证：JWT + 拦截器实现无状态登录。\n- 文章管理：CRUD、分类标签、浏览/点赞计数。\n- 评论系统：支持一级评论和二级回复。\n- AI 对话：基于向量检索的 RAG 问答，使用文章分片作为知识库。\n\n设计亮点：\n1. Redis + MySQL 双写：实时计数存 Redis，定时同步回 MySQL。\n2. 向量分片：文章拆分为 chunk 并生成 embedding，支持语义检索。\n3. RESTful API：无 /api 前缀，前端通过 Vite 代理转发。\n\n踩坑记录：Windows 下 MySQL 命令行默认字符集不是 utf8mb4，导致中文插入报错，需要在脚本开头加 `SET NAMES utf8mb4`。', '记录 NovaBlog 项目的架构选型与设计思路', 'https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=800', 420, 28, 2, 2, 1, 1),
(17, '面试复盘：某大厂 Java 后端一面', '面试岗位：Java 后端开发实习生\n面试时长：约 45 分钟\n\n问题清单：\n1. 自我介绍 + 项目经历（约 5 分钟）\n2. ArrayList 扩容机制，为什么是 1.5 倍？\n3. HashMap 的 put 流程，扩容时链表转红黑树的阈值。\n4. synchronized 锁升级过程。\n5. MySQL 索引最左前缀原则，联合索引 (a,b,c) 哪些查询能命中？\n6. Redis 缓存穿透、击穿、雪崩的区别和解决方案。\n7. 算法题：LeetCode 3 无重复字符的最长子串。\n\n反思：\n- 锁升级过程答得不够流畅，需要再梳理偏向锁、轻量级锁、重量级锁的状态转换。\n- 算法题虽然做出来了，但边界条件考虑不周，第一次提交超时。\n- 项目亮点讲得比较清楚，面试官对 RAG 部分比较感兴趣。', '复盘一次真实的后端面试流程与得失', 'https://images.unsplash.com/photo-1454165804606-c3d57bc86b40?w=800', 310, 22, 2, 3, 1, 1);

-- ============================================
-- 5. 文章-标签关联数据
-- ============================================
INSERT INTO `article_tag` (`article_id`, `tag_id`) VALUES
(1, 1),   -- Java 基础
(2, 2),   -- 集合框架
(3, 3),   -- JVM
(4, 4),   -- 并发编程
(5, 9),   -- MySQL
(6, 10),  -- Redis
(7, 6),   -- SpringBoot
(8, 8),   -- MyBatis
(9, 12),  -- Kafka
(10, 18), -- 计算机网络
(11, 19), -- 操作系统
(12, 20), -- 设计模式
(13, 16), -- 分布式
(14, 23), -- 场景题
(15, 5),  -- Spring
(16, 24), -- 项目深挖
(17, 25); -- 八股文

-- ============================================
-- 6. 评论数据（id 显式指定，parent_id 指向父评论，reply_to_id 指向被回复用户；创建时间由数据库默认值自动填充）
-- ============================================
INSERT INTO `comment` (`id`, `content`, `article_id`, `user_id`, `parent_id`, `reply_to_id`, `status`) VALUES
-- 文章 1 的评论
(1, '讲得很清楚，hashCode 那块终于懂了！', 1, 3, NULL, NULL, 1),
(2, '补充一下，Integer 的 equals 比较的是 intValue。', 1, 4, NULL, NULL, 1),
-- 文章 2 的评论及回复
(3, 'ArrayList 扩容为什么是 1.5 倍而不是 2 倍？', 2, 2, NULL, NULL, 1),
(4, '1.5 倍是在内存占用和扩容频率之间取平衡，2 倍可能浪费更多内存。', 2, 3, 3, 2, 1),
-- 文章 3 的评论
(5, 'CMS 为什么会产生浮动垃圾？', 3, 2, NULL, NULL, 1),
-- 文章 6 的评论
(6, 'Redis 缓存击穿和穿透确实容易混淆，这篇区分得很清楚。', 6, 4, NULL, NULL, 1),
-- 文章 7 的评论
(7, 'SpringBoot 自动配置源码那部分可以再展开讲讲吗？', 7, 5, NULL, NULL, 1),
-- 文章 9 的评论
(8, 'Kafka 的 ISR 机制讲得很好。', 9, 2, NULL, NULL, 1),
-- 文章 10 的评论及回复
(9, 'TIME_WAIT 为什么是 2MSL 而不是 1MSL？', 10, 3, NULL, NULL, 1),
(10, '2MSL 是为了保证最后一个 ACK 到达，并让旧连接报文在网络中消失。', 10, 4, 9, 3, 1),
-- 文章 12 的评论
(11, '枚举单例是最优解，面试可以直接写这个。', 12, 2, NULL, NULL, 1),
-- 文章 14 的评论
(12, '短链系统用雪花算法做短码需要注意什么？', 14, 5, NULL, NULL, 1),
-- 文章 16 的评论
(13, 'Windows 下 MySQL 字符集问题我也遇到过，加 SET NAMES 确实有效。', 16, 3, NULL, NULL, 1),
-- 一条被软删除的评论，用于测试删除态展示
(14, '这条评论用于测试软删除状态。', 1, 2, NULL, NULL, 0);
