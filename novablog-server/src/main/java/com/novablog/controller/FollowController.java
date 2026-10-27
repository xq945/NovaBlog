package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.service.FollowService;
import com.novablog.vo.FollowStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户关注", description = "关注与取消关注")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "关注用户", description = "重复关注幂等，不能关注自己")
    @PostMapping("/{id}/follow")
    public Result<FollowStatusVO> follow(@PathVariable Long id) {
        return Result.success(followService.follow(id));
    }

    @Operation(summary = "取消关注", description = "未关注时调用同样成功")
    @DeleteMapping("/{id}/follow")
    public Result<FollowStatusVO> unfollow(@PathVariable Long id) {
        return Result.success(followService.unfollow(id));
    }
}
