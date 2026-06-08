import request from '../utils/request'

/**
 * 文章相关 API
 */

/**
 * 查询文章列表
 * @param {Object} params - { page, size, categoryId, keyword }
 */
export function getArticleList(params) {
  return request({
    url: '/article/list',
    method: 'get',
    params
  })
}

/**
 * 查询文章详情
 * @param {number} id - 文章ID
 */
export function getArticleDetail(id) {
  return request({
    url: `/article/${id}`,
    method: 'get'
  })
}

/**
 * 发布文章
 * @param {Object} data - 文章数据
 */
export function publishArticle(data) {
  return request({
    url: '/article',
    method: 'post',
    data
  })
}

/**
 * 修改文章
 * @param {Object} data - 文章数据
 */
export function updateArticle(data) {
  return request({
    url: '/article',
    method: 'put',
    data
  })
}

/**
 * 删除文章
 * @param {number} id - 文章ID
 */
export function deleteArticle(id) {
  return request({
    url: `/article/${id}`,
    method: 'delete'
  })
}

/**
 * 查询我的文章列表
 * @param {Object} params - { page, size }
 */
export function getMyArticles(params) {
  return request({
    url: '/user/article/list',
    method: 'get',
    params
  })
}
