package com.novablog.service;

import com.novablog.dto.request.LoginDTO;
import com.novablog.dto.request.RegisterDTO;

import java.util.Map;

public interface AuthService {

    Map<String, Object> login(LoginDTO loginDTO);

    Map<String, Object> register(RegisterDTO registerDTO);

    Map<String, Object> refresh(String refreshToken);

    /**
     * 登出：按会话标识撤销整个会话，该会话的 AccessToken 与 RefreshToken 一并失效
     *
     * @param accessToken  当前 AccessToken，优先由它解析会话标识
     * @param refreshToken 当前 RefreshToken，AccessToken 不可用时回退使用，可为 null
     */
    void logout(String accessToken, String refreshToken);

    Map<String, Object> profile();
}
