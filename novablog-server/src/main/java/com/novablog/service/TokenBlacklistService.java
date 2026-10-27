package com.novablog.service;

import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token 失效服务
 *
 * <p>承载三种粒度的失效能力：
 * <ul>
 *   <li><b>单令牌</b>：jti 级黑名单，用于登出的 AT、被轮换掉的旧 RT</li>
 *   <li><b>轮换映射</b>：宽限窗口内「旧 RT jti → 新 RT」的映射，用于收敛并发刷新</li>
 *   <li><b>会话</b>：sid 级撤销，用于登出与判定令牌泄露后作废整个会话家族</li>
 * </ul>
 *
 * <p>所有操作在 Redis 异常时降级为「保守放行」并告警，避免 Redis 抖动导致全站登录态失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisUtil redisUtil;

    /**
     * 将指定 jti 加入黑名单
     *
     * @param jti       Token 唯一标识
     * @param ttlMillis 存活时长，取该令牌的剩余有效期，令牌自然过期后黑名单条目随之清理
     */
    public void blacklist(String jti, long ttlMillis) {
        if (jti == null || ttlMillis <= 0) {
            return;
        }
        try {
            redisUtil.setWithExpire(RedisKeyConstant.tokenBlacklist(jti), "1",
                ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("添加 Token 黑名单失败, jti={}", jti, e);
        }
    }

    /**
     * 判断指定 jti 是否已在黑名单中
     *
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

    /**
     * 记录「旧 RT jti → 新 RT」的轮换映射，仅在宽限窗口内有效。
     *
     * <p>用 setIfAbsent 保证并发下同一个旧 RT 只收敛到一张新 RT：多个标签页同时刷新时，
     * 先写入者的令牌生效，后到者读取该映射并复用之，而不是各自签发一张。
     *
     * @param graceMillis 宽限窗口时长
     * @return true 表示本次写入成功，调用方自己签发的新 RT 生效；
     *         false 表示已有并发请求先写入，调用方应改用 {@link #getRotated}
     */
    public boolean saveRotation(String oldJti, String newRefreshToken, long graceMillis) {
        if (oldJti == null || newRefreshToken == null || graceMillis <= 0) {
            return false;
        }
        try {
            ValueOperations<String, String> ops = redisUtil.getRedisTemplate().opsForValue();
            Boolean written = ops.setIfAbsent(RedisKeyConstant.tokenRotation(oldJti),
                newRefreshToken, graceMillis, TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(written);
        } catch (Exception e) {
            log.warn("写入轮换映射失败, jti={}", oldJti, e);
            return false;
        }
    }

    /**
     * 读取轮换后的新 RT。
     *
     * <p>返回 null 表示「确认不在宽限窗口内」（窗口已过，或从未轮换过），调用方据此把旧 RT
     * 重现判定为泄露。正因如此，这里**不能吞异常**：Redis 抖动若被降级成 null，
     * 一次正常刷新就会被误判为泄露并撤销整个会话。异常向上抛出，按服务不可用处理，
     * 绝不触发撤销。
     *
     * <p>对比：{@link #isBlacklisted} / {@link #isSessionRevoked} 的读取失败降级为放行，
     * 不会造成破坏性动作，因此保持 fail-open。
     *
     * @return 新 RT；null 表示确认不在宽限窗口内
     */
    public String getRotated(String oldJti) {
        if (oldJti == null) {
            return null;
        }
        return redisUtil.get(RedisKeyConstant.tokenRotation(oldJti));
    }

    /**
     * 撤销整个会话，该 sid 下的全部 AT 与 RT 立即失效
     *
     * <p>TTL 必须覆盖该会话令牌家族的最长剩余有效期。RT 是轮换的，调用方手里那张
     * 可能已经是旧令牌、剩余时间很短，因此这里由调用方统一传入完整有效期，
     * 否则撤销标记会先于最新 RT 过期，会话在登出后复活。
     *
     * @param sid       会话标识
     * @param ttlMillis 撤销标记的存活时长，应覆盖该会话令牌的最长剩余有效期
     * @return true-撤销标记写入成功；false-参数非法或 Redis 写入失败
     */
    public boolean revokeSession(String sid, long ttlMillis) {
        if (sid == null || ttlMillis <= 0) {
            return false;
        }
        try {
            redisUtil.setWithExpire(RedisKeyConstant.tokenSession(sid),
                String.valueOf(System.currentTimeMillis()), ttlMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            log.warn("撤销会话失败, sid={}", sid, e);
            return false;
        }
    }

    /**
     * 判断会话是否已被撤销
     *
     * @return true-该会话的所有令牌均已失效；sid 为 null（历史 Token）时视为未撤销
     */
    public boolean isSessionRevoked(String sid) {
        if (sid == null) {
            return false;
        }
        try {
            return redisUtil.hasKey(RedisKeyConstant.tokenSession(sid));
        } catch (Exception e) {
            log.warn("查询会话撤销状态失败, sid={}", sid, e);
            return false;
        }
    }
}
