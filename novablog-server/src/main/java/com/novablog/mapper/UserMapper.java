package com.novablog.mapper;

import com.novablog.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据访问层
 */
@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体，不存在返回 null
     */
    User findByUsername(@Param("username") String username);

    /**
     * 根据用户ID查询用户
     *
     * @param id 用户ID
     * @return 用户实体，不存在返回 null
     */
    User findById(@Param("id") Long id);

    /**
     * 插入新用户
     *
     * @param user 用户实体
     * @return 影响行数
     */
    int insert(User user);
}
