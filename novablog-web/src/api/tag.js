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
