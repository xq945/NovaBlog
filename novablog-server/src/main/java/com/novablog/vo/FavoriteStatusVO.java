package com.novablog.vo;

import lombok.Data;

/**
 * 收藏状态。增删接口都返回这份权威状态，前端不做本地猜数
 */
@Data
public class FavoriteStatusVO {

    private Boolean favorited;

    private Integer favoriteCount;
}
