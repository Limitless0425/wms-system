<template>
  <div class="page-container">
    <el-card style="margin-bottom:16px">
      <el-form :inline="true">
        <el-form-item label="用户名称"><el-input v-model="searchForm.username" placeholder="请填写用户名称" clearable style="width:150px" @keyup.enter="load" /></el-form-item>
        <el-form-item label="手机号码"><el-input v-model="searchForm.phone" placeholder="请填写手机号码" clearable style="width:150px" @keyup.enter="load" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width:130px" @change="load">
            <el-option label="启用" value="enabled" /><el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="searchForm.createTimeRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width:260px" @change="load" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div class="header">
          <strong>用户管理</strong>
          <div>
            <el-button type="danger" :disabled="!selected.length" @click="batchRemove">批量删除</el-button>
            <el-button type="primary" @click="openDialog()">新增用户</el-button>
          </div>
        </div>
      </template>
      <el-table :data="pagedUsers" border stripe @selection-change="selected = $event">
        <el-table-column type="selection" width="45" :selectable="row => row.username !== 'admin'" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="角色" width="150">
          <template #default="{ row }">{{ roleName(row.role) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'danger'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link :disabled="row.username === 'admin'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="users.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>

    <el-dialog v-model="visible" :title="form.id ? '编辑用户' : '新增用户'" width="500px">
      <el-form label-width="90px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="Boolean(form.id)" placeholder="请填写用户名" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.displayName" placeholder="请填写姓名" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" placeholder="请填写手机号" /></el-form-item>
        <el-form-item :label="form.id ? '重置密码' : '密码'">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空则不修改' : '请输入登录密码'" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" placeholder="选择" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.name + '（' + role.code + '）'" :value="role.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addUser, deleteUser, getRoles, getUsers, updateUser } from '@/api/system'

const users = ref([])
const roles = ref([])
const selected = ref([])
const page = ref(1)
const pageSize = ref(10)
const visible = ref(false)
const form = ref({})
const searchForm = reactive({ username: '', phone: '', status: '', createTimeRange: [] })
const pagedUsers = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return users.value.slice(start, start + pageSize.value)
})

onMounted(load)
async function load() {
  const params = { ...searchForm }
  if (params.createTimeRange && params.createTimeRange.length === 2) {
    params.createTimeStart = params.createTimeRange[0]
    params.createTimeEnd = params.createTimeRange[1]
  } else {
    params.createTimeStart = ''
    params.createTimeEnd = ''
  }
  delete params.createTimeRange
  const [userRes, roleRes] = await Promise.all([getUsers(params), getRoles()])
  users.value = userRes.data
  roles.value = roleRes.data
  page.value = 1
  selected.value = []
}

function resetSearch() {
  searchForm.username = ''
  searchForm.phone = ''
  searchForm.status = ''
  searchForm.createTimeRange = []
  load()
}

function roleName(code) {
  const r = roles.value.find(x => x.code === code)
  return r ? r.name : code
}

function openDialog(row) {
  form.value = row ? { ...row, password: '' } : { username: '', displayName: '', phone: '', password: '', role: '', enabled: true }
  visible.value = true
}

async function save() {
  if (!form.value.username || !form.value.role) return ElMessage.warning('用户名和角色不能为空')
  if (!form.value.id && !form.value.password) return ElMessage.warning('密码不能为空')
  const fn = form.value.id ? updateUser : addUser
  await fn(form.value)
  ElMessage.success(form.value.id ? '修改成功' : '创建成功')
  visible.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm('确定删除用户 ' + row.username + '？', '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
}
async function batchRemove() {
  const rows = selected.value.filter(row => row.username !== 'admin')
  if (!rows.length) return ElMessage.warning('请选择可删除的用户')
  await ElMessageBox.confirm(`确定批量删除 ${rows.length} 个用户吗？`, '批量删除', { type: 'warning' })
  for (const row of rows) await deleteUser(row.id)
  ElMessage.success('批量删除成功')
  await load()
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
