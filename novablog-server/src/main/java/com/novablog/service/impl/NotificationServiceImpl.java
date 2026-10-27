package com.novablog.service.impl;

import com.novablog.common.PageResult;
import com.novablog.common.UserContext;
import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.common.exception.BusinessException;
import com.novablog.entity.Notification;
import com.novablog.entity.User;
import com.novablog.event.NotificationCreatedEvent;
import com.novablog.mapper.NotificationMapper;
import com.novablog.mapper.UserMapper;
import com.novablog.service.NotificationService;
import com.novablog.util.RedisUtil;
import com.novablog.vo.NotificationVO;
import com.novablog.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内通知实现
 *
 * <p>去重交给数据库：dedup_key 非空时由唯一键 (receiver_id, dedup_key) 兜底，
 * 所以不需要「先查再插」，并发下也不会写出重复通知。
 *
 * <p>未读数的正本是数据库 COUNT，Redis 只是 60 秒的读缓存；
 * 写通知或标记已读时直接删除缓存 key，而不是维护一个加减计数器，
 * 这样任何一次 Redis 故障都能靠重新 COUNT 自愈。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final long UNREAD_CACHE_SECONDS = 60;

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void notifyLike(Long receiverId, Long senderId, Long articleId, String articleTitle) {
        if (skip(receiverId, senderId)) {
            return;
        }
        Notification n = build(receiverId, senderId, "LIKE", "ARTICLE",
            articleId, articleId, "赞了你的文章《" + safeTitle(articleTitle) + "》",
            "LIKE:ARTICLE:" + articleId + ":" + senderId);
        saveAndPush(n);
    }

    @Override
    public void notifyComment(Long receiverId, Long senderId, Long articleId,
                              String articleTitle, boolean reply) {
        if (skip(receiverId, senderId)) {
            return;
        }
        String content = reply
            ? "回复了你的评论"
            : "评论了你的文章《" + safeTitle(articleTitle) + "》";
        // 评论与回复不做去重：同一个人评论两次是两条独立的业务事件
        Notification n = build(receiverId, senderId, reply ? "REPLY" : "COMMENT",
            "ARTICLE", articleId, articleId, content, null);
        saveAndPush(n);
    }

    @Override
    public void notifyFollow(Long receiverId, Long senderId) {
        if (skip(receiverId, senderId)) {
            return;
        }
        Notification n = build(receiverId, senderId, "FOLLOW", "USER",
            senderId, null, "关注了你", "FOLLOW:USER:" + senderId);
        saveAndPush(n);
    }

    @Override
    public PageResult<NotificationVO> list(int page, int size, String type, boolean unreadOnly) {
        Long userId = requireLogin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;

        List<Notification> rows = notificationMapper.findList(
            userId, type, unreadOnly, offset, safeSize);
        Long total = notificationMapper.countList(userId, type, unreadOnly);
        if (rows == null || rows.isEmpty()) {
            return new PageResult<>(total == null ? 0L : total, List.of());
        }

        // 批量查触发人，避免每条通知一次查询
        Set<Long> senderIds = rows.stream()
            .map(Notification::getSenderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, User> userMap = senderIds.isEmpty() ? Map.of()
            : userMapper.findByIds(new ArrayList<>(senderIds)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<NotificationVO> list = new ArrayList<>(rows.size());
        for (Notification n : rows) {
            list.add(toVO(n, userMap.get(n.getSenderId())));
        }
        return new PageResult<>(total == null ? 0L : total, list);
    }

    @Override
    public long countUnread() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return 0L;
        }
        String key = RedisKeyConstant.notifyUnread(userId);
        try {
            String cached = redisUtil.get(key);
            if (cached != null) {
                return Long.parseLong(cached);
            }
        } catch (Exception e) {
            log.warn("读取未读通知缓存失败, userId={}", userId, e);
        }

        Long count = notificationMapper.countUnread(userId);
        long value = count == null ? 0L : count;
        try {
            redisUtil.setWithExpire(key, String.valueOf(value),
                UNREAD_CACHE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入未读通知缓存失败, userId={}", userId, e);
        }
        return value;
    }

    @Override
    public boolean markRead(Long id) {
        Long userId = requireLogin();
        boolean updated = notificationMapper.markRead(id, userId) > 0;
        if (updated) {
            evictUnreadCache(userId);
        }
        return updated;
    }

    @Override
    public int markAllRead() {
        Long userId = requireLogin();
        int updated = notificationMapper.markAllRead(userId);
        evictUnreadCache(userId);
        return updated;
    }

    // ========== 私有方法 ==========

    private boolean skip(Long receiverId, Long senderId) {
        return receiverId == null || receiverId.equals(senderId);
    }

    private Notification build(Long receiverId, Long senderId, String type, String targetType,
                               Long targetId, Long articleId, String content, String dedupKey) {
        Notification n = new Notification();
        n.setReceiverId(receiverId);
        n.setSenderId(senderId);
        n.setType(type);
        n.setTargetType(targetType);
        n.setTargetId(targetId);
        n.setArticleId(articleId);
        n.setContent(content);
        n.setIsRead(0);
        n.setDedupKey(dedupKey);
        return n;
    }

    private void saveAndPush(Notification n) {
        int inserted;
        try {
            inserted = notificationMapper.insertIgnore(n);
        } catch (Exception e) {
            // 通知写失败不能把评论、点赞、关注一起拖垮
            log.warn("写入通知失败, receiver={}, type={}", n.getReceiverId(), n.getType(), e);
            return;
        }
        if (inserted == 0) {
            // 被 dedup_key 唯一键拦下：重复点赞或重复关注，不重复推送
            return;
        }

        evictUnreadCache(n.getReceiverId());

        Notification saved = notificationMapper.selectById(n.getId());
        User sender = n.getSenderId() == null ? null : userMapper.findById(n.getSenderId());
        eventPublisher.publishEvent(
            new NotificationCreatedEvent(n.getReceiverId(), toVO(saved, sender)));
    }

    private void evictUnreadCache(Long userId) {
        try {
            redisUtil.delete(RedisKeyConstant.notifyUnread(userId));
        } catch (Exception e) {
            log.warn("清除未读通知缓存失败, userId={}", userId, e);
        }
    }

    private NotificationVO toVO(Notification n, User sender) {
        if (n == null) {
            return null;
        }
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTargetType(n.getTargetType());
        vo.setTargetId(n.getTargetId());
        vo.setArticleId(n.getArticleId());
        vo.setContent(n.getContent());
        vo.setIsRead(n.getIsRead() != null && n.getIsRead() == 1);
        vo.setCreateTime(n.getCreateTime());
        if (sender != null) {
            UserVO userVO = new UserVO();
            userVO.setId(sender.getId());
            userVO.setNickname(sender.getNickname());
            userVO.setAvatar(sender.getAvatar());
            vo.setSender(userVO);
        }
        return vo;
    }

    private String safeTitle(String title) {
        if (title == null) {
            return "文章";
        }
        return title.length() > 30 ? title.substring(0, 30) + "…" : title;
    }

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
