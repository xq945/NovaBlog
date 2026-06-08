import request from '../utils/request'

/**
 * 发表评论
 * @param {Object} data - { articleId, content, parentId, replyToId }
 */
export function publishComment(data) {
  return request({
    url: '/comment',
    method: 'post',
    data
  })
}

/**
 * 查询评论列表
 * @param {Object} params - { articleId, page, size }
 */
export function getCommentList(params) {
  return request({
    url: '/comment/list',
    method: 'get',
    params
  })
}

/**
 * 删除评论
 * @param {number} id - 评论ID
 */
export function deleteComment(id) {
  return request({
    url: `/comment/${id}`,
    method: 'delete'
  })
}
