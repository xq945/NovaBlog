<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyFavorites } from '../api/favorite'
import { useFormatTime } from '../composables/useFormatTime'

const router = useRouter()
const { formatTime } = useFormatTime()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getMyFavorites({ page: page.value, size: size.value })
    if (res.code === 200) {
      list.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    ElMessage.error(error.message || '加载收藏失败')
  } finally {
    loading.value = false
  }
}

const goArticle = (id) => {
  router.push(`/article/${id}`)
}

const goAuthor = (id) => {
  if (id) router.push(`/user/${id}`)
}

const handlePageChange = (value) => {
  page.value = value
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="favorites-page">
    <main class="page-main">
      <div class="page-header">
        <h1 class="page-title">我的收藏</h1>
        <span class="page-count">共 {{ total }} 篇</span>
      </div>

      <div v-loading="loading">
        <div v-if="list.length > 0" class="favorite-list">
          <div
            v-for="item in list"
            :key="item.id"
            class="favorite-item"
            @click="goArticle(item.id)"
          >
            <h3 class="favorite-title">{{ item.title }}</h3>
            <p class="favorite-summary">{{ item.summary || '暂无摘要' }}</p>
            <div class="favorite-meta">
              <span
                v-if="item.author?.id"
                class="author"
                @click.stop="goAuthor(item.author.id)"
              >
                {{ item.author.nickname || '匿名' }}
              </span>
              <span>{{ formatTime(item.createTime) }}</span>
              <span>{{ item.viewCount || 0 }} 浏览</span>
              <span>{{ item.likeCount || 0 }} 赞</span>
              <span>{{ item.favoriteCount || 0 }} 收藏</span>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="还没有收藏任何文章" />
      </div>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
.favorites-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

.page-main {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 20px;
}

.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  color: var(--nb-text-primary);
  font-size: 1.5rem;
  font-weight: 600;
}

.page-count {
  color: var(--nb-text-muted);
  font-size: 14px;
}

.favorite-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.favorite-item {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.favorite-item:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

.favorite-title {
  margin: 0 0 8px;
  color: var(--nb-text-primary);
  font-size: 1.1rem;
  font-weight: 600;
}

.favorite-summary {
  margin: 0 0 12px;
  color: var(--nb-text-muted);
  font-size: 14px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.favorite-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--nb-text-muted);
  font-size: 13px;
}

.favorite-meta .author {
  color: var(--nb-accent);
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
