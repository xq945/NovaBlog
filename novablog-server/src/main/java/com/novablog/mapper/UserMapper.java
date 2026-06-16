package com.novablog.mapper;

import com.novablog.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
     * 根据ID列表查询用户
     *
     * @param ids 用户ID列表
     * @return 用户列表
     */
    List<User> findByIds(@Param("ids") List<Long> ids);

    /**
     * 插入新用户
     *
     * @param user 用户实体
     * @return 影响行数
     */
    int insert(User user);

    /**
     * 更新用户个人信息
     *
     * @param id       用户ID
     * @param username 用户名
     * @param nickname 昵称
     * @param email    邮箱
     * @param avatar   头像URL
     * @param password 密码
     * @return 影响行数
     */
    int updateProfile(@Param("id") Long id,
                      @Param("username") String username,
                      @Param("nickname") String nickname,
                      @Param("email") String email,
                      @Param("avatar") String avatar,
                      @Param("password") String password);

    /**
     * 分页查询用户列表（按注册时间倒序）
     *
     * @param offset  偏移量
     * @param size    每页数量
     * @param keyword 关键词，可为 null
     * @return 用户列表
     */
    List<User> findList(@Param("offset") int offset,
                        @Param("size") int size,
                        @Param("keyword") String keyword);

    /**
     * 查询用户总数
     *
     * @param keyword 关键词，可为 null
     * @return 用户总数
     */
    Long countAll(@Param("keyword") String keyword);

    /**
     * 更新用户状态
     *
     * @param id     用户ID
     * @param status 状态
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 根据ID列表批量删除用户
     *
     * @param ids 用户ID列表
     * @return 影响行数
     */
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 查询指定用户中有文章的用户ID列表
     *
     * @param ids 用户ID列表
     * @return 有文章的用户ID列表
     */
    List<Long> findUserIdsWithArticles(@Param("ids") List<Long> ids);

    /**
     * 查询指定用户中有评论的用户ID列表
     *
     * @param ids 用户ID列表
     * @return 有评论的用户ID列表
     */
    List<Long> findUserIdsWithComments(@Param("ids") List<Long> ids);
}
