package com.novablog.controller;

import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.LoginDTO;
import com.novablog.dto.RegisterDTO;
import com.novablog.dto.UpdateProfileDTO;
import com.novablog.dto.UserStatusDTO;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import com.novablog.service.ArticleService;
import com.novablog.service.AuthService;
import com.novablog.service.UserService;
import com.novablog.service.assembler.UserVOAssembler;
import com.novablog.vo.AdminUserVO;
import com.novablog.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户控制器
 * 处理用户注册、登录、个人信息等请求
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final UserMapper userMapper;
    private final ArticleService articleService;

    /**
     * 用户注册，委托给 AuthService
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterDTO registerDTO) {
        return Result.success(authService.register(registerDTO));
    }

    /**
     * 用户登录，委托给 AuthService
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
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
        userInfo.put("createTime", user.getCreateTime());

        return Result.success(userInfo);
    }

    /**
     * 修改个人信息
     *
     * @param updateProfileDTO 修改参数
     * @return 成功结果
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileDTO updateProfileDTO) {
        userService.updateProfile(
                updateProfileDTO.getUsername(),
                updateProfileDTO.getNickname(),
                updateProfileDTO.getEmail(),
                updateProfileDTO.getAvatar(),
                updateProfileDTO.getPassword()
        );
        return Result.success();
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

    /**
     * 管理员查询用户列表
     *
     * @param page    页码
     * @param size    每页数量
     * @param keyword 关键词，模糊匹配用户名/昵称/邮箱
     * @return 用户分页结果
     */
    @GetMapping("/admin/list")
    @RequireAdmin
    public Result<PageResult<AdminUserVO>> adminList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 10;
        if (size > 50) size = 50;
        int offset = (page - 1) * size;

        String searchKeyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        List<User> users = userMapper.findList(offset, size, searchKeyword);
        Long total = userMapper.countAll(searchKeyword);

        return Result.success(new PageResult<>(total, UserVOAssembler.toAdminUserVOList(users)));
    }

    /**
     * 管理员批量删除用户
     * 会自动过滤管理员、当前登录用户以及存在文章/评论关联数据的用户
     *
     * @param userIds 待删除用户ID列表
     * @return 删除结果统计
     */
    @DeleteMapping("/admin/batch")
    @RequireAdmin
    public Result<Map<String, Object>> batchDelete(@RequestBody List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("请选择要删除的用户");
        }

        // 去重并过滤空值
        List<Long> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (distinctIds.isEmpty()) {
            throw new BusinessException("请选择要删除的用户");
        }

        Long currentUserId = UserContext.getUserId();

        // 查询用户详情，过滤管理员和当前登录用户
        List<User> users = userMapper.findByIds(distinctIds);
        List<Long> deletableIds = users.stream()
                .filter(user -> !"ADMIN".equals(user.getRole()))
                .filter(user -> !Objects.equals(user.getId(), currentUserId))
                .map(User::getId)
                .collect(Collectors.toList());

        if (deletableIds.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("deletedCount", 0);
            result.put("skippedCount", distinctIds.size());
            result.put("skippedReason", "所选用户包含管理员或当前登录用户，已跳过");
            return Result.success(result);
        }

        // 查询有文章或评论的用户，进一步过滤
        Set<Long> blockedIds = new HashSet<>();
        blockedIds.addAll(userMapper.findUserIdsWithArticles(deletableIds));
        blockedIds.addAll(userMapper.findUserIdsWithComments(deletableIds));

        List<Long> finalIds = deletableIds.stream()
                .filter(id -> !blockedIds.contains(id))
                .collect(Collectors.toList());

        int deletedCount = 0;
        if (!finalIds.isEmpty()) {
            deletedCount = userMapper.deleteByIds(finalIds);
        }

        int skippedCount = distinctIds.size() - deletedCount;
        String reason;
        if (skippedCount == 0) {
            reason = "";
        } else if (!blockedIds.isEmpty() && deletableIds.size() - finalIds.size() > 0) {
            reason = "已跳过管理员/当前用户及存在文章、评论关联数据的用户";
        } else if (!blockedIds.isEmpty()) {
            reason = "已跳过存在文章或评论关联数据的用户";
        } else {
            reason = "已跳过管理员或当前登录用户";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("skippedCount", skippedCount);
        result.put("skippedReason", reason);
        return Result.success(result);
    }

    /**
     * 管理员修改用户状态
     *
     * @param dto 状态参数
     * @return 成功结果
     */
    @PutMapping("/admin/status")
    @RequireAdmin
    public Result<Void> updateStatus(@RequestBody UserStatusDTO dto) {
        if (dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1)) {
            throw new BusinessException("状态值只能为 0 或 1");
        }
        if (Objects.equals(dto.getUserId(), UserContext.getUserId())) {
            throw new BusinessException("不能禁用当前登录用户");
        }
        User user = userMapper.findById(dto.getUserId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.updateStatus(dto.getUserId(), dto.getStatus());
        return Result.success();
    }
}
