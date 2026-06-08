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
