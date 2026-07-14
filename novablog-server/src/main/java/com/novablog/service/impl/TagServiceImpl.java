package com.novablog.service.impl;

import com.novablog.common.annotation.AutoFillTime;
import com.novablog.common.enums.OperationType;
import com.novablog.entity.Tag;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novablog.mapper.TagMapper;
import com.novablog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标签业务层实现
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<Tag> findAll() {
        return tagMapper.findAll();
    }

    @Override
    @AutoFillTime(OperationType.INSERT)
    public Long create(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tagMapper.insert(tag);
        return tag.getId();
    }

    @Override
    @AutoFillTime(OperationType.UPDATE)
    public void update(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tagMapper.update(tag);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        tagMapper.deleteArticleTagByTagId(id);
        tagMapper.deleteById(id);
    }
}
