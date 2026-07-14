package com.novablog.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        List<String> roles = resolveRoles(user);
        return new SecurityUserDetails(user, roles);
    }

    public UserDetails loadUserByUserId(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        List<String> roles = resolveRoles(user);
        return new SecurityUserDetails(user, roles);
    }

    /**
     * 解析用户角色。第一阶段临时方案：直接读取 user.role 字段。
     * TODO [第二阶段] RBAC 五表建成后，改为通过 RoleMapper 查询角色列表。
     */
    private List<String> resolveRoles(User user) {
        return List.of("ROLE_" + user.getRole());
    }
}
