package com.novablog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import com.novablog.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,20}$"
    );

    @Override
    public void updateProfile(String username, String nickname, String email, String avatar, String password) {
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
        if (avatar != null && !avatar.isEmpty() && avatar.length() > 500) {
            throw new BusinessException("头像链接过长");
        }

        Long userId = UserContext.getUserId();

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(401, "用户已被禁用");
        }

        String usernameToUpdate = user.getUsername();
        if (username != null && !username.isEmpty() && !username.equals(user.getUsername())) {
            if (username.length() < 3 || username.length() > 20) {
                throw new BusinessException("用户名长度必须为3-20位");
            }
            if (!Pattern.matches("^[a-zA-Z0-9_]+$", username)) {
                throw new BusinessException("用户名只能包含字母、数字、下划线");
            }
            User existingUser = userMapper.findByUsername(username);
            if (existingUser != null && !existingUser.getId().equals(userId)) {
                throw new BusinessException("用户名已存在");
            }
            usernameToUpdate = username;
        }

        String passwordToUpdate = user.getPassword();
        if (password != null && !password.isEmpty()) {
            if (!PASSWORD_PATTERN.matcher(password).matches()) {
                throw new BusinessException("密码必须为8-20位，且同时包含大写字母、小写字母、数字、特殊符号");
            }
            passwordToUpdate = passwordEncoder.encode(password);
        }

        String emailToUpdate = (email == null || email.isEmpty()) ? null : email;
        String avatarToUpdate = (avatar == null || avatar.isEmpty()) ? null : avatar;
        userMapper.updateProfile(userId, usernameToUpdate, nickname, emailToUpdate, avatarToUpdate, passwordToUpdate);
    }
}
