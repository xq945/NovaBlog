package com.novablog.service;

import com.novablog.common.PageResult;
import com.novablog.vo.NotificationVO;

/**
 * 站内通知服务
 *
 * <p>通知是业务数据：在调用方的事务里落库，提交后再由监听器做 SSE 推送。
 * 本服务不自己开事务，跟着调用方走，保证「业务成功则通知存在、业务回滚则通知不存在」。
 */
public interface NotificationService {

    /**
     * 点赞通知。接收人是文章作者，自己赞自己不产生通知
     */
    void notifyLike(Long receiverId, Long senderId, Long articleId, String articleTitle);

    /**
     * 评论或回复通知
     *
     * @param reply true-回复评论，false-评论文章
     */
    void notifyComment(Long receiverId, Long senderId, Long articleId,
                       String articleTitle, boolean reply);

    /**
     * 关注通知
     */
    void notifyFollow(Long receiverId, Long senderId);

    PageResult<NotificationVO> list(int page, int size, String type, boolean unreadOnly);

    long countUnread();

    boolean markRead(Long id);

    int markAllRead();
}
