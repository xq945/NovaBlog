package com.novablog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知实时推送的连接注册表
 *
 * <p>每个连接一个 unicast sink，Map 只做「用户 → 连接集合」的索引。
 * 这样多标签页互不干扰：一个慢客户端把缓冲区撑满，只影响它自己那条连接。
 *
 * <p>SSE 只在握手时校验一次 JWT，之后不会自动失效，所以心跳里顺带检查
 * 会话是否已被撤销（登出、被踢、禁用都会撤销会话），撤销即关闭连接。
 *
 * <p>当前是单实例实现：通知由哪个实例产生，就推给连在该实例上的用户。
 * 多实例部署需要接一层 Redis Pub/Sub 广播，见 README。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSseService {

    private static final int MAX_CONNECTIONS_PER_USER = 5;

    private static final int PER_CONNECTION_BUFFER = 64;

    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 用 Spring 容器里的 ObjectMapper：它注册了 JavaTimeModule 和全局日期格式，
     * 自己 new 一个会让 NotificationVO 里的 LocalDateTime 直接序列化失败
     */
    private final ObjectMapper objectMapper;

    private final Map<Long, Set<Connection>> connections = new ConcurrentHashMap<>();

    /**
     * 建立一条通知流
     *
     * @param userId 用户ID
     * @param sid    握手时令牌里的会话标识，用于心跳检查会话是否已撤销
     */
    public Flux<ServerSentEvent<String>> subscribe(Long userId, String sid) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast()
            .onBackpressureBuffer(new ArrayBlockingQueue<>(PER_CONNECTION_BUFFER));
        Connection connection = new Connection(sink, sid);

        Set<Connection> set = connections.computeIfAbsent(
            userId, k -> ConcurrentHashMap.newKeySet());
        set.add(connection);
        evictOldestIfNeeded(userId, set, connection);

        return sink.asFlux().doFinally(signal -> remove(userId, connection));
    }

    /**
     * 向某个用户的所有连接推送一条通知
     */
    public void push(Long userId, NotificationVO notification) {
        if (userId == null) {
            return;
        }
        Set<Connection> set = connections.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(notification);
        } catch (Exception e) {
            log.warn("序列化通知失败, userId={}", userId, e);
            return;
        }
        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
            .event("notification")
            .data(json)
            .build();

        for (Connection connection : set) {
            if (!connection.send(event)) {
                log.debug("推送失败，关闭连接: userId={}, sid={}", userId, connection.sid);
                connection.complete();
                set.remove(connection);
            }
        }
        if (set.isEmpty()) {
            connections.remove(userId, set);
        }
    }

    /**
     * 心跳与失效清理
     *
     * <p>25 秒一次，比常见网关 60 秒的空闲超时更短。
     * 既防止代理把长连接当空闲断开，也顺带发现已经登出或被踢的连接。
     */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        connections.forEach((userId, set) -> {
            for (Connection connection : new ArrayList<>(set)) {
                if (tokenBlacklistService.isSessionRevoked(connection.sid)) {
                    connection.complete();
                    set.remove(connection);
                    continue;
                }
                ServerSentEvent<String> ping = ServerSentEvent.<String>builder()
                    .comment("ping")
                    .build();
                if (!connection.send(ping)) {
                    connection.complete();
                    set.remove(connection);
                }
            }
            if (set.isEmpty()) {
                connections.remove(userId, set);
            }
        });
    }

    /**
     * 当前连接数，供排查使用
     */
    public int connectionCount() {
        return connections.values().stream().mapToInt(Set::size).sum();
    }

    private void evictOldestIfNeeded(Long userId, Set<Connection> set, Connection current) {
        if (set.size() <= MAX_CONNECTIONS_PER_USER) {
            return;
        }
        Connection oldest = set.stream()
            .filter(c -> c != current)
            .min(Comparator.comparingLong(c -> c.createdAt))
            .orElse(null);
        if (oldest != null) {
            log.info("用户连接数超限，关闭最旧连接: userId={}, sid={}", userId, oldest.sid);
            oldest.complete();
            set.remove(oldest);
        }
    }

    private void remove(Long userId, Connection connection) {
        Set<Connection> set = connections.get(userId);
        if (set != null) {
            set.remove(connection);
            if (set.isEmpty()) {
                connections.remove(userId, set);
            }
        }
    }

    /**
     * 一条 SSE 连接。sink 不是串行安全的，发送必须加锁，
     * 否则通知推送和心跳同时到达时会得到 FAIL_NON_SERIALIZED。
     */
    private static final class Connection {

        private final Sinks.Many<ServerSentEvent<String>> sink;

        private final String sid;

        private final long createdAt = System.currentTimeMillis();

        private Connection(Sinks.Many<ServerSentEvent<String>> sink, String sid) {
            this.sink = sink;
            this.sid = sid;
        }

        private synchronized boolean send(ServerSentEvent<String> event) {
            return !sink.tryEmitNext(event).isFailure();
        }

        private void complete() {
            sink.tryEmitComplete();
        }
    }
}
