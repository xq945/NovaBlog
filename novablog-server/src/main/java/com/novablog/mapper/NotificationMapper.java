package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 插入通知。dedup_key 非空且撞唯一键时静默跳过
     *
     * @return 1-本次新增，0-被去重
     */
    int insertIgnore(Notification notification);

    Long countUnread(@Param("receiverId") Long receiverId);

    int markRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    int markAllRead(@Param("receiverId") Long receiverId);

    List<Notification> findList(@Param("receiverId") Long receiverId,
                                @Param("type") String type,
                                @Param("unreadOnly") boolean unreadOnly,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    Long countList(@Param("receiverId") Long receiverId,
                   @Param("type") String type,
                   @Param("unreadOnly") boolean unreadOnly);
}
