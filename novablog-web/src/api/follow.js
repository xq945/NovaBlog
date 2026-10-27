import request from '../utils/request'

/**
 * 用户关注相关 API
 */

/**
 * 关注用户（重复关注幂等）
 * @param {number} userId
 */
export function followUser(userId) {
  return request({
    url: `/user/${userId}/follow`,
    method: 'post'
  })
}

/**
 * 取消关注
 * @param {number} userId
 */
export function unfollowUser(userId) {
  return request({
    url: `/user/${userId}/follow`,
    method: 'delete'
  })
}
