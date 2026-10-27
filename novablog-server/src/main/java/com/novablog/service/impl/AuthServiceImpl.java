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
import com.novablog.service.SessionService;
import com.novablog.service.TokenBlacklistService;
import com.novablog.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;
    private final SessionService sessionService;
    private final RoleMapper roleMapper;
    private final LoginLogMapper loginLogMapper;
    private final UserRoleMapper userRoleMapper;

    /**
     * RefreshToken 轮换后的宽限窗口。窗口内旧 RT 再次出现视为并发刷新（多标签页共享同一 AT，
     * 会几乎同时刷新），复用已签发的新 RT；窗口外则判定为令牌泄露。
     */
    @Value("${jwt.rotation-grace}")
    private long rotationGrace;

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

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String jti = jwtTokenProvider.getJti(refreshToken);
        // 存量 RT 不含 sid：补发一个，保证这次轮换出的令牌家族仍可被会话级撤销。
        // storedSid 用于查询已有会话，sid 用于签发新令牌，两者在存量令牌上不同
        String storedSid = jwtTokenProvider.getSid(refreshToken);
        String sid = storedSid != null ? storedSid : UUID.randomUUID().toString();

        // 会话已被整体撤销（用户登出，或此前判定过令牌泄露）
        if (tokenBlacklistService.isSessionRevoked(storedSid)) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        List<String> authorities = buildAuthorities(userId);
        HttpServletRequest request = RequestUtils.currentRequest();
        String ip = RequestUtils.getClientIp(request);
        String ua = RequestUtils.getUserAgent(request);

        // 该 RT 已被消费过（轮换走了），它再次出现只有两种可能
        if (tokenBlacklistService.isBlacklisted(jti)) {
            String rotated = tokenBlacklistService.getRotated(jti);
            if (rotated != null) {
                // 一、仍在宽限窗口内：多个标签页共享同一个 AT，会几乎同时刷新，
                //     直接复用已签发的那张 RT，让并发方收敛到同一个令牌
                sessionService.onRefresh(userId, sid, ip, ua);
                return buildRefreshResult(user, authorities, sid, rotated);
            }
            // 二、已超出宽限窗口：合法客户端手里早已是新令牌，旧令牌重现即为泄露，
            //     作废整个会话家族，逼攻击者与真实用户一起重新登录
            log.warn("检测到 RefreshToken 重放，撤销整个会话: userId={}, sid={}, jti={}", userId, sid, jti);
            tokenBlacklistService.revokeSession(sid, jwtTokenProvider.getRefreshExpirationMillis());
            throw new BusinessException(401, "登录状态异常，请重新登录");
        }

        // 正常轮换：每次刷新都签发新 RT，旧 RT 立即作废，缩小泄露后的可用窗口
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId, sid);
        if (!tokenBlacklistService.saveRotation(jti, newRefreshToken, rotationGrace)) {
            // 并发竞争落败说明另一个请求已先写入映射，复用它那张，
            // 保证同一个旧 RT 只对应一张新 RT
            String winner = tokenBlacklistService.getRotated(jti);
            if (winner != null) {
                newRefreshToken = winner;
            }
        }
        tokenBlacklistService.blacklist(jti, jwtTokenProvider.getRemainingMillis(refreshToken));
        sessionService.onRefresh(userId, sid, ip, ua);

        return buildRefreshResult(user, authorities, sid, newRefreshToken);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        // 优先用 AT 的 sid；AT 已过期或被篡改时回退到 RT
        String sid = resolveSid(accessToken);
        Long userId = resolveUserId(accessToken);
        if (sid == null) {
            sid = resolveSid(refreshToken);
            userId = resolveUserId(refreshToken);
        }

        if (sid != null) {
            // 撤销标记一律覆盖完整的 RT 有效期。RT 是轮换的，前端持有的可能是
            // 轮换前的旧令牌，按它的剩余时间写标记会先于最新 RT 过期——
            // 标记消失后同一 sid 下的新 RT 仍然可用，会话就在登出后复活了
            tokenBlacklistService.revokeSession(sid, jwtTokenProvider.getRefreshExpirationMillis());
            sessionService.removeSession(userId, sid);
            return;
        }

        // 兜底：不含 sid 的存量令牌无法按会话撤销，退化为单令牌拉黑。
        // AT 与 RT 都要拉黑——只拉黑 AT 的话，AT 过期后 RT 仍能继续刷新，
        // 待存量令牌自然过期后此分支即失效
        long accessRemaining = resolveRemaining(accessToken);
        if (accessRemaining > 0) {
            tokenBlacklistService.blacklist(jwtTokenProvider.getJti(accessToken), accessRemaining);
        }
        long refreshRemaining = resolveRemaining(refreshToken);
        if (refreshRemaining > 0) {
            tokenBlacklistService.blacklist(jwtTokenProvider.getJti(refreshToken), refreshRemaining);
        }
    }

    /**
     * 安全解析会话标识。Token 为空、无效或为不含 sid 的存量格式时返回 null
     */
    private String resolveSid(String token) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return null;
        }
        return jwtTokenProvider.getSid(token);
    }

    /**
     * 安全解析 Token 所属用户，无法解析时返回 null
     */
    private Long resolveUserId(String token) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return null;
        }
        return jwtTokenProvider.getUserId(token);
    }

    /**
     * 安全解析 Token 剩余有效期，无法解析时返回 -1
     */
    private long resolveRemaining(String token) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return -1;
        }
        return jwtTokenProvider.getRemainingMillis(token);
    }

    @Override
    public Map<String, Object> profile() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return buildUserInfo(user);
    }

    private Map<String, Object> buildLoginResult(User user) {
        // 一次登录派生一个会话标识，AT 与 RT 共享它，登出或判定泄露时按 sid 整体撤销
        String sid = UUID.randomUUID().toString();
        List<String> authorities = buildAuthorities(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", jwtTokenProvider.createAccessToken(
            user.getId(), user.getUsername(), authorities, sid));
        result.put("refreshToken", jwtTokenProvider.createRefreshToken(user.getId(), sid));
        result.put("expiresIn", jwtTokenProvider.getAccessExpirationSeconds());
        result.put("userInfo", buildUserInfo(user));

        // 登记登录设备。令牌已经签发，这里失败只告警，不能让用户登录不了
        HttpServletRequest request = RequestUtils.currentRequest();
        sessionService.registerSession(user.getId(), sid,
            RequestUtils.getClientIp(request), RequestUtils.getUserAgent(request));
        return result;
    }

    /**
     * 组装刷新响应。字段与登录响应保持一致，否则前端刷新后会拿到空的 userInfo
     */
    private Map<String, Object> buildRefreshResult(User user, List<String> authorities,
                                                   String sid, String refreshToken) {
        Map<String, Object> result = new HashMap<>();
        result.put("token", jwtTokenProvider.createAccessToken(
            user.getId(), user.getUsername(), authorities, sid));
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", jwtTokenProvider.getAccessExpirationSeconds());
        result.put("userInfo", buildUserInfo(user));
        return result;
    }

    /**
     * 组装返回给前端的用户信息
     */
    private Map<String, Object> buildUserInfo(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("email", user.getEmail());
        userInfo.put("role", user.getRole());
        userInfo.put("roles", roleMapper.findRoleNamesByUserId(user.getId()));
        return userInfo;
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

        // 从请求上下文获取 IP 与 UA，统一走 RequestUtils，
        // 避免登录日志里的 IP 与会话记录里的 IP 取法不一致
        HttpServletRequest request = RequestUtils.currentRequest();
        if (request != null) {
            log.setIp(RequestUtils.getClientIp(request));
            log.setUserAgent(RequestUtils.getUserAgent(request));
        }

        loginLogMapper.insert(log);
    }
}
