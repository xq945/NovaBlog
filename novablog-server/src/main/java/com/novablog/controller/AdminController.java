package com.novablog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.common.annotation.LogOperation;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.dto.request.RoleDTO;
import com.novablog.dto.request.RolePermissionDTO;
import com.novablog.dto.request.UserRoleDTO;
import com.novablog.entity.LoginLog;
import com.novablog.entity.OperationLog;
import com.novablog.entity.Permission;
import com.novablog.entity.Role;
import com.novablog.entity.RolePermission;
import com.novablog.entity.User;
import com.novablog.entity.UserRole;
import com.novablog.mapper.LoginLogMapper;
import com.novablog.mapper.OperationLogMapper;
import com.novablog.mapper.PermissionMapper;
import com.novablog.mapper.RoleMapper;
import com.novablog.mapper.RolePermissionMapper;
import com.novablog.mapper.UserMapper;
import com.novablog.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@RequireAdmin
public class AdminController {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;

    // ==================== 角色管理 ====================

    @GetMapping("/role/list")
    public Result<List<Role>> roleList() {
        return Result.success(roleMapper.selectList(null));
    }

    @PostMapping("/role")
    @LogOperation(target = "ROLE", operation = "CREATE")
    public Result<Long> createRole(@RequestBody RoleDTO dto) {
        Role role = new Role();
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        roleMapper.insert(role);
        return Result.success(role.getId());
    }

    @PutMapping("/role/{id}")
    @LogOperation(target = "ROLE", operation = "UPDATE")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody RoleDTO dto) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return Result.error(404, "角色不存在");
        }
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        roleMapper.updateById(role);
        return Result.success();
    }

    @DeleteMapping("/role/{id}")
    @LogOperation(target = "ROLE", operation = "DELETE")
    public Result<Void> deleteRole(@PathVariable Long id) {
        // 删除角色前先清除关联
        rolePermissionMapper.delete(
            new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        userRoleMapper.delete(
            new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        roleMapper.deleteById(id);
        return Result.success();
    }

    @PutMapping("/role/{roleId}/permissions")
    @LogOperation(target = "ROLE", operation = "OTHER", detail = "配置角色权限")
    public Result<Void> assignPermissions(@PathVariable Long roleId,
                                           @RequestBody RolePermissionDTO dto) {
        // 清除旧关联
        rolePermissionMapper.delete(
            new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));

        // 插入新关联
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            for (Long permissionId : dto.getPermissionIds()) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permissionId);
                rolePermissionMapper.insert(rp);
            }
        }
        return Result.success();
    }

    // ==================== 权限管理 ====================

    @GetMapping("/permission/list")
    public Result<List<Permission>> permissionList() {
        return Result.success(permissionMapper.selectList(null));
    }

    // ==================== 用户角色分配 ====================

    @PutMapping("/user/{id}/roles")
    @LogOperation(target = "USER", operation = "OTHER", detail = "分配用户角色")
    public Result<Void> assignUserRoles(@PathVariable Long id, @RequestBody UserRoleDTO dto) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        // 清除旧关联
        userRoleMapper.delete(
            new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));

        // 插入新关联
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            for (Long roleId : dto.getRoleIds()) {
                UserRole ur = new UserRole();
                ur.setUserId(id);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
        return Result.success();
    }

    /**
     * 查询用户角色列表（含已分配的角色ID）
     */
    @GetMapping("/user/{id}/roles")
    public Result<Map<String, Object>> getUserRoles(@PathVariable Long id) {
        List<Role> allRoles = roleMapper.selectList(null);
        List<Long> assignedRoleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id))
            .stream().map(UserRole::getRoleId).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("roles", allRoles);
        result.put("assignedRoleIds", assignedRoleIds);
        return Result.success(result);
    }

    // ==================== 日志查询 ====================

    @GetMapping("/login-log")
    public Result<PageResult<LoginLog>> loginLog(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        IPage<LoginLog> p = loginLogMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<LoginLog>().orderByDesc(LoginLog::getLoginTime));
        return Result.success(new PageResult<>(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/operation-log")
    public Result<PageResult<OperationLog>> operationLog(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        IPage<OperationLog> p = operationLogMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getCreateTime));
        return Result.success(new PageResult<>(p.getTotal(), p.getRecords()));
    }
}
