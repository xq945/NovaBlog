package com.novablog.service.component;

import com.novablog.entity.Tag;
import com.novablog.mapper.ArticleTagMapper;
import com.novablog.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章标签关联组件
 * 封装文章与标签关联的增量更新逻辑
 */
@Component
@RequiredArgsConstructor
public class TagRelationComponent {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    /**
     * 单篇文章最大标签数
     */
    private static final int MAX_TAG_COUNT = 10;

    /**
     * 过滤并校验有效的标签ID（去重、过滤null、限制数量、校验存在性）
     *
     * @param tagIds 标签ID列表
     * @return 有效的标签ID列表
     */
    public List<Long> filterValidTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new ArrayList<>();
        }

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
     * 增量更新文章标签关联
     *
     * @param articleId 文章ID
     * @param tagIds    新的标签ID列表（为 null 时不更新）
     */
    public void updateArticleTags(Long articleId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }

        // 获取当前标签
        List<Tag> currentTags = tagMapper.findByArticleId(articleId);
        Set<Long> currentTagIds = currentTags.stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());

        // 过滤并校验新标签
        List<Long> validTagIds = filterValidTagIds(tagIds);
        Set<Long> validTagIdSet = new LinkedHashSet<>(validTagIds);

        // 计算差异
        Set<Long> toDelete = new HashSet<>(currentTagIds);
        toDelete.removeAll(validTagIdSet);

        Set<Long> toAdd = new HashSet<>(validTagIdSet);
        toAdd.removeAll(currentTagIds);

        // 执行增量操作
        if (!toDelete.isEmpty()) {
            articleTagMapper.deleteByArticleIdAndTagIds(articleId, new ArrayList<>(toDelete));
        }
        if (!toAdd.isEmpty()) {
            articleTagMapper.batchInsert(articleId, new ArrayList<>(toAdd));
        }
    }
}
