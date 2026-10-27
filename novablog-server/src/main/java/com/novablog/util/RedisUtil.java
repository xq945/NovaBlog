package com.novablog.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 封装 String/Set/ZSet 常用操作及 SCAN 扫描
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    /**
     * 获取底层 RedisTemplate，用于需要直接操作的高级场景
     *
     * @return StringRedisTemplate 实例
     */
    public StringRedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    // ========== String 操作 ==========

    /**
     * 自增 1
     *
     * @param key 键
     * @return 自增后的值
     */
    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 获取 String 值
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 设置 String 值
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 删除 key
     *
     * @param key 键
     * @return 是否删除成功
     */
    public Boolean del(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 设置 String 值并指定过期时间
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public void setWithExpire(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 键
     * @return true-存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置 key 的过期时间
     *
     * @param key     键
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return true-设置成功（key 存在），false-key 不存在
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * key 不存在时才写入，并指定过期时间（SET NX EX）
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return true-本次写入成功，false-已存在未写入
     */
    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    /**
     * 批量读取多个 String 值
     *
     * @param keys 键集合
     * @return 与 keys 顺序一致的值列表，不存在的 key 对应 null
     */
    public List<String> multiGet(Collection<String> keys) {
        return redisTemplate.opsForValue().multiGet(keys);
    }

    /**
     * 批量删除 key
     *
     * @param keys 键
     * @return 实际删除的数量
     */
    public Long delete(String... keys) {
        return redisTemplate.delete(Arrays.asList(keys));
    }

    // ========== Set 操作 ==========

    /**
     * 添加成员到 Set
     *
     * @param key    键
     * @param member 成员
     * @return 新增成功返回 1，已存在返回 0
     */
    public Long sAdd(String key, String member) {
        return redisTemplate.opsForSet().add(key, member);
    }

    /**
     * 从 Set 移除成员
     *
     * @param key    键
     * @param member 成员
     * @return 移除成功返回 1，不存在返回 0
     */
    public Long sRem(String key, String member) {
        return redisTemplate.opsForSet().remove(key, member);
    }

    /**
     * 判断成员是否在 Set 中
     *
     * @param key    键
     * @param member 成员
     * @return true-存在，false-不存在
     */
    public Boolean sIsMember(String key, String member) {
        return redisTemplate.opsForSet().isMember(key, member);
    }

    /**
     * 获取 Set 元素数量
     *
     * @param key 键
     * @return 元素数量
     */
    public Long sCard(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    /**
     * 删除整个 Set
     *
     * @param key 键
     * @return 是否删除成功
     */
    public Boolean sDel(String key) {
        return redisTemplate.delete(key);
    }

    // ========== ZSet 操作 ==========

    /**
     * 添加成员到 ZSet（带分数）
     *
     * @param key    键
     * @param member 成员
     * @param score  分数
     */
    public void zAdd(String key, String member, double score) {
        redisTemplate.opsForZSet().add(key, member, score);
    }

    /**
     * 按分数降序获取 ZSet 范围（带分数）
     *
     * @param key   键
     * @param start 起始索引（0-based）
     * @param end   结束索引（-1 表示到最后）
     * @return 成员及分数集合
     */
    public Set<ZSetOperations.TypedTuple<String>> zRevRangeWithScores(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
    }

    /**
     * 从 ZSet 移除成员
     *
     * @param key     键
     * @param members 成员
     * @return 移除数量
     */
    public Long zRem(String key, String... members) {
        return redisTemplate.opsForZSet().remove(key, (Object[]) members);
    }

    /**
     * 按分数升序获取 ZSet 范围（带分数）
     *
     * @param key   键
     * @param start 起始索引（0-based）
     * @param end   结束索引（-1 表示到最后）
     * @return 成员及分数集合
     */
    public Set<ZSetOperations.TypedTuple<String>> zRangeWithScores(String key, long start, long end) {
        return redisTemplate.opsForZSet().rangeWithScores(key, start, end);
    }

    /**
     * 获取成员的分数
     *
     * @param key    键
     * @param member 成员
     * @return 分数，成员不存在返回 null
     */
    public Double zScore(String key, String member) {
        return redisTemplate.opsForZSet().score(key, member);
    }

    /**
     * 获取 ZSet 成员数量
     *
     * @param key 键
     * @return 成员数量
     */
    public Long zCard(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    /**
     * 统计分数落在闭区间内的成员数量
     *
     * <p>注意分数用的是毫秒时间戳（恒为正），下界传 0 即等价于 -inf，
     * 避免 Spring Data Redis 把无穷大序列化成 Redis 无法解析的字面量。
     *
     * @param key 键
     * @param min 最小分数（含）
     * @param max 最大分数（含）
     * @return 数量
     */
    public Long zCount(String key, double min, double max) {
        return redisTemplate.opsForZSet().count(key, min, max);
    }

    /**
     * 移除分数落在闭区间内的成员
     *
     * @param key 键
     * @param min 最小分数（含）
     * @param max 最大分数（含）
     * @return 移除数量
     */
    public Long zRemRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().removeRangeByScore(key, min, max);
    }

    // ========== 通用操作 ==========

    /**
     * 使用 SCAN 扫描匹配的所有 key（避免 KEYS 阻塞）
     *
     * @param pattern 匹配模式，如 "article:view:*"
     * @return key 集合
     */
    public Set<String> scan(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            // SCAN 异常时返回空集合
        }
        return keys;
    }
}
