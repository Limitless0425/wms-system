<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="header">
          <strong>角色权限</strong>
          <div>
            <el-button type="danger" :disabled="!selected.length" @click="batchRemove">批量删除</el-button>
            <el-button type="primary" @click="openDialog()">新增角色</el-button>
          </div>
        </div>
      </template>

      <el-table :data="pagedRoles" border stripe @selection-change="selected = $event">
        <el-table-column type="selection" width="45" :selectable="row => !protectedRoles.includes(row.code)" />
        <el-table-column prop="code" label="角色编码" width="150" />
        <el-table-column prop="name" label="角色名称" width="170" />
        <el-table-column label="功能权限">
          <template #default="{ row }">
            <el-tag v-if="row.permissions === '*'" type="success">全部权限</el-tag>
            <span v-else>{{ permissionNames(row.permissions) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link :disabled="protectedRoles.includes(row.code)" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="roles.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </el-card>

    <el-dialog v-model="visible" :title="form.id ? '编辑角色' : '新增角色'" width="720px">
      <el-form label-width="90px">
        <el-form-item label="角色编码">
          <el-input v-model="form.code" :disabled="Boolean(form.id)" placeholder="例如 INSPECTOR" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="功能权限">
          <el-checkbox v-model="allPermissions" @change="toggleAll">全部权限</el-checkbox>
          <el-checkbox-group v-model="selectedPermissions" class="permission-grid" :disabled="allPermissions">
            <el-checkbox v-for="item in permissionOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addRole, deleteRole, getRoles, updateRole } from '@/api/system'

const protectedRoles = ['ADMIN', 'USER', 'AI_ADMIN']
const permissionOptions = [
  { value: 'ai.admin', label: 'AI仓库管理员' },
  { value: 'inbound.order', label: '入库单管理' },
  { value: 'kanban.manage', label: '看板管理' },
  { value: 'inbound.kanban', label: '入库看板兼容权限' },
  { value: 'inbound.scan', label: '扫码入库' },
  { value: 'outbound.order', label: '出库单管理' },
  { value: 'outbound.scan', label: '扫码出库' },
  { value: 'operations.repack', label: '转包作业' },
  { value: 'baseinfo.supplier', label: '供应商管理' },
  { value: 'baseinfo.customer', label: '客户管理' },
  { value: 'baseinfo.part', label: '零件管理' },
  { value: 'baseinfo.warehouse', label: '仓库管理' },
  { value: 'baseinfo.location', label: '库位管理' },
  { value: 'baseinfo.container', label: '器具管理' },
  { value: 'inventory.stock', label: '库存监控' },
  { value: 'inventory.report', label: '总库存报表' },
  { value: 'inventory.trace', label: '库存追溯' },
  { value: 'system.user', label: '用户管理' },
  { value: 'system.role', label: '角色权限' }
]

const roles = ref([])
const selected = ref([])
const page = ref(1)
const pageSize = ref(10)
const visible = ref(false)
const form = ref({})
const selectedPermissions = ref([])
const allPermissions = ref(false)

const pagedRoles = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return roles.value.slice(start, start + pageSize.value)
})

onMounted(load)

async function load() {
  roles.value = (await getRoles()).data || []
  page.value = 1
  selected.value = []
}

function openDialog(row) {
  form.value = row ? { ...row } : { code: '', name: '', remark: '', permissions: '' }
  allPermissions.value = row?.permissions === '*'
  selectedPermissions.value = row && row.permissions !== '*' && row.permissions ? row.permissions.split(',') : []
  visible.value = true
}

function toggleAll(value) {
  if (value) selectedPermissions.value = []
}

async function save() {
  if (!form.value.code || !form.value.name) return ElMessage.warning('请填写角色编码和名称')
  form.value.permissions = allPermissions.value ? '*' : selectedPermissions.value.join(',')
  if (form.value.id) await updateRole(form.value)
  else await addRole(form.value)
  ElMessage.success('角色保存成功，重新登录后菜单权限生效')
  visible.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除角色 ${row.name} 吗？`, '提示', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('角色已删除')
  await load()
}

async function batchRemove() {
  const rows = selected.value.filter(row => !protectedRoles.includes(row.code))
  if (!rows.length) return ElMessage.warning('请选择可删除的角色')
  await ElMessageBox.confirm(`确定批量删除 ${rows.length} 个角色吗？`, '批量删除', { type: 'warning' })
  for (const row of rows) await deleteRole(row.id)
  ElMessage.success('批量删除成功')
  await load()
}

function permissionNames(value) {
  if (!value) return '无菜单权限'
  const values = value.split(',')
  const names = permissionOptions.filter(item => values.includes(item.value)).map(item => item.label)
  return names.length ? names.join('、') : value
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.header { display: flex; align-items: center; justify-content: space-between; }
.permission-grid { display: grid; grid-template-columns: repeat(3, 1fr); width: 100%; margin-top: 8px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
