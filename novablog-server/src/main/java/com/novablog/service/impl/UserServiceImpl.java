package com.novablog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.entity.Article;
import com.novablog.entity.User;
import com.novablog.mapper.ArticleMapper;
import com.novablog.mapper.UserFollowMapper;
import com.novablog.mapper.UserMapper;
import com.novablog.service.SessionService;
import com.novablog.service.UserService;
import com.novablog.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final UserFollowMapper userFollowMapper;

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
        boolean passwordChanged = false;
        if (password != null && !password.isEmpty()) {
            if (!PASSWORD_PATTERN.matcher(password).matches()) {
                throw new BusinessException("密码必须为8-20位，且同时包含大写字母、小写字母、数字、特殊符号");
            }
            // 重新编码会让哈希变化，但密码本身没变，不该据此踢掉其他设备
            if (!passwordEncoder.matches(password, user.getPassword())) {
                passwordToUpdate = passwordEncoder.encode(password);
                passwordChanged = true;
            }
        }

        String emailToUpdate = (email == null || email.isEmpty()) ? null : email;
        String avatarToUpdate = (avatar == null || avatar.isEmpty()) ? null : avatar;
        userMapper.updateProfile(userId, usernameToUpdate, nickname, emailToUpdate, avatarToUpdate, passwordToUpdate);

        if (passwordChanged) {
            // 改密后其他设备上的会话应当失效，否则旧密码泄露时改了也没用。
            // 撤销失败只记日志，不能让密码修改本身回滚
            try {
                String currentSid = UserContext.getSid();
                if (currentSid != null) {
                    sessionService.kickOthers(userId, currentSid);
                } else {
                    sessionService.revokeAll(userId);
                }
            } catch (Exception e) {
                log.warn("修改密码后撤销其他会话失败, userId={}", userId, e);
            }
        }
    }

    @Override
    public UserProfileVO getUserProfile(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(404, "用户不存在");
        }

        Long articleCount = articleMapper.selectCount(
            new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, userId)
                .eq(Article::getStatus, 1));

        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setArticleCount(articleCount);
        vo.setFollowerCount(user.getFollowerCount() == null ? 0L : user.getFollowerCount());
        vo.setFollowingCount(user.getFollowingCount() == null ? 0L : user.getFollowingCount());

        // 关注状态顺带在这里返回，前端打开主页就不用再发一次请求
        Long currentUserId = UserContext.getUserId();
        boolean followed = currentUserId != null && !currentUserId.equals(userId)
            && userFollowMapper.exists(currentUserId, userId) > 0;
        vo.setFollowed(followed);

        vo.setJoinTime(user.getCreateTime());
        return vo;
    }
}
