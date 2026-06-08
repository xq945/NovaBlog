package com.novablog.common;

import lombok.Data;

import java.util.List;

/**
 * 分页结果封装类
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> {

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页数据列表
     */
    private List<T> list;

    public PageResult() {
    }

    public PageResult(Long total, List<T> list) {
        this.total = total;
        this.list = list;
    }
}
