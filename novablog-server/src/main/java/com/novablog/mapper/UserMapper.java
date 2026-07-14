package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    User findByUsername(@Param("username") String username);

    User findById(@Param("id") Long id);

    List<User> findByIds(@Param("ids") List<Long> ids);

    int updateProfile(@Param("id") Long id,
                      @Param("username") String username,
                      @Param("nickname") String nickname,
                      @Param("email") String email,
                      @Param("avatar") String avatar,
                      @Param("password") String password);

    List<User> findList(@Param("offset") int offset,
                        @Param("size") int size,
                        @Param("keyword") String keyword);

    Long countAll(@Param("keyword") String keyword);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int deleteByIds(@Param("ids") List<Long> ids);

    List<Long> findUserIdsWithArticles(@Param("ids") List<Long> ids);

    List<Long> findUserIdsWithComments(@Param("ids") List<Long> ids);
}
