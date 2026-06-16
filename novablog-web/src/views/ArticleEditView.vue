<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { publishArticle, updateArticle, getArticleDetail, importArticle, generateSummary } from '../api/article'
import { getCategoryList } from '../api/category'
import { getTagList } from '../api/tag'
import { uploadFile } from '../api/upload'

const route = useRoute()
const router = useRouter()

const isEdit = ref(false)
const articleId = ref(null)
const loading = ref(false)
const coverLoading = ref(false)
const importLoading = ref(false)
const summaryLoading = ref(false)
const categories = ref([])
const tags = ref([])
const importFileRef = ref(null)

const form = reactive({
  title: '',
  content: '',
  summary: '',
  cover: '',
  categoryId: null,
  tagIds: [],
  status: 1
})

const formRef = ref(null)

const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 1, max: 100, message: '标题长度为1-100字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入正文', trigger: 'blur' }
  ],
  categoryId: [
    { required: true, message: '请选择分类', trigger: 'change' }
  ]
}

const fetchCategories = async () => {
  try {
    const res = await getCategoryList()
    if (res.code === 200) {
      categories.value = res.data
    }
  } catch (error) {
    console.error('加载分类失败', error)
  }
}

const fetchTags = async () => {
  try {
    const res = await getTagList()
    if (res.code === 200) {
      tags.value = res.data
    }
  } catch (error) {
    console.error('加载标签失败', error)
  }
}

const fetchArticle = async (id) => {
  loading.value = true
  try {
    const res = await getArticleDetail(id)
    if (res.code === 200) {
      const data = res.data
      form.title = data.title
      form.content = data.content
      form.summary = data.summary || ''
      form.cover = data.cover || ''
      form.categoryId = data.category?.id
      form.status = data.status
      // 将 tagList 转为 tagIds
      if (data.tagList && data.tagList.length > 0) {
        form.tagIds = data.tagList.map(t => t.id)
      }
    } else {
      ElMessage.error('文章不存在')
      router.push('/')
    }
  } catch (error) {
    ElMessage.error('加载文章失败')
    router.push('/')
  } finally {
    loading.value = false
  }
}

const handlePublish = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    form.status = 1
    const res = isEdit.value
      ? await updateArticle({ ...form, id: articleId.value })
      : await publishArticle(form)

    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '修改成功' : '发布成功')
      router.push('/')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    loading.value = false
  }
}

const handleSaveDraft = async () => {
  if (!form.title) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!form.content) {
    ElMessage.warning('请输入正文')
    return
  }

  loading.value = true
  try {
    form.status = 0
    const res = isEdit.value
      ? await updateArticle({ ...form, id: articleId.value })
      : await publishArticle(form)

    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '已保存为草稿' : '草稿保存成功')
      router.push('/')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

/**
 * 封面图上传前的校验
 */
