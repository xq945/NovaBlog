import request from '../utils/request'

/**
 * 分类相关 API
 */

/**
 * 查询所有分类
 */
export function getCategoryList() {
  return request({
    url: '/category/list',
    method: 'get'
  })
}

/**
 * 创建分类
 * @param {Object} data - { name, description }
 */
export function createCategory(data) {
  return request({
    url: '/category',
    method: 'post',
    data
  })
}

/**
 * 修改分类
 * @param {Object} data - { id, name, description }
 */
export function updateCategory(data) {
  return request({
    url: '/category',
    method: 'put',
    data
  })
}

/**
 * 删除分类
 * @param {number} id - 分类ID
 */
export function deleteCategory(id) {
  return request({
    url: `/category/${id}`,
    method: 'delete'
  })
}
