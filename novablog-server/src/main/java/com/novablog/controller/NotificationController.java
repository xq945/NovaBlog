package com.novablog.controller;

import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.service.NotificationService;
import com.novablog.service.NotificationSseService;
import com.novablog.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@Tag(name = "站内通知", description = "通知列表、未读数、已读、实时推送")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    @Operation(summary = "通知列表", description = "可按类型筛选，unreadOnly=true 只看未读")
    @GetMapping("/list")
    public Result<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Result.success(notificationService.list(page, size, type, unreadOnly));
    }

    @Operation(summary = "未读数", description = "数据库 COUNT 为正本，Redis 只是 60 秒读缓存")
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", notificationService.countUnread()));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.success();
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead() {
        return Result.success(Map.of("count", notificationService.markAllRead()));
    }

    @Operation(summary = "实时通知流",
        description = "SSE 长连接。客户端需用 fetch 携带 Authorization 头，EventSource 无法自定义请求头")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return notificationSseService.subscribe(userId, UserContext.getSid());
    }
}
