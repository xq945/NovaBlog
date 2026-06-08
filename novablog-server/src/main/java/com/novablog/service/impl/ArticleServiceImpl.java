package com.novablog.service.impl;

import com.novablog.common.PageResult;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.ArticleDTO;
import com.novablog.entity.Article;
import com.novablog.entity.Category;
import com.novablog.entity.Tag;
import com.novablog.mapper.ArticleMapper;
import com.novablog.mapper.ArticleTagMapper;
import com.novablog.mapper.CategoryMapper;
import com.novablog.mapper.TagMapper;
import com.novablog.service.ArticleService;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章业务层实现
 */
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 摘要自动截取长度
     */
    private static final int SUMMARY_MAX_LENGTH = 150;

    /**
     * 单篇文章最大标签数
     */
    private static final int MAX_TAG_COUNT = 10;

    @Override
    @Transactional
    public Long publish(ArticleDTO articleDTO) {
        // 1. 参数校验
        String title = articleDTO.getTitle();
        String content = articleDTO.getContent();
        if (title == null || title.isEmpty() || title.length() > 100) {
            throw new BusinessException(400, "标题不能为空且长度不能超过100字符");
        }
        if (content == null || content.isEmpty() || content.length() > 50000) {
            throw new BusinessException(400, "正文不能为空且长度不能超过50000字符");
        }
        if (articleDTO.getCategoryId() == null) {
            throw new BusinessException(400, "分类不能为空");
        }

        // 2. 校验分类是否存在
        Category category = categoryMapper.findById(articleDTO.getCategoryId());
        if (category == null) {
            throw new BusinessException(400, "分类不存在");
        }

        // 3. 处理摘要
        String summary = articleDTO.getSummary();
        if (summary == null || summary.isEmpty()) {
            summary = extractSummary(content);
        } else if (summary.length() > 500) {
            throw new BusinessException(400, "摘要长度不能超过500字符");
        }

        // 4. 处理状态（默认已发布，非法值视为已发布）
        Integer status = articleDTO.getStatus();
        if (status == null || (status != 0 && status != 1)) {
            status = 1;
        }

        // 5. 构建文章实体
        Article article = new Article();
        article.setTitle(title);
        article.setContent(content);
        article.setSummary(summary);
        article.setCover(articleDTO.getCover());
        article.setUserId(UserContext.getUserId());
        article.setCategoryId(articleDTO.getCategoryId());
        article.setStatus(status);

        // 6. 插入文章
        articleMapper.insert(article);
        Long articleId = article.getId();

        // 7. 关联标签（去重、过滤null、限制数量）
        List<Long> tagIds = articleDTO.getTagIds();
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Long> validTagIds = filterValidTagIds(tagIds);
            if (!validTagIds.isEmpty()) {
                articleTagMapper.batchInsert(articleId, validTagIds);
            }
        }

        return articleId;
    }

    @Override
    public PageResult<ArticleVO> findList(Integer page, Integer size, Long categoryId, String keyword) {
        // 1. 分页参数修正
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }

        // 2. 处理关键词：去除首尾空格，过滤SQL通配符
        if (keyword != null) {
            keyword = keyword.trim();
            if (!keyword.isEmpty()) {
                keyword = keyword.replaceAll("[%_*]", "");
            }
            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        // 3. 计算偏移量
        int offset = (page - 1) * size;

        // 4. 查询列表和总数
        List<ArticleVO> list = articleMapper.findList(categoryId, keyword, offset, size);
        Long total = articleMapper.countList(categoryId, keyword);

        // 5. 查询每篇文章的标签
        for (ArticleVO article : list) {
            List<String> tagNames = findTagNamesByArticleId(article.getId());
            article.setTags(tagNames);
        }

        return new PageResult<>(total, list);
    }

    @Override
    public ArticleDetailVO findDetail(Long id) {
        // 1. 查询文章（不限制状态）
        ArticleDetailVO detail = articleMapper.findDetailById(id);
        if (detail == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // 2. 草稿权限判断：仅作者本人可查看
        if (detail.getStatus() != null && detail.getStatus() == 0) {
            Long currentUserId = UserContext.getUserId();
            Long authorId = detail.getAuthor() != null ? detail.getAuthor().getId() : null;
            if (currentUserId == null || !currentUserId.equals(authorId)) {
                throw new BusinessException(404, "文章不存在");
            }
            // 草稿不增加浏览量
        } else {
            // 3. 已发布文章：浏览量 +1（原生SQL）
            articleMapper.incrementViewCount(id);
            detail.setViewCount(detail.getViewCount() + 1);
        }

        // 4. 查询标签
        List<Tag> tags = tagMapper.findByArticleId(id);
        List<TagVO> tagVOList = tags.stream().map(t -> {
            TagVO vo = new TagVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            return vo;
        }).collect(Collectors.toList());
        detail.setTagList(tagVOList);

        // 设置标签名称列表（兼容列表页字段）
        List<String> tagNames = tags.stream().map(Tag::getName).collect(Collectors.toList());
        detail.setTags(tagNames);

        return detail;
    }

    @Override
    @Transactional
    public void update(ArticleDTO articleDTO) {
        Long articleId = articleDTO.getId();
        if (articleId == null) {
            throw new BusinessException(400, "文章ID不能为空");
        }

        // 1. 查询文章是否存在
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // 2. 权限校验
        checkPermission(article.getUserId());

        // 3. 参数校验
        String title = articleDTO.getTitle();
        String content = articleDTO.getContent();
        if (title != null && (title.isEmpty() || title.length() > 100)) {
            throw new BusinessException(400, "标题不能为空且长度不能超过100字符");
        }
        if (content != null && (content.isEmpty() || content.length() > 50000)) {
            throw new BusinessException(400, "正文不能为空且长度不能超过50000字符");
        }

        // 4. 校验分类是否存在（若传入）
        if (articleDTO.getCategoryId() != null) {
            Category category = categoryMapper.findById(articleDTO.getCategoryId());
            if (category == null) {
                throw new BusinessException(400, "分类不存在");
            }
        }

        // 5. 处理摘要
        String summary = articleDTO.getSummary();
        if (content != null && (summary == null || summary.isEmpty())) {
            summary = extractSummary(content);
        }

        // 6. 构建更新实体
        Article updateArticle = new Article();
        updateArticle.setId(articleId);
        if (title != null) {
            updateArticle.setTitle(title);
        }
        if (content != null) {
            updateArticle.setContent(content);
        }
        if (summary != null) {
            updateArticle.setSummary(summary);
        }
        if (articleDTO.getCover() != null) {
            updateArticle.setCover(articleDTO.getCover());
        }
        if (articleDTO.getCategoryId() != null) {
            updateArticle.setCategoryId(articleDTO.getCategoryId());
        }
        // status 仅允许 0 或 1，其他值忽略
        if (articleDTO.getStatus() != null && (articleDTO.getStatus() == 0 || articleDTO.getStatus() == 1)) {
            updateArticle.setStatus(articleDTO.getStatus());
        }

        // 7. 更新文章（update_time 在 Mapper XML 中手动设置）
        articleMapper.update(updateArticle);

        // 8. 标签关联处理
        List<Long> tagIds = articleDTO.getTagIds();
        if (tagIds != null) {
            articleTagMapper.deleteByArticleId(articleId);
            if (!tagIds.isEmpty()) {
                List<Long> validTagIds = filterValidTagIds(tagIds);
                if (!validTagIds.isEmpty()) {
                    articleTagMapper.batchInsert(articleId, validTagIds);
                }
            }
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 1. 查询文章是否存在
        Article article = articleMapper.findById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // 2. 权限校验
        checkPermission(article.getUserId());

        // 3. 删除标签关联
        articleTagMapper.deleteByArticleId(id);

        // 4. 删除文章
        articleMapper.deleteById(id);
    }

    @Override
    public PageResult<ArticleVO> findMyArticles(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        // 1. 分页参数修正
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }

        // 2. 计算偏移量
        int offset = (page - 1) * size;

        // 3. 查询列表和总数
        List<ArticleVO> list = articleMapper.findByUserId(userId, offset, size);
        Long total = articleMapper.countByUserId(userId);

        // 4. 查询每篇文章的标签
        for (ArticleVO article : list) {
            List<String> tagNames = findTagNamesByArticleId(article.getId());
            article.setTags(tagNames);
        }

        return new PageResult<>(total, list);
    }

    /**
     * 从正文提取摘要
     *
     * @param content 正文
     * @return 摘要
     */
    private String extractSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 去除Markdown标记：行内代码、代码块、标题标记、链接、图片等
        String plainText = content
                .replaceAll("```[\\s\\S]*?```", "")     // 代码块
                .replaceAll("`([^`]+)`", "$1")           // 行内代码
                .replaceAll("!\\[.*?\\]\\(.*?\\)", "")   // 图片
                .replaceAll("\\[([^\\]]+)\\]\\(.*?\\)", "$1") // 链接
                .replaceAll("#{1,6}\\s*", "")            // 标题标记
                .replaceAll("\\*+|_+|~+|`+", "")        // 粗体、斜体、删除线
                .replaceAll("\\n+", " ")                 // 换行转空格
                .trim();

        if (plainText.length() <= SUMMARY_MAX_LENGTH) {
            return plainText;
        }
        return plainText.substring(0, SUMMARY_MAX_LENGTH);
    }

    /**
     * 过滤有效的标签ID（去重、过滤null、限制数量）
     *
     * @param tagIds 标签ID列表
     * @return 存在的标签ID列表
     */
    private List<Long> filterValidTagIds(List<Long> tagIds) {
        // 去重并保持顺序
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId != null) {
                uniqueIds.add(tagId);
            }
        }

        // 校验存在性并限制数量
        List<Long> validIds = new ArrayList<>();
        for (Long tagId : uniqueIds) {
            if (tagMapper.findById(tagId) != null) {
                validIds.add(tagId);
                if (validIds.size() >= MAX_TAG_COUNT) {
                    break;
                }
            }
        }
        return validIds;
    }

    /**
     * 根据文章ID查询标签名称列表
     *
     * @param articleId 文章ID
     * @return 标签名称列表
     */
    private List<String> findTagNamesByArticleId(Long articleId) {
        List<Tag> tags = tagMapper.findByArticleId(articleId);
        return tags.stream().map(Tag::getName).collect(Collectors.toList());
    }

    /**
     * 校验当前用户是否有权操作指定文章
     *
     * @param authorId 文章作者ID
     */
    private void checkPermission(Long authorId) {
        Long currentUserId = UserContext.getUserId();
        String currentRole = UserContext.getRole();

        // 自己或管理员可以操作
        if (!authorId.equals(currentUserId) && !"ADMIN".equals(currentRole)) {
            throw new BusinessException(403, "无权操作");
        }
    }
}
