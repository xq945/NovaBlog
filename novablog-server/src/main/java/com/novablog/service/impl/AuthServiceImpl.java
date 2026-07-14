package com.novablog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novablog.common.UserContext;
import com.novablog.common.annotation.AutoFillTime;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.enums.OperationType;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.LoginDTO;
import com.novablog.dto.RegisterDTO;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import com.novablog.security.JwtTokenProvider;
import com.novablog.service.AuthService;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,20}$"
    );

    @Override
    public Map<String, Object> login(LoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new BusinessException("用户名和密码不能为空");
        }

        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException("用户已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        return buildLoginResult(user);
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

        List<String> roles = List.of("ROLE_" + user.getRole());
        String newAccessToken = jwtTokenProvider.createAccessToken(userId, user.getUsername(), roles);

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
        return result;
    }

    private Map<String, Object> buildLoginResult(User user) {
        List<String> roles = List.of("ROLE_" + user.getRole());
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("email", user.getEmail());
        userInfo.put("role", user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", 7200);
        result.put("userInfo", userInfo);
        return result;
    }
}
