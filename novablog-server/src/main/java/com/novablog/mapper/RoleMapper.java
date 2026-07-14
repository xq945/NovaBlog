package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 查询用户的所有角色名称
     */
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的所有权限标识
     */
    List<String> findPermissionNamesByUserId(@Param("userId") Long userId);
}
