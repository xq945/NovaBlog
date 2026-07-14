package com.novablog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(403);
        response.getWriter().write(
            objectMapper.writeValueAsString(Result.error(403, "无权访问")));
    }
}
