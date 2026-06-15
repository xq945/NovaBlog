<script setup>
import { ref } from 'vue'
import AdminLayout from '../components/admin/AdminLayout.vue'
import UserManageTab from '../components/admin/UserManageTab.vue'
import ArticleManageTab from '../components/admin/ArticleManageTab.vue'
import CommentManageTab from '../components/admin/CommentManageTab.vue'
import CategoryManageTab from '../components/admin/CategoryManageTab.vue'
import TagManageTab from '../components/admin/TagManageTab.vue'

const activeTab = ref('users')

const tabs = [
  { name: 'users', label: '用户管理', component: UserManageTab },
  { name: 'articles', label: '文章管理', component: ArticleManageTab },
  { name: 'comments', label: '评论管理', component: CommentManageTab },
  { name: 'categories', label: '分类管理', component: CategoryManageTab },
  { name: 'tags', label: '标签管理', component: TagManageTab }
]
</script>

<template>
  <AdminLayout>
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane
        v-for="tab in tabs"
        :key="tab.name"
        :label="tab.label"
        :name="tab.name"
      >
        <component :is="tab.component" />
      </el-tab-pane>
    </el-tabs>
  </AdminLayout>
</template>
