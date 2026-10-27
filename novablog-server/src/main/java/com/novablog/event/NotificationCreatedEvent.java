package com.novablog.event;

import com.novablog.vo.NotificationVO;

/**
 * 通知已落库事件
 *
 * <p>只在通知写入成功后发布，监听器负责 SSE 推送。
 * 推送失败不影响数据——通知行本身就是可补偿的存储。
 */
public class NotificationCreatedEvent {

    private final Long receiverId;

    private final NotificationVO notification;

    public NotificationCreatedEvent(Long receiverId, NotificationVO notification) {
        this.receiverId = receiverId;
        this.notification = notification;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public NotificationVO getNotification() {
        return notification;
    }
}
