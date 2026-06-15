package com.novablog.service.impl;

import com.novablog.common.UserContext;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.LoginDTO;
import com.novablog.dto.RegisterDTO;
import com.novablog.dto.UserDTO;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import com.novablog.service.UserService;
import com.novablog.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用户业务层实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * 密码复杂度正则：8-20位，必须同时包含大写字母、小写字母、数字、特殊符号
     */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,20}$"
    );

    /**
     * BCrypt 密码加密器
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void register(RegisterDTO registerDTO) {
        String username = registerDTO.getUsername();
        String password = registerDTO.getPassword();
        String nickname = registerDTO.getNickname();

        // 1. 参数校验
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

        // 2. 检查用户名是否已存在
        User existingUser = userMapper.findByUsername(username);
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }

        // 3. 加密密码并创建用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setAvatar("https://xq945.oss-cn-beijing.aliyuncs.com/NovaBlog/first-avatar.jpg");  // 默认头像
        user.setRole(RoleConstant.USER);   // 注册时固定为普通用户，禁止注册为管理员
        user.setStatus(1);      // 默认启用状态

        userMapper.insert(user);
    }

    @Override
    public Map<String, Object> login(LoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();

        // 1. 参数校验
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new BusinessException("用户名和密码不能为空");
        }

        // 2. 查询用户
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 3. 检查用户状态
        if (user.getStatus() == 0) {
            throw new BusinessException("用户已被禁用");
        }

        // 4. 密码比对
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 5. 生成 Token
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setUsername(user.getUsername());
        userDTO.setRole(user.getRole());

        String accessToken = jwtUtil.generateAccessToken(userDTO);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 6. 组装用户信息
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("email", user.getEmail());
        userInfo.put("role", user.getRole());

        // 7. 返回完整登录结果
        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", jwtUtil.getExpiration() / 1000); // 转换为秒
        result.put("userInfo", userInfo);

        return result;
    }

    @Override
    public void updateProfile(String nickname, String email, String avatar) {
        // 1. 参数校验
        if (nickname == null || nickname.isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }
        if (nickname.length() > 20) {
            throw new BusinessException("昵称长度必须为1-20位");
        }
        if (email != null && !email.isEmpty()) {
            if (email.length() > 100) {
                throw new BusinessException("邮箱长度不能超过100位");
            }
            if (!Pattern.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", email)) {
                throw new BusinessException("邮箱格式不正确");
            }
        }
        // avatar 为URL格式，可选，长度不超过500
        if (avatar != null && !avatar.isEmpty() && avatar.length() > 500) {
            throw new BusinessException("头像链接过长");
        }

        // 2. 获取当前用户ID
        Long userId = UserContext.getUserId();

        // 3. 校验用户存在且未被禁用
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(401, "用户已被禁用");
        }

        // 4. 执行更新（email/avatar 为空字符串时转为 null，表示清空）
        String emailToUpdate = (email == null || email.isEmpty()) ? null : email;
        String avatarToUpdate = (avatar == null || avatar.isEmpty()) ? null : avatar;
        userMapper.updateProfile(userId, nickname, emailToUpdate, avatarToUpdate);
    }
}
