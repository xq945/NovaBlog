package com.novablog.service.converter;

import com.novablog.dto.UserDTO;
import com.novablog.entity.User;
import com.novablog.vo.UserVO;

/**
 * 用户对象转换器
 * 负责 User 实体与 DTO/VO 之间的转换
 */
public final class UserConverter {

    private UserConverter() {
        // 禁止实例化
    }

    /**
     * 将 User 实体转换为 JWT/ThreadLocal 用的 DTO
     *
     * @param user 用户实体
     * @return 用户 DTO
     */
    public static UserDTO toUserDTO(User user) {
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole());
        return dto;
    }

    /**
     * 将 User 实体转换为用户信息 VO
     *
     * @param user 用户实体
     * @return 用户信息 VO
     */
    public static UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }
}