const beforeCoverUpload = (file) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/gif' || file.type === 'image/webp'
  if (!isJpgOrPng) {
    ElMessage.error('仅支持 jpg/png/gif/webp 格式的图片')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

/**
 * 自定义封面图上传
 */
const handleCoverUpload = async (options) => {
  coverLoading.value = true
  try {
    const res = await uploadFile(options.file)
    if (res.code === 200) {
      form.cover = res.data.url
      ElMessage.success('封面图上传成功')
    } else {
      ElMessage.error(res.message || '上传失败')
      options.onError()
    }
  } catch (error) {
    ElMessage.error('上传失败')
    options.onError()
  } finally {
    coverLoading.value = false
  }
}

/**
 * 删除封面图
 */
const removeCover = () => {
  form.cover = ''
}

/**
 * 导入文件前的校验
 */
const beforeImportUpload = (file) => {
  const validExtensions = ['.md', '.txt', '.docx', '.pdf']
  const name = file.name.toLowerCase()
  const isValid = validExtensions.some(ext => name.endsWith(ext))
  if (!isValid) {
    ElMessage.error('仅支持 .md、.txt、.docx、.pdf 格式文件')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

/**
 * 导入文件并填充表单
 */
const handleImportFile = async (options) => {
  importLoading.value = true
  try {
    const res = await importArticle(options.file)
    if (res.code === 200) {
      const data = res.data
      form.title = data.title || ''
      form.content = data.content || ''
      form.summary = data.summary || ''
      // 导入视为新文章，退出编辑模式
      isEdit.value = false
      articleId.value = null
      // 清除路由中的 id 参数，避免刷新后进入编辑模式
      if (route.params.id) {
        router.replace('/article/edit')
      }
      ElMessage.success('文件导入成功，请检查内容并完善分类、标签和图片后发布')
    } else {
      ElMessage.error(res.message || '导入失败')
      options.onError()
    }
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
    options.onError()
  } finally {
    importLoading.value = false
    // 清空选择，允许重复导入同一文件
    if (importFileRef.value) {
      importFileRef.value.clearFiles()
    }
  }
}

/**
 * AI 生成摘要
 */
const handleGenerateSummary = async () => {
  if (!form.content || form.content.trim().length < 100) {
    ElMessage.warning('正文内容过少，无法生成摘要')
    return
  }

  // 如果已有摘要，先确认是否覆盖
  if (form.summary && form.summary.trim()) {
    try {
      await ElMessageBox.confirm('已存在摘要，是否覆盖？', '确认覆盖', {
        confirmButtonText: '覆盖',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }

  summaryLoading.value = true
  try {
    const res = await generateSummary(form.content)
    if (res.code === 200) {
      form.summary = res.data.summary || ''
      ElMessage.success('摘要已生成')
    } else {
      ElMessage.error(res.message || '摘要生成失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '摘要生成失败')
  } finally {
    summaryLoading.value = false
  }
}

/**
 * MdEditor 图片上传回调
 */
const onUploadImg = async (files, callback) => {
  try {
    const res = await Promise.all(
      files.map(file => uploadFile(file))
    )
    const urls = res.map(item => {
      if (item.code === 200) {
        return item.data.url
      }
      throw new Error(item.message || '上传失败')
    })
    callback(urls)
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
    // 通知 MdEditor 上传失败，取消 loading 状态
    callback([])
  }
}

onMounted(() => {
  fetchCategories()
  fetchTags()

  const id = route.params.id
  if (id) {
    isEdit.value = true
    articleId.value = Number(id)
    fetchArticle(articleId.value)
  }
})
</script>

<template>
  <div class="article-edit-page">
    <main class="article-edit-main">
      <!-- 顶部工具栏 -->
      <div class="edit-toolbar">
        <div class="toolbar-title">
          {{ isEdit ? '编辑文章' : '发布文章' }}
        </div>
        <div class="toolbar-actions">
          <el-upload
            ref="importFileRef"
            class="import-upload"
            :http-request="handleImportFile"
            :before-upload="beforeImportUpload"
            accept=".md,.txt,.docx,.pdf"
            :show-file-list="false"
            :disabled="importLoading"
          >
            <el-button :loading="importLoading" type="success">
              <el-icon><Upload /></el-icon> 导入文件
            </el-button>
          </el-upload>
          <el-button @click="goBack">取消</el-button>
          <el-button type="info" @click="handleSaveDraft" :loading="loading">
            保存草稿
          </el-button>
          <el-button type="primary" @click="handlePublish" :loading="loading">
            {{ isEdit ? '保存修改' : '立即发布' }}
          </el-button>
        </div>
      </div>

      <!-- 编辑表单 -->
      <div class="content-area" v-loading="loading">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="edit-form"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="请输入文章标题"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
                <el-option
                  v-for="cat in categories"
                  :key="cat.id"
                  :label="cat.name"
                  :value="cat.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签">
              <el-select
                v-model="form.tagIds"
                multiple
                placeholder="请选择标签"
                style="width: 100%"
              >
                <el-option
                  v-for="tag in tags"
                  :key="tag.id"
                  :label="tag.name"
                  :value="tag.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="封面图">
          <div class="cover-uploader">
            <el-upload
              v-if="!form.cover"
              class="cover-upload"
              :http-request="handleCoverUpload"
              :before-upload="beforeCoverUpload"
              accept="image/jpeg,image/png,image/gif,image/webp"
              :show-file-list="false"
            >
              <div class="upload-placeholder" v-loading="coverLoading">
                <el-icon :size="28"><Plus /></el-icon>
                <span class="upload-text">点击上传封面图</span>
                <span class="upload-hint">支持 jpg/png/gif/webp，最大5MB</span>
              </div>
            </el-upload>
            <div v-else class="cover-preview">
              <el-image
                :src="form.cover"
                fit="cover"
                class="preview-img"
              />
              <div class="cover-actions">
                <el-button type="danger" size="small" circle @click="removeCover">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item class="summary-form-item">
          <template #label>
            <div class="summary-label-row">
              <span>摘要</span>
              <el-button
                type="primary"
                :loading="summaryLoading"
                class="summary-generate-btn"
                @click="handleGenerateSummary"
              >
                <span>AI 生成</span>
              </el-button>
            </div>
          </template>
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="4"
            placeholder="留空保存时将自动从正文生成；也可点击标签栏按钮由 AI 自动生成摘要..."
            maxlength="500"
            show-word-limit
            class="summary-textarea"
          />
        </el-form-item>

        <el-form-item label="正文" prop="content" class="content-editor">
          <MdEditor
            v-model="form.content"
            theme="dark"
            placeholder="请输入 Markdown 格式的正文..."
            @onUploadImg="onUploadImg"
            :toolbars="[
              'bold',
              'underline',
              'italic',
              'strikeThrough',
              'title',
              'sub',
              'sup',
              'quote',
              'unorderedList',
              'orderedList',
              'codeRow',
              'code',
              'link',
              'image',
              'table',
              'preview',
              'catalog'
            ]"
            style="height: 600px"
          />
        </el-form-item>
      </el-form>
    </div>
  </main>
</div>
</template>

<style scoped>
.article-edit-page {
  min-height: 100vh;
  background: var(--nb-bg-primary);
  color: var(--nb-text-primary);
}

/* 顶部工具栏 */
.edit-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: var(--nb-bg-glass);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--nb-border-color);
  position: sticky;
  top: 0;
  z-index: 100;
}

.toolbar-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--nb-text-primary);
}

.toolbar-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.import-upload :deep(.el-upload) {
  display: inline-block;
}

/* 内容区域 */
.content-area {
  width: 90%;
  max-width: 1600px;
  margin: 0 auto;
  padding: 32px 24px;
}

.edit-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--nb-text-secondary);
}

/* 统一暗色输入框 */
.edit-form :deep(.el-input__wrapper),
.edit-form :deep(.el-textarea__inner),
.edit-form :deep(.el-select .el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.05) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset !important;
}

