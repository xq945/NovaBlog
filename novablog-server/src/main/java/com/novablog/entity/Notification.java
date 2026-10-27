package com.novablog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知
 *
 * <p>一条通知 = 接收人 + 触发人 + 动作类型 + 目标对象。通知是业务数据，
 * 与业务写入同事务落库；SSE 推送只是提交后的尽力而为。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收人
     */
    private Long receiverId;

    /**
     * 触发人，系统通知为 null
     */
    private Long senderId;

    /**
     * 类型：LIKE / COMMENT / REPLY / FOLLOW / SYSTEM
     */
    private String type;

    /**
     * 目标类型：ARTICLE / COMMENT / USER
     */
    private String targetType;

    /**
     * 目标 ID，多态字段，不做外键
     */
    private Long targetId;

    /**
     * 关联文章 ID，用于点击跳转
     */
    private Long articleId;

    /**
     * 通知文案
     */
    private String content;

    /**
     * 1-已读 0-未读
     */
    private Integer isRead;

    private LocalDateTime readTime;

    /**
     * 去重键。点赞与关注填写固定串，评论与回复留 null。
     * 配合唯一索引 (receiver_id, dedup_key) 实现并发安全的去重；
     * MySQL 唯一索引允许多个 null，因此评论通知不受影响。
     */
    private String dedupKey;

    private LocalDateTime createTime;
}
