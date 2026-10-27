package com.novablog.service;

import com.novablog.vo.FollowStatusVO;

public interface FollowService {

    /**
     * 关注用户。重复关注幂等
     */
    FollowStatusVO follow(Long followeeId);

    /**
     * 取消关注。未关注时取关同样幂等
     */
    FollowStatusVO unfollow(Long followeeId);

    boolean isFollowing(Long followerId, Long followeeId);
}
