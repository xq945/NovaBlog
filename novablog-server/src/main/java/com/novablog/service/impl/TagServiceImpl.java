package com.novablog.service.impl;

import com.novablog.entity.Tag;
import com.novablog.mapper.TagMapper;
import com.novablog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
