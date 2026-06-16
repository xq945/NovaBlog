package com.novablog.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑用户消息并重新生成请求
 */
@Data
public class EditMessageRequest {

    /**
     * 修改后的用户问题
     */
    @NotBlank(message = "问题内容不能为空")
    @Size(max = 500, message = "问题长度不能超过500字符")
    private String content;
}
