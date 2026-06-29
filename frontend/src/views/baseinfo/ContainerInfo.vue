<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="零件号"><el-input v-model="search.partCode" placeholder="请输入零件号" clearable style="width:140px" /></el-form-item>
        <el-form-item label="供应商代码"><el-input v-model="search.supplierCode" placeholder="请输入供应商代码" clearable style="width:150px" /></el-form-item>
        <el-form-item label="车型"><el-input v-model="search.vehicleModel" placeholder="请输入车型" clearable style="width:140px" /></el-form-item>
        <el-form-item label="器具类型">
          <el-select v-model="search.type" clearable placeholder="选择" style="width:140px">
            <el-option label="普通器具" value="普通器具" />
            <el-option label="转包器具" value="转包器具" />
          </el-select>
        </el-form-item>
        <el-form-item label="器具代码"><el-input v-model="search.code" placeholder="请输入器具代码" clearable style="width:140px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadData">查询</el-button><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>器具管理</span>
          <div>
            <el-button type="success" size="small" plain @click="importVisible = true">批量导入</el-button>
            <el-button type="danger" size="small" :disabled="!selected.length" @click="handleBatchDelete">批量删除</el-button>
            <el-button type="primary" size="small" @click="openDialog(null)">新增器具</el-button>
          </div>
        </div>
      </template>

      <el-table :data="pagedContainers" border stripe empty-text="暂无器具数据" @selection-change="selected = $event">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="code" label="器具编号" width="120" />
        <el-table-column prop="supplierCode" label="供应商代码" width="120" />
        <el-table-column prop="partCode" label="零件号" width="110" />
        <el-table-column prop="vehicleModel" label="车型" width="110" />
        <el-table-column prop="name" label="器具名称" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === '普通器具' ? 'primary' : 'warning'">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="spec" label="规格" width="160" />
        <el-table-column prop="capacity" label="单箱容量" width="90" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="containers.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>

    <el-dialog :title="isEdit ? '编辑器具' : '新增器具'" v-model="dialogVisible" width="520px">
      <el-form :model="form" label-width="105px">
        <el-form-item label="器具编号" required><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="器具名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="供应商代码"><el-input v-model="form.supplierCode" /></el-form-item>
        <el-form-item label="零件号"><el-input v-model="form.partCode" /></el-form-item>
        <el-form-item label="车型"><el-input v-model="form.vehicleModel" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" placeholder="选择" style="width:100%">
            <el-option label="普通器具" value="普通器具" />
            <el-option label="转包器具" value="转包器具" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" placeholder="如 600x400x300mm" /></el-form-item>
        <el-form-item label="单箱容量" required><el-input-number v-model="form.capacity" :min="1" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="批量导入器具" width="780px">
      <el-alert title="文本格式：器具编号,器具名称,类型,规格,单箱容量,供应商代码,零件号,车型" type="info" :closable="false" class="import-tip" />
      <el-input v-model="batchText" type="textarea" :rows="7" placeholder="CTN100,标准周转箱,普通器具,600x400x300mm,50,SUP001,PT001,车型A" />
      <div class="import-actions">
        <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" @change="handleExcelUpload">
          <el-button type="success" plain>识别Excel表格</el-button>
        </el-upload>
        <span class="muted">Excel表头：器具编号、器具名称、类型、规格、单箱容量、供应商代码、零件号、车型</span>
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
import { addContainer, batchAddContainers, deleteContainer, getContainers, updateContainer } from '@/api/baseInfo'

const containers = ref([])
const selected = ref([])
const page = ref(1)
const pageSize = ref(10)
const search = ref({ partCode: '', supplierCode: '', vehicleModel: '', type: '', code: '' })
const dialogVisible = ref(false)
const importVisible = ref(false)
const isEdit = ref(false)
const batchText = ref('')
const form = ref(emptyForm())
const pagedContainers = computed(() => containers.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

onMounted(loadData)

function emptyForm() {
  return { code: '', name: '', supplierCode: '', partCode: '', vehicleModel: '', type: '普通器具', spec: '', capacity: 1 }
}

async function loadData() {
  containers.value = (await getContainers(search.value)).data || []
  page.value = 1
  selected.value = []
}

async function resetSearch() {
  search.value = { partCode: '', supplierCode: '', vehicleModel: '', type: '', code: '' }
  await loadData()
}

function openDialog(row) {
  isEdit.value = !!row
  form.value = row ? { ...row } : emptyForm()
  dialogVisible.value = true
}

async function handleSave() {
  if (isEdit.value) await updateContainer(form.value)
  else await addContainer(form.value)
  ElMessage.success(isEdit.value ? '修改成功' : '添加成功')
  dialogVisible.value = false
  await loadData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确定删除该器具吗？', '提示', { type: 'warning' })
  await deleteContainer(id)
  ElMessage.success('删除成功')
  await loadData()
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定批量删除 ${selected.value.length} 个器具吗？`, '批量删除', { type: 'warning' })
  for (const row of selected.value) await deleteContainer(row.id)
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
      pick(row, headers, /器具编号|器具代码|编号|code/i),
      pick(row, headers, /器具名称|名称|name/i),
      pick(row, headers, /器具类型|类型|type/i),
      pick(row, headers, /规格|spec/i),
      pick(row, headers, /容量|单箱容量|capacity/i),
      pick(row, headers, /供应商代码|supplier/i),
      pick(row, headers, /零件号|零件编号|part/i),
      pick(row, headers, /车型|vehicle/i)
    ]))
  } catch (e) {
    ElMessage.error('Excel解析失败：' + e.message)
  }
}

async function importRows(rows) {
  const data = rows.filter(row => row[0] || row[1]).map(row => ({
    code: row[0] || '',
    name: row[1] || '',
    type: row[2] || '普通器具',
    spec: row[3] || '',
    capacity: Number(row[4] || 0),
    supplierCode: row[5] || '',
    partCode: row[6] || '',
    vehicleModel: row[7] || ''
  }))
  if (!data.length) return ElMessage.warning('没有可导入的器具数据')
  const res = await batchAddContainers(data)
  const errors = res.data?.errors || []
  if (errors.length) ElMessage.warning(`成功导入 ${res.data?.count || 0} 条，跳过 ${errors.length} 条`)
  else ElMessage.success(`成功导入 ${res.data?.count || 0} 条器具`)
  importVisible.value = false
  await loadData()
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
