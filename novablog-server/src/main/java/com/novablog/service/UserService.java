package com.novablog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novablog.entity.User;

public interface UserService extends IService<User> {

    void updateProfile(String username, String nickname, String email, String avatar, String password);
}
