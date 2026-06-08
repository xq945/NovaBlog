package com.novablog.service.impl;

import com.novablog.entity.Tag;
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
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<Tag> findAll() {
        return tagMapper.findAll();
    }

    @Override
    public Long create(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tagMapper.insert(tag);
        return tag.getId();
    }

    @Override
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
