package com.novablog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novablog.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {

    List<Tag> findAll();

    Long create(String name);

    void update(Long id, String name);

    void delete(Long id);
}
