import request from '../utils/request'

/**
 * 文件上传 API
 */

/**
 * 上传文件到OSS
 * @param {File} file - 要上传的文件对象
 * @returns {Promise} 上传结果，包含文件的访问URL
 */
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/upload',
    method: 'post',
    data: formData
    // 不设置 Content-Type，让 axios 自动设置（包含正确的 boundary）
  })
}
