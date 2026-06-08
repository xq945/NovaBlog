package com.novablog.controller;

import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.LoginDTO;
import com.novablog.dto.RegisterDTO;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import com.novablog.service.ArticleService;
import com.novablog.service.UserService;
import com.novablog.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 * 处理用户注册、登录、个人信息等请求
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final ArticleService articleService;

    /**
     * 用户注册
     *
     * @param registerDTO 注册参数
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success();
    }

    /**
     * 用户登录
     *
     * @param loginDTO 登录参数
     * @return 包含 token、refreshToken、expiresIn、userInfo
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO loginDTO) {
        Map<String, Object> loginResult = userService.login(loginDTO);
        return Result.success(loginResult);
    }

    /**
     * 获取当前登录用户信息
     * 需要携带有效的 Access Token
     *
     * @return 用户信息（不含密码）
     */
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        User user = userMapper.findById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("email", user.getEmail());
        userInfo.put("role", user.getRole());

        return Result.success(userInfo);
    }

    /**
     * 查询当前用户的文章列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 文章分页结果
     */
    @GetMapping("/article/list")
    public Result<PageResult<ArticleVO>> myArticles(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        PageResult<ArticleVO> result = articleService.findMyArticles(page, size);
        return Result.success(result);
    }
}
