package com.novablog.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novablog.entity.User;
import com.novablog.mapper.RoleMapper;
import com.novablog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return buildUserDetails(user);
    }

    public UserDetails loadUserByUserId(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return buildUserDetails(user);
    }

    /**
     * 从 RBAC 表查询角色和权限，构建 UserDetails。
     * 角色名称转 ROLE_ 前缀供 Spring Security 使用，权限标识直接添加。
     */
    private UserDetails buildUserDetails(User user) {
        List<String> roleNames = roleMapper.findRoleNamesByUserId(user.getId());
        List<String> permissions = roleMapper.findPermissionNamesByUserId(user.getId());

        List<String> authorities = new ArrayList<>();
        roleNames.forEach(role -> authorities.add("ROLE_" + role));
        authorities.addAll(permissions);

        return new SecurityUserDetails(user, authorities);
    }
}
