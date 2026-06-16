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
 * 导入文件生成文章草稿数据
 * @param {File} file - 上传的文件
 */
export function importArticle(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/article/import',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
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

/**
 * 点赞文章
 * @param {number} id - 文章ID
 */
export function likeArticle(id) {
  return request({
    url: `/article/like/${id}`,
    method: 'post'
  })
}

/**
 * 取消点赞
 * @param {number} id - 文章ID
 */
export function unlikeArticle(id) {
  return request({
    url: `/article/like/${id}`,
    method: 'delete'
  })
}

/**
 * 查询点赞状态
 * @param {number} articleId - 文章ID
 */
export function getLikeStatus(articleId) {
  return request({
    url: '/article/like/status',
    method: 'get',
    params: { articleId }
  })
}

/**
 * 查询热门文章列表
 * @param {number} size - 数量，默认10
 */
export function getHotArticles(size = 10) {
  return request({
    url: '/article/hot',
    method: 'get',
    params: { size }
  })
}

/**
 * 管理员查询所有文章（含草稿）
 * @param {Object} params - { page, size, keyword }
 */
export function getAdminArticleList(params) {
  return request({
    url: '/article/admin/list',
    method: 'get',
    params
  })
}
