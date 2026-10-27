package com.novablog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知视图对象
 */
@Data
public class NotificationVO {

    private Long id;

    /**
     * LIKE / COMMENT / REPLY / FOLLOW / SYSTEM
     */
    private String type;

    private String targetType;

    private Long targetId;

    /**
     * 关联文章，点击通知时跳转
     */
    private Long articleId;

    private String content;

    private Boolean isRead;

    private LocalDateTime createTime;

    /**
     * 触发人，系统通知为空
     */
    private UserVO sender;
}
