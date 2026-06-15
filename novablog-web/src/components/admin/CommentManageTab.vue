<script setup>
import { ref, onMounted } from 'vue'
import { getAdminCommentList, deleteComment } from '../../api/comment'
import { useFormatTime } from '../../composables/useFormatTime'
import { useAdminTable } from '../../composables/useAdminTable'

const { formatTime } = useFormatTime()
const articleId = ref(null)

const { loading, list, total, page, size, fetch, handleDelete } = useAdminTable(
  (params) => {
    const p = { page: params.page, size: params.size }
    if (articleId.value) {
      p.articleId = articleId.value
    }
    return getAdminCommentList(p)
  },
  {
    deleteFn: deleteComment,
    deleteMessage: '确定要删除这条评论吗？'
  }
)

const handleFilter = () => {
  page.value = 1
  fetch()
}

onMounted(() => {
  fetch()
})
</script>

<template>
  <div v-loading="loading" class="tab-content">
    <div class="toolbar">
      <el-input
        v-model="articleId"
        placeholder="按文章ID筛选"
        clearable
        style="width: 200px"
        type="number"
      />
      <el-button type="primary" @click="handleFilter">
        <el-icon><Search /></el-icon> 筛选
      </el-button>
    </div>

    <el-table :data="list" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
      <el-table-column prop="articleTitle" label="所属文章" width="150" show-overflow-tooltip />
      <el-table-column prop="user.nickname" label="评论者" width="100" />
      <el-table-column prop="parentId" label="类型" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.parentId" type="info" size="small">回复</el-tag>
          <el-tag v-else type="success" size="small">一级</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="replyToUser.nickname" label="被回复者" width="100">
        <template #default="{ row }">{{ row.replyToUser?.nickname || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
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
</style>
