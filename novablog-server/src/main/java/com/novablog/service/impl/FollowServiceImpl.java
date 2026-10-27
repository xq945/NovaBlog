package com.novablog.service.impl;

import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.entity.User;
import com.novablog.mapper.UserFollowMapper;
import com.novablog.mapper.UserMapper;
import com.novablog.service.FollowService;
import com.novablog.service.NotificationService;
import com.novablog.vo.FollowStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关注服务
 *
 * <p>纯 MySQL：关系表 + 唯一键保证幂等，计数用原子 SQL 维护。
 * 关注会同时修改两行 user（自己的关注数、对方的粉丝数），
 * 两条 UPDATE 统一按 userId 升序执行，避免 A、B 互相关注时交叉持锁导致死锁。
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final UserMapper userMapper;
    private final UserFollowMapper followMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public FollowStatusVO follow(Long followeeId) {
        Long followerId = requireLogin();
        if (followerId.equals(followeeId)) {
            throw new BusinessException(400, "不能关注自己");
        }

        User followee = userMapper.findById(followeeId);
        if (followee == null || followee.getStatus() == null || followee.getStatus() == 0) {
            throw new BusinessException(404, "用户不存在或已被禁用");
        }

        int inserted = followMapper.insertIgnore(followerId, followeeId);
        if (inserted > 0) {
            updateCounts(followerId, followeeId, true);
            // 与关注关系同事务落库；重复关注被唯一键拦下时不会走到这里
            notificationService.notifyFollow(followeeId, followerId);
        }
        return buildStatus(true, followeeId);
    }

    @Override
    @Transactional
    public FollowStatusVO unfollow(Long followeeId) {
        Long followerId = requireLogin();

        int deleted = followMapper.deleteFollow(followerId, followeeId);
        if (deleted > 0) {
            updateCounts(followerId, followeeId, false);
        }
        return buildStatus(false, followeeId);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null || followerId.equals(followeeId)) {
            return false;
        }
        return followMapper.exists(followerId, followeeId) > 0;
    }

    /**
     * 按 userId 升序更新两行计数。
     *
     * <p>互相关注时，事务 A 先锁 A 行再锁 B 行，事务 B 若反序就会死锁；
     * 固定顺序后两边的加锁序列一致，InnoDB 只需排队而不必回滚。
     */
    private void updateCounts(Long followerId, Long followeeId, boolean follow) {
        boolean followerFirst = followerId < followeeId;
        if (follow) {
            if (followerFirst) {
                userMapper.incrementFollowingCount(followerId);
                userMapper.incrementFollowerCount(followeeId);
            } else {
                userMapper.incrementFollowerCount(followeeId);
                userMapper.incrementFollowingCount(followerId);
            }
        } else {
            if (followerFirst) {
                userMapper.decrementFollowingCount(followerId);
                userMapper.decrementFollowerCount(followeeId);
            } else {
                userMapper.decrementFollowerCount(followeeId);
                userMapper.decrementFollowingCount(followerId);
            }
        }
    }

    private FollowStatusVO buildStatus(boolean followed, Long followeeId) {
        User followee = userMapper.findById(followeeId);
        long count = followee == null || followee.getFollowerCount() == null
            ? 0 : followee.getFollowerCount();
        FollowStatusVO vo = new FollowStatusVO();
        vo.setFollowed(followed);
        vo.setFollowerCount(count);
        return vo;
    }

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