.edit-form :deep(.el-input__wrapper.is-focus),
.edit-form :deep(.el-textarea__inner:focus),
.edit-form :deep(.el-select .el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--nb-accent) inset !important;
}

.edit-form :deep(.el-input__wrapper:hover),
.edit-form :deep(.el-textarea__inner:hover),
.edit-form :deep(.el-select .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.2) inset !important;
}

.edit-form :deep(.el-input__inner),
.edit-form :deep(.el-textarea__inner) {
  color: var(--nb-text-primary) !important;
}

.edit-form :deep(.el-input__inner::placeholder),
.edit-form :deep(.el-textarea__inner::placeholder) {
  color: var(--nb-text-muted) !important;
}

.edit-form :deep(.el-input__count) {
  background: transparent !important;
  color: var(--nb-text-muted) !important;
}

.edit-form :deep(.el-select .el-select__tags) {
  background: transparent;
}

/* 摘要区域 */
.summary-form-item :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--nb-text-primary);
  width: 100%;
}

.summary-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 12px;
}

.summary-generate-btn {
  border-radius: 8px;
  padding: 6px 14px;
  font-weight: 500;
  flex-shrink: 0;
  font-size: 0.85rem;
}

.summary-textarea :deep(.el-textarea__inner) {
  min-height: 120px;
  padding: 12px 15px;
  line-height: 1.6;
  resize: none;
}

.summary-textarea :deep(.el-input__count) {
  right: 15px;
  bottom: 12px;
}

.content-editor :deep(.md-editor) {
  border-radius: 8px;
  border: 1px solid var(--nb-border-color);
}

/* 封面图上传 */
.cover-uploader {
  width: 100%;
}

.cover-upload :deep(.el-upload) {
  width: 100%;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 180px;
  border: 2px dashed rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
  color: var(--nb-text-muted);
  background: rgba(255, 255, 255, 0.03);
}

.upload-placeholder:hover {
  border-color: var(--nb-accent);
  color: var(--nb-accent);
}

.upload-text {
  font-size: 14px;
}

.upload-hint {
  font-size: 12px;
  color: var(--nb-text-muted);
}

.cover-preview {
  position: relative;
  width: 100%;
  height: 180px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--nb-border-color);
}

.preview-img {
  width: 100%;
  height: 100%;
}

.cover-actions {
  position: absolute;
  top: 8px;
  right: 8px;
}

@media (max-width: 768px) {
  .navbar {
    padding: 16px;
  }

  .nav-actions {
    gap: 8px;
  }

  .content-area {
    padding: 20px 16px;
  }

  .summary-label-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-generate-btn {
    align-self: flex-end;
  }
}
</style>
