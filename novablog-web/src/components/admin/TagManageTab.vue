<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTagList, createTag, updateTag, deleteTag } from '../../api/tag'

const loading = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  id: null,
  name: ''
})

const rules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { min: 1, max: 20, message: '长度为1-20位', trigger: 'blur' }
  ]
}

const fetch = async () => {
  loading.value = true
  try {
    const res = await getTagList()
    if (res.code === 200) {
      list.value = res.data
    }
  } catch (error) {
    ElMessage.error(error.message || '加载标签列表失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  dialogTitle.value = '新增标签'
  form.id = null
  form.name = ''
  dialogVisible.value = true
}

const openEdit = (row) => {
  dialogTitle.value = '编辑标签'
  form.id = row.id
  form.name = row.name
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const data = { name: form.name.trim() }
    if (form.id) {
      data.id = form.id
    }
    const res = form.id ? await updateTag(data) : await createTag(data)
    if (res.code === 200) {
      ElMessage.success(form.id ? '修改成功' : '创建成功')
      dialogVisible.value = false
      await fetch()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个标签吗？', '删除确认', { type: 'warning' })
    const res = await deleteTag(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await fetch()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(() => {
  fetch()
})
</script>

<template>
  <div v-loading="loading" class="tab-content">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新增标签
      </el-button>
    </div>

    <el-table :data="list" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" min-width="200" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="openEdit(row)">编辑</el-button>
          <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="400px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="20" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
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
</style>
