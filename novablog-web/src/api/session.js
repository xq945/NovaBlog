import request from '../utils/request'

/**
 * 会话与登录设备相关 API
 */

/**
 * 当前账号的登录设备列表
 */
export function getSessions() {
  return request({
    url: '/auth/sessions',
    method: 'get'
  })
}

/**
 * 下线指定设备
 * @param {string} sid - 会话标识
 */
export function kickSession(sid) {
  return request({
    url: `/auth/sessions/${sid}`,
    method: 'delete'
  })
}

/**
 * 退出其他所有设备，保留当前设备
 */
export function kickOtherSessions() {
  return request({
    url: '/auth/sessions/others',
    method: 'delete'
  })
}

/**
 * 管理员查询在线用户
 * @param {Object} params - { page, size }
 */
export function getOnlineUsers(params) {
  return request({
    url: '/admin/online-users',
    method: 'get',
    params
  })
}

/**
 * 管理员强制用户下线（撤销该用户全部会话）
 * @param {number} userId - 用户ID
 */
export function revokeUserSessions(userId) {
  return request({
    url: `/admin/users/${userId}/sessions`,
    method: 'delete'
  })
}
