package com.novablog.service;

import com.novablog.dto.LoginDTO;
import com.novablog.dto.RegisterDTO;

import java.util.Map;

/**
 * 用户业务层接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param registerDTO 注册参数
     */
    void register(RegisterDTO registerDTO);

    /**
     * 用户登录
     *
     * @param loginDTO 登录参数
     * @return 包含 token、refreshToken、expiresIn、userInfo 的 Map
     */
    Map<String, Object> login(LoginDTO loginDTO);

    /**
     * 修改当前登录用户的个人信息
     *
     * @param username 用户名
     * @param nickname 昵称
     * @param email    邮箱
     * @param avatar   头像URL
     * @param password 密码
     */
    void updateProfile(String username, String nickname, String email, String avatar, String password);
}
