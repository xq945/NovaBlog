package com.novablog.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UserRoleDTO {

    private List<Long> roleIds;
}
