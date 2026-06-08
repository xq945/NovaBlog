import request from '../utils/request'

/**
 * 标签相关 API
 */

/**
 * 查询所有标签
 */
export function getTagList() {
  return request({
    url: '/tag/list',
    method: 'get'
  })
}

/**
 * 创建标签
 * @param {Object} data - { name }
 */
export function createTag(data) {
  return request({
    url: '/tag',
    method: 'post',
    data
  })
}

/**
 * 修改标签
 * @param {Object} data - { id, name }
 */
export function updateTag(data) {
  return request({
    url: '/tag',
    method: 'put',
    data
  })
}

/**
 * 删除标签
 * @param {number} id - 标签ID
 */
export function deleteTag(id) {
  return request({
    url: `/tag/${id}`,
    method: 'delete'
  })
}
