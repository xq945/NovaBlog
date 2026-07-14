package com.novablog.query;

import lombok.Data;

@Data
public class CommentQuery {
    private Integer page = 1;
    private Integer size = 10;
    private Long articleId;
}
