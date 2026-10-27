import request from '../utils/request'

/**
 * 文章收藏相关 API
 */

/**
 * 收藏文章（重复收藏幂等）
 * @param {number} articleId
 */
export function favoriteArticle(articleId) {
  return request({
    url: `/article/favorite/${articleId}`,
    method: 'post'
  })
}

/**
 * 取消收藏
 * @param {number} articleId
 */
export function unfavoriteArticle(articleId) {
  return request({
    url: `/article/favorite/${articleId}`,
    method: 'delete'
  })
}

/**
 * 查询收藏状态
 * @param {number} articleId
 */
export function getFavoriteStatus(articleId) {
  return request({
    url: '/article/favorite/status',
    method: 'get',
    params: { articleId }
  })
}

/**
 * 我的收藏列表
 * @param {Object} params - { page, size }
 */
export function getMyFavorites(params) {
  return request({
    url: '/article/favorite/list',
    method: 'get',
    params
  })
}
