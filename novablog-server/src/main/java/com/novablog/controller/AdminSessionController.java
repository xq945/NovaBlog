package com.novablog.controller;

import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.common.annotation.LogOperation;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.service.SessionService;
import com.novablog.vo.OnlineUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 在线用户与会话管理（管理员）
 */
@Tag(name = "在线用户", description = "查看在线用户、强制下线")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@RequireAdmin
public class AdminSessionController {

    private final SessionService sessionService;

    @Operation(summary = "在线用户列表",
        description = "在线口径：最近 5 分钟内带有效 AccessToken 发过请求；活跃度有节流，最后活跃时间误差不超过 1 分钟")
    @GetMapping("/online-users")
    public Result<PageResult<OnlineUserVO>> onlineUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return Result.success(sessionService.onlineUsers(safePage, safeSize));
    }

    @Operation(summary = "强制用户下线",
        description = "撤销该用户的全部会话。包含当前不在线、但 RefreshToken 仍有效的会话，因此「在线」只是列表口径，不影响这里全量撤销")
    @DeleteMapping("/users/{userId}/sessions")
    @LogOperation(target = "USER", operation = "UPDATE", detail = "强制用户下线：{0}")
    public Result<Map<String, Object>> revokeUserSessions(@PathVariable Long userId) {
        int count = sessionService.revokeAll(userId);
        return Result.success(Map.of("count", count));
    }
}
