package com.novablog.listener;

import com.novablog.event.NotificationCreatedEvent;
import com.novablog.service.NotificationSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 通知落库后的 SSE 推送
 *
 * <p>有事务时在提交后执行：事务回滚了就不会推送，避免用户看到一条并不存在的通知。
 * 点赞路径没有事务，用 fallbackExecution 让它立即推送。
 *
 * <p>推送只是尽力而为：失败不影响通知本身，用户下次连上或打开列表仍能看到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final NotificationSseService sseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        try {
            sseService.push(event.getReceiverId(), event.getNotification());
        } catch (Exception e) {
            log.warn("推送通知失败, receiver={}", event.getReceiverId(), e);
        }
    }
}
