<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAdminArticleList, deleteArticle } from '../../api/article'
import { useFormatTime } from '../../composables/useFormatTime'
import { useAdminTable } from '../../composables/useAdminTable'

const router = useRouter()
const { formatTime } = useFormatTime()
const keyword = ref('')

const { loading, list, total, page, size, fetch, handleDelete } = useAdminTable(
  (params) => getAdminArticleList({
    page: params.page,
    size: params.size,
    keyword: keyword.value || undefined
  }),
  {
    deleteFn: deleteArticle,
    deleteMessage: '确定要删除这篇文章吗？'
  }
)

const handleSearch = () => {
  page.value = 1
  fetch()
}

const getStatusLabel = (status) => (status === 1 ? '已发布' : '草稿')
const getStatusType = (status) => (status === 1 ? 'success' : 'warning')

onMounted(() => {
  fetch()
})
</script>

<template>
  <div v-loading="loading" class="tab-content">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索文章标题..."
        clearable
        style="width: 300px"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <el-table :data="list" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="author.nickname" label="作者" width="120" />
      <el-table-column prop="category.name" label="分类" width="100" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="viewCount" label="浏览" width="80" />
      <el-table-column prop="likeCount" label="点赞" width="80" />
      <el-table-column prop="createTime" label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right" class-name="operation-column">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="router.push(`/article/${row.id}`)">查看</el-button>
          <el-button type="primary" size="small" text @click="router.push(`/article/edit/${row.id}`)">编辑</el-button>
          <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="fetch"
        @size-change="fetch"
      />
    </div>
  </div>
</template>

<style scoped>
.tab-content {
  padding: 10px 0;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-pagination .el-pager li.is-active) {
  color: #fff;
}

:deep(.operation-column .cell) {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

:deep(.operation-column .el-button) {
  padding: 4px 8px;
  margin: 0;
}
</style>
