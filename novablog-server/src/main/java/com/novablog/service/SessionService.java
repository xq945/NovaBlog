package com.novablog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.common.PageResult;
import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.common.exception.BusinessException;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import com.novablog.security.JwtTokenProvider;
import com.novablog.util.RedisUtil;
import com.novablog.vo.OnlineUserVO;
import com.novablog.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 会话管理服务
 *
 * <p>在「令牌生命周期」之外维护一份「活跃会话正本」，让 sid 从 JWT 里的一个标识
 * 变成可枚举、可撤销的记录，从而支持登录设备列表、单设备下线、单点登录与在线人数统计。
 *
 * <p>数据结构（全部 Redis）：
 * <ul>
 *   <li>{@code blog:user:sessions:{userId}}：ZSet，member 为 sid、score 为登录时间，
 *       用于枚举会话并按登录先后淘汰</li>
 *   <li>{@code blog:user:session:meta:{sid}}：会话元信息，TTL 与令牌家族对齐</li>
 *   <li>{@code blog:user:session:active:{sid}}：最后活跃时间</li>
 *   <li>{@code blog:user:session:throttle:{sid}}：节流标记，避免每个请求都写活跃时间</li>
 *   <li>{@code blog:user:online:zset}：在线用户，按时间窗口判定</li>
 * </ul>
 *
 * <p><b>TTL 对齐规则</b>：只有「签发新 RT」的路径（登录、刷新）才续 meta 与索引，
 * 这样会话最长寿命恰好等于「最后一次刷新 + RT 有效期」，不会出现令牌还有效、
 * 会话列表里却找不到的隐身会话。
 *
 * <p><b>失败语义</b>：会话中心是认证链路的旁路设施，除「写撤销标记」外，
 * 所有操作失败都只降级不阻断，避免拖垮登录与业务请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisUtil redisUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 单账号允许的并发会话数，超出时踢掉最早登录的
     */
    @Value("${auth.session.max-count:5}")
    private int maxCount;

    /**
     * 活跃度更新节流间隔（秒）
     */
    @Value("${auth.session.touch-interval-seconds:60}")
    private long touchIntervalSeconds;

    /**
     * 在线判定窗口（秒），窗口内有过请求即视为在线
     */
    @Value("${auth.session.online-window-seconds:300}")
    private long onlineWindowSeconds;

    /**
     * 登录成功后登记会话
     *
     * <p>失败只告警不抛出：令牌已经签发，会话登记失败不该让用户登录不了。
     */
    public void registerSession(Long userId, String sid, String ip, String ua) {
        if (userId == null || sid == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            String indexKey = RedisKeyConstant.sessionIndex(userId);

            redisUtil.setWithExpire(RedisKeyConstant.sessionMeta(sid),
                buildMeta(ip, ua, now), refreshTtl(), TimeUnit.MILLISECONDS);
            redisUtil.setWithExpire(RedisKeyConstant.sessionActive(sid),
                String.valueOf(now), refreshTtl(), TimeUnit.MILLISECONDS);
            redisUtil.zAdd(indexKey, sid, now);
            redisUtil.expire(indexKey, refreshTtl(), TimeUnit.MILLISECONDS);
            markOnline(userId, now);

            evictOldest(userId, sid);
        } catch (Exception e) {
            log.warn("登记登录会话失败, userId={}, sid={}", userId, sid, e);
        }
    }

    /**
     * 刷新成功后延续会话寿命
     *
     * <p>RT 每次刷新都会重新获得完整有效期，若 meta 与索引不跟着续，第 8 天会出现
     * 「令牌仍有效但会话不可见」的隐身会话。存量令牌（无 sid）刷新时顺带补登记。
     */
    public void onRefresh(Long userId, String sid, String ip, String ua) {
        if (userId == null || sid == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            String indexKey = RedisKeyConstant.sessionIndex(userId);
            String metaKey = RedisKeyConstant.sessionMeta(sid);

            redisUtil.setWithExpire(RedisKeyConstant.sessionActive(sid),
                String.valueOf(now), refreshTtl(), TimeUnit.MILLISECONDS);
            markOnline(userId, now);

            if (!Boolean.TRUE.equals(redisUtil.hasKey(metaKey))) {
                redisUtil.setWithExpire(metaKey, buildMeta(ip, ua, now),
                    refreshTtl(), TimeUnit.MILLISECONDS);
            } else {
                redisUtil.expire(metaKey, refreshTtl(), TimeUnit.MILLISECONDS);
            }
            if (redisUtil.zScore(indexKey, sid) == null) {
                redisUtil.zAdd(indexKey, sid, now);
            }
            redisUtil.expire(indexKey, refreshTtl(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("刷新会话失败, userId={}, sid={}", userId, sid, e);
        }
    }

    /**
     * 更新会话活跃时间，由认证过滤器在每次请求时调用
     *
     * <p>用 SET NX 做 60 秒节流：只有节流键写入成功才真正更新活跃时间与在线集合，
     * 否则每个请求都要多两次 Redis 写。
     */
    public void touch(Long userId, String sid, String ip, String ua) {
        if (userId == null || sid == null) {
            return;
        }
        try {
            Boolean acquired = redisUtil.setIfAbsent(
                RedisKeyConstant.sessionThrottle(sid), "1", touchIntervalSeconds, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(acquired)) {
                return;
            }

            long now = System.currentTimeMillis();
            redisUtil.setWithExpire(RedisKeyConstant.sessionActive(sid),
                String.valueOf(now), refreshTtl(), TimeUnit.MILLISECONDS);
            markOnline(userId, now);

            // 自愈：Redis 重启或数据被淘汰后索引/meta 可能缺失，节流命中时补登记
            String indexKey = RedisKeyConstant.sessionIndex(userId);
            if (redisUtil.zScore(indexKey, sid) == null) {
                redisUtil.zAdd(indexKey, sid, now);
                redisUtil.expire(indexKey, refreshTtl(), TimeUnit.MILLISECONDS);
            }
            String metaKey = RedisKeyConstant.sessionMeta(sid);
            if (!Boolean.TRUE.equals(redisUtil.hasKey(metaKey))) {
                redisUtil.setWithExpire(metaKey, buildMeta(ip, ua, now),
                    refreshTtl(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("更新会话活跃时间失败, userId={}, sid={}", userId, sid, e);
        }
    }

    /**
     * 列出用户的全部活跃设备
     *
     * @param currentSid 发起本次请求的会话标识，用于标记「本机」
     */
    public List<SessionVO> listSessions(Long userId, String currentSid) {
        if (userId == null) {
            return List.of();
        }
        try {
            String indexKey = RedisKeyConstant.sessionIndex(userId);
            Set<ZSetOperations.TypedTuple<String>> tuples =
                redisUtil.zRangeWithScores(indexKey, 0, -1);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }

            List<String> sidList = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .toList();
            if (sidList.isEmpty()) {
                return List.of();
            }

            List<String> metaKeys = sidList.stream()
                .map(RedisKeyConstant::sessionMeta).toList();
            List<String> activeKeys = sidList.stream()
                .map(RedisKeyConstant::sessionActive).toList();
            List<String> metas = redisUtil.multiGet(metaKeys);
            List<String> actives = redisUtil.multiGet(activeKeys);

            List<SessionVO> result = new ArrayList<>();
            for (int i = 0; i < sidList.size(); i++) {
                String sid = sidList.get(i);
                String metaJson = metas == null || i >= metas.size() ? null : metas.get(i);
                // 元信息已过期或会话已被撤销：顺手从索引里移除，避免幽灵设备占名额
                if (metaJson == null || tokenBlacklistService.isSessionRevoked(sid)) {
                    redisUtil.zRem(indexKey, sid);
                    continue;
                }

                JsonNode meta = objectMapper.readTree(metaJson);
                long loginTime = meta.path("loginTime").asLong(0L);
                String activeValue = actives == null || i >= actives.size() ? null : actives.get(i);

                SessionVO vo = new SessionVO();
                vo.setSid(sid);
                vo.setIp(meta.path("ip").asText(null));
                vo.setDeviceName(resolveDeviceName(meta.path("ua").asText(null)));
                vo.setLoginTime(loginTime);
                vo.setLastActive(parseLong(activeValue, loginTime));
                vo.setCurrent(sid.equals(currentSid));
                result.add(vo);
            }

            result.sort(Comparator.comparing(SessionVO::getLoginTime).reversed());
            return result;
        } catch (Exception e) {
            log.warn("查询登录设备失败, userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 踢掉指定设备
     *
     * <p>先写撤销标记、成功后再清理元数据——顺序反过来会留下「列表里没了、
     * 令牌却还能用」的窗口，这是下线功能最危险的失败模式。
     *
     * @return 被踢的是否为当前设备
     */
    public boolean kickSession(Long userId, String sid, String currentSid) {
        if (sid == null || sid.isBlank()) {
            throw new BusinessException(404, "会话不存在或已失效");
        }
        // 归属校验：sid 虽然不可伪造，但别人手里的 sid 也不该能拿来踢人
        if (redisUtil.zScore(RedisKeyConstant.sessionIndex(userId), sid) == null) {
            throw new BusinessException(404, "会话不存在或已失效");
        }
        if (!tokenBlacklistService.revokeSession(sid, refreshTtl())) {
            throw new BusinessException(500, "下线失败，请稍后重试");
        }
        removeSessionData(userId, sid);
        return sid.equals(currentSid);
    }

    /**
     * 退出除当前设备外的所有会话
     *
     * @return 实际下线的设备数
     */
    public int kickOthers(Long userId, String currentSid) {
        if (currentSid == null || currentSid.isBlank()) {
            throw new BusinessException(400, "当前会话标识缺失，请重新登录后再操作");
        }
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisUtil.zRangeWithScores(RedisKeyConstant.sessionIndex(userId), 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String sid = tuple.getValue();
            if (sid == null || sid.equals(currentSid)) {
                continue;
            }
            if (tokenBlacklistService.revokeSession(sid, refreshTtl())) {
                removeSessionData(userId, sid);
                count++;
            }
        }
        return count;
    }

    /**
     * 清理单个会话的元数据，撤销标记由调用方负责写入
     *
     * <p>用于登出：登出是放行接口，拿不到认证上下文，userId 由调用方从令牌解析后传入。
     */
    public void removeSession(Long userId, String sid) {
        if (userId == null || sid == null) {
            return;
        }
        try {
            removeSessionData(userId, sid);
        } catch (Exception e) {
            log.warn("清理会话数据失败, userId={}, sid={}", userId, sid, e);
        }
    }

    /**
     * 撤销该用户的全部会话
     *
     * <p>用于禁用用户、删除用户与修改密码。禁用后不撤销的话，重新启用时旧令牌会复活。
     * 这里逐台尽力撤销，单台失败只告警，由调用方决定是否忽略。
     *
     * @return 实际撤销的会话数
     */
    public int revokeAll(Long userId) {
        if (userId == null) {
            return 0;
        }
        try {
            String indexKey = RedisKeyConstant.sessionIndex(userId);
            Set<ZSetOperations.TypedTuple<String>> tuples =
                redisUtil.zRangeWithScores(indexKey, 0, -1);
            int count = 0;
            if (tuples != null) {
                for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                    String sid = tuple.getValue();
                    if (sid == null) {
                        continue;
                    }
                    if (tokenBlacklistService.revokeSession(sid, refreshTtl())) {
                        count++;
                    }
                }
            }
            redisUtil.delete(indexKey);
            redisUtil.zRem(RedisKeyConstant.ONLINE_USERS, String.valueOf(userId));
            return count;
        } catch (Exception e) {
            log.warn("批量撤销会话失败, userId={}", userId, e);
            return 0;
        }
    }

    /**
     * 在线用户分页
     *
     * <p>在线口径：最近 N 分钟（默认 5 分钟）内带有效 AccessToken 发过请求。
     * 活跃度更新有节流，因此「最后活跃」误差不超过一个节流间隔。
     */
    public PageResult<OnlineUserVO> onlineUsers(int page, int size) {
        long now = System.currentTimeMillis();
        long cutoff = now - onlineWindowSeconds * 1000L;
        String onlineKey = RedisKeyConstant.ONLINE_USERS;
        try {
            // score 为毫秒时间戳（恒为正），下界传 0 即等价于 -inf
            redisUtil.zRemRangeByScore(onlineKey, 0, cutoff);
            Long total = redisUtil.zCount(onlineKey, cutoff, now);

            long offset = (long) (page - 1) * size;
            Set<ZSetOperations.TypedTuple<String>> tuples =
                redisUtil.zRevRangeWithScores(onlineKey, offset, offset + size - 1);
            if (tuples == null || tuples.isEmpty()) {
                return new PageResult<>(total == null ? 0L : total, List.of());
            }

            List<Long> userIds = tuples.stream()
                .map(t -> parseLong(t.getValue(), null))
                .filter(Objects::nonNull)
                .toList();
            if (userIds.isEmpty()) {
                return new PageResult<>(total == null ? 0L : total, List.of());
            }
            Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

            List<OnlineUserVO> list = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                Long userId = parseLong(tuple.getValue(), null);
                User user = userId == null ? null : userMap.get(userId);
                if (user == null) {
                    // 用户已被删除，清掉残留的在线记录
                    redisUtil.zRem(onlineKey, tuple.getValue());
                    continue;
                }
                OnlineUserVO vo = new OnlineUserVO();
                vo.setUserId(user.getId());
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
                vo.setDeviceCount(countDevices(user.getId()));
                vo.setLastActive(tuple.getScore() == null ? null : tuple.getScore().longValue());
                list.add(vo);
            }
            return new PageResult<>(total == null ? 0L : total, list);
        } catch (Exception e) {
            log.warn("查询在线用户失败", e);
            return new PageResult<>(0L, List.of());
        }
    }

    /**
     * 当前在线人数
     */
    public long countOnline() {
        long now = System.currentTimeMillis();
        long cutoff = now - onlineWindowSeconds * 1000L;
        String onlineKey = RedisKeyConstant.ONLINE_USERS;
        try {
            redisUtil.zRemRangeByScore(onlineKey, 0, cutoff);
            Long count = redisUtil.zCount(onlineKey, cutoff, now);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("统计在线人数失败", e);
            return 0L;
        }
    }

    // ========== 私有方法 ==========

    /**
     * 超出会话上限时踢掉最早登录的设备
     *
     * <p>先写入再裁剪，取索引中最老的若干条——刚登录的 sid 分数最大，不会被选中。
     * 并发登录时可能多淘汰一台，但不会出现「超限却没人被踢」。
     */
    private void evictOldest(Long userId, String currentSid) {
        String indexKey = RedisKeyConstant.sessionIndex(userId);
        Long count = redisUtil.zCard(indexKey);
        if (count == null || count <= maxCount) {
            return;
        }
        long excess = count - maxCount;
        Set<ZSetOperations.TypedTuple<String>> oldest =
            redisUtil.zRangeWithScores(indexKey, 0, excess - 1);
        if (oldest == null || oldest.isEmpty()) {
            return;
        }
        for (ZSetOperations.TypedTuple<String> tuple : oldest) {
            String sid = tuple.getValue();
            if (sid == null || sid.equals(currentSid)) {
                continue;
            }
            if (tokenBlacklistService.revokeSession(sid, refreshTtl())) {
                removeSessionData(userId, sid);
                log.info("会话数超出上限，已踢出最早登录的设备: userId={}, sid={}", userId, sid);
            }
        }
    }

    /**
     * 删除会话的元数据与索引，在线集合交给时间窗口判定，不在这里误删
     */
    private void removeSessionData(Long userId, String sid) {
        redisUtil.delete(RedisKeyConstant.sessionMeta(sid),
            RedisKeyConstant.sessionActive(sid),
            RedisKeyConstant.sessionThrottle(sid));
        redisUtil.zRem(RedisKeyConstant.sessionIndex(userId), sid);
    }

    /**
     * 统计该用户当前有效的设备数，顺带清理已过期的索引条目
     */
    private int countDevices(Long userId) {
        String indexKey = RedisKeyConstant.sessionIndex(userId);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisUtil.zRangeWithScores(indexKey, 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String sid = tuple.getValue();
            if (sid == null) {
                continue;
            }
            if (Boolean.TRUE.equals(redisUtil.hasKey(RedisKeyConstant.sessionMeta(sid)))) {
                count++;
            } else {
                redisUtil.zRem(indexKey, sid);
            }
        }
        return count;
    }

    private void markOnline(Long userId, long now) {
        redisUtil.zAdd(RedisKeyConstant.ONLINE_USERS, String.valueOf(userId), now);
    }

    private long refreshTtl() {
        return jwtTokenProvider.getRefreshExpirationMillis();
    }

    private String buildMeta(String ip, String ua, long loginTime) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("ip", ip);
        meta.put("ua", ua);
        meta.put("loginTime", loginTime);
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            // 序列化失败时退化为空对象，设备信息展示为「未知设备」
            return "{}";
        }
    }

    private Long parseLong(String value, Long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 User-Agent 推导设备描述，不引入 UA 解析库，覆盖主流浏览器与系统即可
     */
    private static String resolveDeviceName(String ua) {
        if (ua == null || ua.isBlank()) {
            return "未知设备";
        }
        String browser;
        if (ua.contains("Edg/")) {
            browser = "Edge";
        } else if (ua.contains("OPR/") || ua.contains("Opera")) {
            browser = "Opera";
        } else if (ua.contains("Firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("Chrome/")) {
            browser = "Chrome";
        } else if (ua.contains("Safari/")) {
            browser = "Safari";
        } else {
            browser = "未知浏览器";
        }

        String os;
        if (ua.contains("Windows")) {
            os = "Windows";
        } else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            os = "macOS";
        } else if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("iPhone") || ua.contains("iPad")) {
            os = "iOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        } else {
            os = "未知系统";
        }
        return browser + " / " + os;
    }
}
