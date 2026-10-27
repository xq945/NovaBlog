package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    /**
     * 插入关注关系。唯一键冲突时静默跳过，返回 0
     *
     * @return 1-本次新增，0-已关注
     */
    int insertIgnore(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * @return 1-确实取消了关注，0-原本就没关注
     */
    int deleteFollow(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    int exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * 该用户关注了谁，用于删除用户时回收对方的粉丝数
     */
    List<Long> findFolloweeIdsByFollower(@Param("followerId") Long followerId);

    /**
     * 谁关注了该用户，用于删除用户时回收对方的关注数
     */
    List<Long> findFollowerIdsByFollowee(@Param("followeeId") Long followeeId);
}
