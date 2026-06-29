<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="编号"><el-input v-model="search.code" placeholder="请输入编号" clearable style="width:130px" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="search.name" placeholder="请输入名称" clearable style="width:150px" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="search.address" placeholder="请输入地址" clearable style="width:160px" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="search.phone" placeholder="请输入手机号" clearable style="width:140px" /></el-form-item>
        <el-form-item label="座机号"><el-input v-model="search.landline" placeholder="请输入座机号" clearable style="width:140px" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="search.contact" placeholder="请输入联系人" clearable style="width:120px" /></el-form-item>
        <el-form-item label="级别"><el-input v-model="search.level" placeholder="请输入级别" clearable style="width:100px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadData">查询</el-button><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>供应商管理</span>
          <div>
            <el-button type="success" size="small" plain @click="importVisible = true">批量导入</el-button>
            <el-button type="danger" size="small" :disabled="!selected.length" @click="handleBatchDelete">批量删除</el-button>
            <el-button type="primary" size="small" @click="openDialog(null)">新增供应商</el-button>
          </div>
        </div>
      </template>
      <el-table :data="pagedSuppliers" border stripe empty-text="暂无供应商数据" @selection-change="selected = $event">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="code" label="供应商编号" width="120" />
        <el-table-column prop="name" label="供应商名称" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="landline" label="座机号" width="130" />
        <el-table-column prop="level" label="级别" width="80" />
        <el-table-column prop="preferredWarehouseName" label="首选仓库" width="150" />
        <el-table-column prop="address" label="地址" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="suppliers.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>

    <el-dialog :title="isEdit ? '编辑供应商' : '新增供应商'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="供应商编号" required><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="供应商名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="座机号"><el-input v-model="form.landline" /></el-form-item>
        <el-form-item label="级别"><el-input v-model="form.level" placeholder="如 A/B/C" /></el-form-item>
        <el-form-item label="首选仓库">
          <el-select v-model="form.preferredWarehouseId" clearable filterable placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="item in warehouses" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="批量导入供应商" width="760px">
      <el-alert title="文本格式：供应商编号,供应商名称,联系人,手机号,座机号,级别,首选仓库编号或名称,地址" type="info" :closable="false" class="import-tip" />
      <el-input
        v-model="batchText"
        type="textarea"
        :rows="7"
        placeholder="SUP100,广州某某供应商,张三,13800000000,020-88888888,A,WH01,广州市..."
      />
      <div class="import-actions">
        <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" @change="handleExcelUpload">
          <el-button type="success" plain>识别Excel表格</el-button>
        </el-upload>
        <span class="muted">Excel表头：供应商编号、供应商名称、联系人、手机号、座机号、级别、首选仓库、地址</span>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="parseTextImport">解析并导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import * as XLSX from 'xlsx'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addSupplier, batchAddSuppliers, deleteSupplier, getSuppliers, getWarehouses, updateSupplier
} from '@/api/baseInfo'

const suppliers = ref([])
const warehouses = ref([])
const selected = ref([])
const page = ref(1)
const pageSize = ref(10)
const search = ref({ code: '', name: '', address: '', phone: '', landline: '', contact: '', level: '' })
const dialogVisible = ref(false)
const importVisible = ref(false)
const isEdit = ref(false)
const batchText = ref('')
const form = ref(emptyForm())
const pagedSuppliers = computed(() => suppliers.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

onMounted(async () => {
  warehouses.value = (await getWarehouses()).data || []
  await loadData()
})

function emptyForm() {
  return { code: '', name: '', contact: '', phone: '', landline: '', level: '', preferredWarehouseId: null, address: '' }
}

async function loadData() {
  suppliers.value = (await getSuppliers(search.value)).data || []
  page.value = 1
  selected.value = []
}

async function resetSearch() {
  search.value = { code: '', name: '', address: '', phone: '', landline: '', contact: '', level: '' }
  await loadData()
}

function openDialog(row) {
  isEdit.value = !!row
  form.value = row ? { ...row } : emptyForm()
  dialogVisible.value = true
}

async function handleSave() {
  if (isEdit.value) await updateSupplier(form.value)
  else await addSupplier(form.value)
  ElMessage.success(isEdit.value ? '修改成功' : '添加成功')
  dialogVisible.value = false
  await loadData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确定删除该供应商吗？', '提示', { type: 'warning' })
  await deleteSupplier(id)
  ElMessage.success('删除成功')
  await loadData()
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定批量删除 ${selected.value.length} 个供应商吗？`, '批量删除', { type: 'warning' })
  for (const row of selected.value) await deleteSupplier(row.id)
  ElMessage.success('批量删除成功')
  await loadData()
}

async function parseTextImport() {
  const rows = batchText.value.trim().split('\n').filter(Boolean).map(line => line.split(',').map(v => v.trim()))
  await importRows(rows)
  batchText.value = ''
}

async function handleExcelUpload(file) {
  try {
    const rows = await readExcelRows(file)
    const headers = rows[0].map(v => String(v || '').trim())
    await importRows(rows.slice(1).map(row => [
      pick(row, headers, /供应商编号|编号|code/i),
      pick(row, headers, /供应商名称|名称|name/i),
      pick(row, headers, /联系人|contact/i),
      pick(row, headers, /手机号|手机|phone/i),
      pick(row, headers, /座机号|座机|landline/i),
      pick(row, headers, /级别|level/i),
      pick(row, headers, /首选仓库|仓库|warehouse/i),
      pick(row, headers, /地址|address/i)
    ]))
  } catch (e) {
    ElMessage.error('Excel解析失败：' + e.message)
  }
}

async function importRows(rows) {
  const data = rows.filter(row => row[0] || row[1]).map(row => ({
    code: row[0] || '',
    name: row[1] || '',
    contact: row[2] || '',
    phone: row[3] || '',
    landline: row[4] || '',
    level: row[5] || '',
    preferredWarehouseId: warehouseId(row[6]),
    address: row[7] || ''
  }))
  if (!data.length) return ElMessage.warning('没有可导入的供应商数据')
  const res = await batchAddSuppliers(data)
  const errors = res.data?.errors || []
  if (errors.length) ElMessage.warning(`成功导入 ${res.data?.count || 0} 条，跳过 ${errors.length} 条`)
  else ElMessage.success(`成功导入 ${res.data?.count || 0} 条供应商`)
  importVisible.value = false
  await loadData()
}

function warehouseId(value) {
  if (!value) return null
  const text = String(value).trim()
  return warehouses.value.find(w => w.code === text || w.name === text || `${w.code} ${w.name}` === text)?.id || null
}

async function readExcelRows(file) {
  const data = await file.raw.arrayBuffer()
  const wb = XLSX.read(data, { type: 'array' })
  const ws = wb.Sheets[wb.SheetNames[0]]
  const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' })
  if (rows.length < 2) throw new Error('至少需要表头和一行数据')
  return rows
}

function pick(row, headers, pattern) {
  const index = headers.findIndex(header => pattern.test(header))
  return index >= 0 ? String(row[index] || '').trim() : ''
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.search-card { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
.import-tip { margin-bottom: 12px; }
.import-actions { display: flex; gap: 12px; align-items: center; margin-top: 12px; }
.muted { color: #909399; font-size: 13px; }
</style>
