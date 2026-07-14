package com.novablog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String summary;

    private String cover;

    private Long viewCount;

    private Long likeCount;

    private Long userId;

    private Long categoryId;

    private Integer status;

    private Integer indexed;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
