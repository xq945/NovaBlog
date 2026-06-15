package com.novablog.service.assembler;

import com.novablog.entity.User;
import com.novablog.vo.AdminUserVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户视图对象组装器
 * 负责将 User 实体转换为各类 VO
 */
public final class UserVOAssembler {

    private UserVOAssembler() {
        // 禁止实例化
    }

    /**
     * 将 User 实体转换为管理员用户列表 VO
     *
     * @param user 用户实体
     * @return 管理员用户 VO
     */
    public static AdminUserVO toAdminUserVO(User user) {
        if (user == null) {
            return null;
        }
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    /**
     * 将 User 实体列表转换为管理员用户列表 VO 列表
     *
     * @param users 用户实体列表
     * @return 管理员用户 VO 列表
     */
    public static List<AdminUserVO> toAdminUserVOList(List<User> users) {
        if (users == null) {
            return new ArrayList<>();
        }
        List<AdminUserVO> voList = new ArrayList<>(users.size());
        for (User user : users) {
            AdminUserVO vo = toAdminUserVO(user);
            if (vo != null) {
                voList.add(vo);
            }
        }
        return voList;
    }
}
