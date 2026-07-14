package com.novablog.common.enums;

import lombok.Getter;

@Getter
public enum ArticleStatus {
    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布");

    private final int code;
    private final String desc;

    ArticleStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
