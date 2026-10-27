import request from '../utils/request'

/**
 * 站内通知相关 API
 */

/**
 * 通知列表
 * @param {Object} params - { page, size, type, unreadOnly }
 */
export function getNotifications(params) {
  return request({
    url: '/notification/list',
    method: 'get',
    params
  })
}

/**
 * 未读通知数
 */
export function getUnreadCount() {
  return request({
    url: '/notification/unread-count',
    method: 'get'
  })
}

/**
 * 标记单条已读
 * @param {number} id
 */
export function markNotificationRead(id) {
  return request({
    url: `/notification/${id}/read`,
    method: 'put'
  })
}

/**
 * 全部标记已读
 */
export function markAllNotificationsRead() {
  return request({
    url: '/notification/read-all',
    method: 'put'
  })
}
