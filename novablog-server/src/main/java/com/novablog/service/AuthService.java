package com.novablog.service;

import com.novablog.dto.LoginDTO;
import com.novablog.dto.RegisterDTO;

import java.util.Map;

public interface AuthService {

    Map<String, Object> login(LoginDTO loginDTO);

    Map<String, Object> register(RegisterDTO registerDTO);

    Map<String, Object> refresh(String refreshToken);

    void logout(String accessToken);

    Map<String, Object> profile();
}
