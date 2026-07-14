import request from '../utils/request'

/**
 * 收藏相关 API
 */

/**
 * 收藏文章
 * @param {number} articleId - 文章ID
 */
export function favoriteArticle(articleId) {
  return request({
    url: `/favorite/${articleId}`,
    method: 'post'
  })
}

/**
 * 取消收藏
 * @param {number} articleId - 文章ID
 */
export function unfavoriteArticle(articleId) {
  return request({
    url: `/favorite/${articleId}`,
    method: 'delete'
  })
}

/**
 * 查询收藏状态
 * @param {number} articleId - 文章ID
 */
export function getFavoriteStatus(articleId) {
  return request({
    url: `/favorite/${articleId}`,
    method: 'get'
  })
}

/**
 * 查询我的收藏列表
 * @param {Object} params - { page, size }
 */
export function getMyFavorites(params) {
  return request({
    url: '/favorite/list',
    method: 'get',
    params
  })
}
