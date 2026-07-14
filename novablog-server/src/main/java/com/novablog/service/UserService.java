package com.novablog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novablog.entity.User;
import com.novablog.vo.UserProfileVO;

public interface UserService extends IService<User> {

    void updateProfile(String username, String nickname, String email, String avatar, String password);

    /**
     * 获取用户公开主页信息
     */
    UserProfileVO getUserProfile(Long userId);
}
