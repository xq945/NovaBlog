<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserProfile, getUserArticles } from '../api/user'
import { followUser, unfollowUser } from '../api/follow'
import { useUserStore } from '../stores'
import { useFormatTime } from '../composables/useFormatTime'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { formatTime } = useFormatTime()

const profile = ref(null)
const loading = ref(false)

const articles = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const articleLoading = ref(false)

const followLoading = ref(false)

const isSelf = computed(() =>
  !!userStore.userInfo?.id && userStore.userInfo.id === profile.value?.id
)

const fetchProfile = async () => {
  loading.value = true
  try {
    const res = await getUserProfile(route.params.id)
    if (res.code === 200) {
      profile.value = res.data
    } else {
      ElMessage.error(res.message || '用户不存在')
    }
  } catch (error) {
    ElMessage.error(error.message || '加载用户信息失败')
  } finally {
    loading.value = false
  }
}

const fetchArticles = async () => {
  articleLoading.value = true
  try {
    const res = await getUserArticles(route.params.id, { page: page.value, size: size.value })
    if (res.code === 200) {
      articles.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    ElMessage.error(error.message || '加载文章失败')
  } finally {
    articleLoading.value = false
  }
}

const toggleFollow = async () => {
  if (!userStore.userInfo) {
    router.push('/login')
    return
  }
  if (isSelf.value) return

  followLoading.value = true
  try {
    const res = profile.value.followed
      ? await unfollowUser(profile.value.id)
      : await followUser(profile.value.id)
    if (res.code === 200) {
      // 以后端返回的权威状态为准
      profile.value.followed = res.data.followed
      profile.value.followerCount = res.data.followerCount
      ElMessage.success(res.data.followed ? '已关注' : '已取消关注')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}

const goArticle = (id) => {
  router.push(`/article/${id}`)
}

const handlePageChange = (value) => {
  page.value = value
  fetchArticles()
}

onMounted(() => {
  fetchProfile()
  fetchArticles()
})

// 从文章作者跳到另一个作者时组件会被复用，需要按路由参数重新拉取
watch(() => route.params.id, () => {
  page.value = 1
  fetchProfile()
  fetchArticles()
})
</script>

<template>
  <div class="user-profile-page">
    <main class="profile-main">
      <div class="profile-card" v-loading="loading">
        <template v-if="profile">
          <el-avatar :size="80" :src="profile.avatar">
            {{ (profile.nickname || profile.username || '?').charAt(0).toUpperCase() }}
          </el-avatar>

          <div class="profile-info">
            <h2 class="nickname">{{ profile.nickname || profile.username }}</h2>
            <p class="handle">@{{ profile.username }}</p>
            <p class="bio">{{ profile.bio || '这个人很懒，还没有写简介' }}</p>
            <div class="stats">
              <span><strong>{{ profile.articleCount || 0 }}</strong> 文章</span>
              <span><strong>{{ profile.followerCount || 0 }}</strong> 粉丝</span>
              <span><strong>{{ profile.followingCount || 0 }}</strong> 关注</span>
            </div>
          </div>

          <div class="profile-action">
            <el-button v-if="isSelf" disabled>这是你自己</el-button>
            <el-button
              v-else-if="profile.followed"
              :loading="followLoading"
              @click="toggleFollow"
            >
              已关注
            </el-button>
            <el-button
              v-else
              type="primary"
              :loading="followLoading"
              @click="toggleFollow"
            >
              关注
            </el-button>
          </div>
        </template>
        <el-empty v-else-if="!loading" description="用户不存在" />
      </div>

      <section class="article-section">
        <h2 class="section-title">TA 的文章</h2>

        <div v-loading="articleLoading">
          <div v-if="articles.length > 0" class="article-list">
            <div
              v-for="item in articles"
              :key="item.id"
              class="article-item"
              @click="goArticle(item.id)"
            >
              <h3 class="article-title">{{ item.title }}</h3>
              <p class="article-summary">{{ item.summary || '暂无摘要' }}</p>
              <div class="article-meta">
                <span>{{ formatTime(item.createTime) }}</span>
                <span>{{ item.viewCount || 0 }} 浏览</span>
                <span>{{ item.likeCount || 0 }} 赞</span>
              </div>
            </div>
          </div>
          <el-empty v-else-if="!articleLoading" description="还没有发布文章" />
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
      </section>
    </main>
  </div>
</template>

<style scoped>
.user-profile-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

.profile-main {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 20px;
}

.profile-card {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 32px;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.nickname {
  margin: 0 0 4px;
  color: var(--nb-text-primary);
  font-size: 1.5rem;
  font-weight: 600;
}

.handle {
  margin: 0 0 12px;
  color: var(--nb-text-muted);
  font-size: 14px;
}

.bio {
  margin: 0 0 16px;
  color: var(--nb-text-secondary);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.stats {
  display: flex;
  gap: 24px;
  color: var(--nb-text-muted);
  font-size: 14px;
}

.stats strong {
  color: var(--nb-text-primary);
  font-size: 1.1rem;
  margin-right: 4px;
}

.profile-action {
  flex-shrink: 0;
}

.section-title {
  margin: 0 0 16px;
  color: var(--nb-text-primary);
  font-size: 1.25rem;
  font-weight: 600;
}

.article-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.article-item {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.article-item:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

.article-title {
  margin: 0 0 8px;
  color: var(--nb-text-primary);
  font-size: 1.1rem;
  font-weight: 600;
}

.article-summary {
  margin: 0 0 12px;
  color: var(--nb-text-muted);
  font-size: 14px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-meta {
  display: flex;
  gap: 16px;
  color: var(--nb-text-muted);
  font-size: 13px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
