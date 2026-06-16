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
