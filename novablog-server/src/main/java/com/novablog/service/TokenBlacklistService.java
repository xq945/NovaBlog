package com.novablog.service;

import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务
 * 用于管理已失效的 Refresh Token，支持用户登出、修改密码后强制重新登录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisUtil redisUtil;

    /**
     * 将指定 jti 加入黑名单
     *
     * @param jti        Token 唯一标识
     * @param expiration Token 过期时间
     */
    public void addToBlacklist(String jti, Date expiration) {
        if (jti == null || expiration == null) {
            return;
        }
        long timeout = expiration.getTime() - System.currentTimeMillis();
        if (timeout <= 0) {
            return;
        }
        try {
            redisUtil.setWithExpire(RedisKeyConstant.tokenBlacklist(jti), "1", timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("添加 Token 黑名单失败, jti={}", jti, e);
        }
    }

    /**
     * 判断指定 jti 是否在黑名单中
     *
     * @param jti Token 唯一标识
     * @return true-已失效
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return redisUtil.hasKey(RedisKeyConstant.tokenBlacklist(jti));
        } catch (Exception e) {
            log.warn("查询 Token 黑名单失败, jti={}", jti, e);
            return false;
        }
    }
}
