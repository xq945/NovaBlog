import request from '../utils/request'

/**
 * 用户相关 API
 */

/**
 * 用户注册
 * @param {Object} data - { username, nickname, password }
 */
export function register(data) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

/**
 * 用户登录
 * @param {Object} data - { username, password }
 */
export function login(data) {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

/**
 * 登出：撤销整个服务端会话。
 * 带上 refreshToken 是因为 AccessToken 可能已过期，后端需要靠它定位会话
 * @param {string} refreshToken - 当前 RefreshToken
 */
export function logout(refreshToken) {
  return request({
    url: '/auth/logout',
    method: 'post',
    data: { refreshToken }
  })
}

/**
 * 获取当前登录用户信息
 */
export function getProfile() {
  return request({
    url: '/user/profile',
    method: 'get'
  })
}

/**
 * 修改个人信息
 * @param {Object} data - { nickname, email, avatar }
 */
export function updateProfile(data) {
  return request({
    url: '/user/profile',
    method: 'put',
    data
  })
}

/**
 * 公开用户主页信息（含粉丝数、关注数、是否已关注）
 * @param {number} id - 用户ID
 */
export function getUserProfile(id) {
  return request({
    url: `/user/${id}`,
    method: 'get'
  })
}

/**
 * 用户主页的文章列表
 * @param {number} id - 用户ID
 * @param {Object} params - { page, size }
 */
export function getUserArticles(id, params) {
  return request({
    url: `/user/${id}/articles`,
    method: 'get',
    params
  })
}

/**
 * 管理员查询用户列表
 * @param {Object} params - { page, size, keyword }
 */
export function getAdminUserList(params) {
  return request({
    url: '/user/admin/list',
    method: 'get',
    params
  })
}

/**
 * 管理员批量删除用户
 * @param {number[]} userIds - 用户ID数组
 */
export function batchDeleteUsers(userIds) {
  return request({
    url: '/user/admin/batch',
    method: 'delete',
    data: userIds
  })
}

/**
 * 管理员修改用户状态
 * @param {Object} data - { userId, status }
 */
export function updateUserStatus(data) {
  return request({
    url: '/user/admin/status',
    method: 'put',
    data
  })
}
