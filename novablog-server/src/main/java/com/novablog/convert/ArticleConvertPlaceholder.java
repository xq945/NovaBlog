package com.novablog.convert;

/**
 * TODO [第二阶段] Entity ↔ VO 转换器
 *
 * 职责：将 Article 实体转换为 ArticleVO / ArticleDetailVO / HotArticleVO 等视图对象。
 *
 * 当前状态：转换逻辑散落在 ArticleServiceImpl、CommentServiceImpl 等 Service 中，
 * 以手动 setter 方式实现，代码冗长且重复。
 *
 * 第二阶段计划：
 * 1. 引入 MapStruct（org.mapstruct:mapstruct），自动生成转换代码
 * 2. 将 Service 中的手动 setter 替换为 Convert 调用
 * 3. 参考模式：
 *    public static ArticleVO toVO(Article entity) { ... }
 *    public static List<ArticleVO> toVOList(List<Article> entities) { ... }
 *
 * 涉及文件：
 * - ArticleServiceImpl: 多处 Article → ArticleVO 手动组装
 * - CommentServiceImpl: Comment → CommentVO 组装
 * - UserController / UserServiceImpl: User → AdminUserVO 组装（已有 UserVOAssembler）
 *
 * @see com.novablog.service.assembler.UserVOAssembler （现有手动组装器，参考迁移）
 */
public class ArticleConvertPlaceholder {
    // 占位类，第二阶段实现
    private ArticleConvertPlaceholder() {}
}
