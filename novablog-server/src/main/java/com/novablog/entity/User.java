package com.novablog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    private String email;

    private String bio;

    private String role;

    private Integer status;

    private Integer muted;

    private Long followerCount;

    private Long followingCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
