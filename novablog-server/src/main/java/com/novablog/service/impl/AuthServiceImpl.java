package com.novablog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novablog.common.UserContext;
import com.novablog.common.annotation.AutoFillTime;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.enums.OperationType;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.request.LoginDTO;
import com.novablog.dto.request.RegisterDTO;
import com.novablog.entity.LoginLog;
import com.novablog.entity.User;
import com.novablog.entity.UserRole;
import com.novablog.mapper.LoginLogMapper;
import com.novablog.mapper.RoleMapper;
import com.novablog.mapper.UserMapper;
import com.novablog.mapper.UserRoleMapper;
import com.novablog.security.JwtTokenProvider;
import com.novablog.service.AuthService;
import com.novablog.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;
    private final RoleMapper roleMapper;
    private final LoginLogMapper loginLogMapper;
    private final UserRoleMapper userRoleMapper;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,20}$"
    );

    @Override
    public Map<String, Object> login(LoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            recordLoginLog(username, null, false, "用户名和密码不能为空");
            throw new BusinessException("用户名和密码不能为空");
        }

        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            recordLoginLog(username, null, false, "用户名或密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() == null || user.getStatus() == 0) {
            recordLoginLog(username, user.getId(), false, "用户已被禁用");
            throw new BusinessException("用户已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordLoginLog(username, user.getId(), false, "用户名或密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        Map<String, Object> result = buildLoginResult(user);
        recordLoginLog(username, user.getId(), true, null);
        return result;
    }

    @Override
    @AutoFillTime(OperationType.INSERT)
    public Map<String, Object> register(RegisterDTO registerDTO) {
        String username = registerDTO.getUsername();
        String password = registerDTO.getPassword();
        String nickname = registerDTO.getNickname();

        if (username == null || username.length() < 3 || username.length() > 20) {
            throw new BusinessException("用户名长度必须为3-20位");
        }
        if (!Pattern.matches("^[a-zA-Z0-9_]+$", username)) {
            throw new BusinessException("用户名只能包含字母、数字、下划线");
        }
        if (nickname == null || nickname.isEmpty() || nickname.length() > 20) {
            throw new BusinessException("昵称长度必须为1-20位");
        }
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BusinessException("密码必须为8-20位，且同时包含大写字母、小写字母、数字、特殊符号");
        }

        User existingUser = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setAvatar("https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg");
        user.setRole(RoleConstant.USER);
        user.setStatus(1);

        userMapper.insert(user);

        // 为新用户分配 USER 角色
        roleMapper.selectList(
            new LambdaQueryWrapper<com.novablog.entity.Role>()
                .eq(com.novablog.entity.Role::getName, RoleConstant.USER))
            .forEach(role -> {
                UserRole ur = new UserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(role.getId());
                userRoleMapper.insert(ur);
            });

        return buildLoginResult(user);
    }

    @Override
    public Map<String, Object> refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(401, "Token 无效或已过期");
        }
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(401, "Token 类型错误");
        }

        String jti = jwtTokenProvider.getJti(refreshToken);
        if (redisUtil.hasKey("token:blacklist:" + jti)) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        List<String> authorities = buildAuthorities(userId);
        String newAccessToken = jwtTokenProvider.createAccessToken(userId, user.getUsername(), authorities);

        Map<String, Object> result = new HashMap<>();
        result.put("token", newAccessToken);
        result.put("expiresIn", 7200);

        // 滑动过期：RefreshToken 剩余有效期 ≤ 1 天时，同时换发
        long remaining = jwtTokenProvider.getRemainingMillis(refreshToken);
        if (remaining <= 86400000) {
            String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
            result.put("refreshToken", newRefreshToken);
            // 旧 RT 加入黑名单
            redisUtil.setWithExpire("token:blacklist:" + jti, "1", Math.max(remaining, 1), TimeUnit.MILLISECONDS);
        }

        return result;
    }

    @Override
    public void logout(String accessToken) {
        String jti = jwtTokenProvider.getJti(accessToken);
        long remaining = jwtTokenProvider.getRemainingMillis(accessToken);
        if (remaining > 0) {
            redisUtil.setWithExpire("token:blacklist:" + jti, "1", remaining, TimeUnit.MILLISECONDS);
        }

        Long userId = UserContext.getUserId();
        if (userId != null) {
            redisUtil.del("login:user:" + userId);
        }
    }

    @Override
    public Map<String, Object> profile() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("email", user.getEmail());
        result.put("role", user.getRole());
        result.put("roles", roleMapper.findRoleNamesByUserId(userId));
        return result;
    }

    private Map<String, Object> buildLoginResult(User user) {
        List<String> authorities = buildAuthorities(user.getId());
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), authorities);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("email", user.getEmail());
        userInfo.put("role", user.getRole());
        userInfo.put("roles", roleMapper.findRoleNamesByUserId(user.getId()));

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", 7200);
        result.put("userInfo", userInfo);
        return result;
    }

    /**
     * 从 RBAC 表加载用户的角色和权限列表，用于构建 JWT 中的 authorities。
     */
    private List<String> buildAuthorities(Long userId) {
        List<String> roleNames = roleMapper.findRoleNamesByUserId(userId);
        List<String> permissions = roleMapper.findPermissionNamesByUserId(userId);
        List<String> authorities = new ArrayList<>();
        roleNames.forEach(role -> authorities.add("ROLE_" + role));
        authorities.addAll(permissions);
        return authorities;
    }

    /**
     * 记录登录日志
     */
    private void recordLoginLog(String username, Long userId, boolean success, String reason) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setUserId(userId);
        log.setSuccess(success ? 1 : 0);
        log.setReason(reason != null ? reason : "");

        // 从请求上下文获取 IP
        try {
            HttpServletRequest request = ((HttpServletRequest)
                org.springframework.web.context.request.RequestContextHolder
                    .currentRequestAttributes()
                    .resolveReference(org.springframework.web.context.request.RequestAttributes.REFERENCE_REQUEST));
            if (request != null) {
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                log.setIp(ip);
                String ua = request.getHeader("User-Agent");
                log.setUserAgent(ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua);
            }
        } catch (Exception ignored) {
            // 非 HTTP 上下文（如测试）忽略
        }

        loginLogMapper.insert(log);
    }
}
