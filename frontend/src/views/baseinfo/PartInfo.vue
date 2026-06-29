<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="零件编号">
          <el-input v-model="search.partCode" clearable placeholder="请输入零件编号" style="width: 150px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="零件名称">
          <el-input v-model="search.partName" clearable placeholder="请输入零件名称" style="width: 150px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="search.supplier" clearable filterable placeholder="选择供应商" style="width: 190px" @change="load">
            <el-option v-for="item in suppliers" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.name" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="openDialog()">新增零件</el-button>
          <el-button type="danger" :disabled="!selected.length" @click="batchRemove">批量删除</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="pagedParts" border stripe @selection-change="selected = $event">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="code" label="零件编号" width="120" />
        <el-table-column prop="name" label="零件名称" min-width="150" />
        <el-table-column prop="spec" label="规格" width="130" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="customerBarcode" label="客户条码" width="140" />
        <el-table-column prop="lowStock" label="低储" width="80" />
        <el-table-column prop="highStock" label="高储" width="80" />
        <el-table-column prop="originalPackageQty" label="原包装容量" width="105" />
        <el-table-column prop="targetPackageQty" label="转包容量" width="95" />
        <el-table-column prop="repackContainerType" label="转包器具类型" width="130" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="parts.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑零件' : '新增零件'" width="760px">
      <el-collapse style="margin-bottom:10px">
        <el-collapse-item title="批量导入零件（粘贴文本）">
          <el-alert
            title="格式：零件编号,零件名称,规格,单位,供应商代码或名称,低储,高储,客户条码,原包装容量,转包容量,转包器具类型"
            type="info"
            :closable="false"
            class="mb-8"
          />
          <el-input
            v-model="batchText"
            type="textarea"
            :rows="5"
            placeholder="示例：
PT100,新零件A,标准型,个,SUP001,50,500,BAR100,100,20,转包器具
PT101,新零件B,加强型,个,佛山博世汽车部件,80,800,BAR101,120,30,转包器具"
          />
          <el-button type="primary" size="small" style="margin-top:6px" @click="parseBatchParts">解析并批量添加</el-button>
        </el-collapse-item>
      </el-collapse>

      <div class="excel-tip">
        <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" @change="handleExcelUpload">
          <el-button type="primary" size="small" plain>导入Excel表格</el-button>
        </el-upload>
        <span>表头需包含：零件编号、零件名称、规格、单位、供应商、低储、高储、客户条码、原包装容量、转包容量、转包器具类型</span>
      </div>

      <el-form label-width="110px">
        <el-form-item label="零件编号" required><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="零件名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" /></el-form-item>
        <el-form-item label="单位" required><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="供应商" required>
          <el-select v-model="form.supplierId" filterable placeholder="选择" style="width: 100%">
            <el-option v-for="item in suppliers" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户条码"><el-input v-model="form.customerBarcode" placeholder="扫码出库时用于防错比对" /></el-form-item>
        <el-form-item label="低储阈值"><el-input-number v-model="form.lowStock" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="高储阈值"><el-input-number v-model="form.highStock" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="原包装容量" required><el-input-number v-model="form.originalPackageQty" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="转包容量" required><el-input-number v-model="form.targetPackageQty" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="转包器具类型" required>
          <el-select v-model="form.repackContainerType" filterable allow-create placeholder="选择或输入" style="width: 100%">
            <el-option v-for="item in containerTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addPart, batchAddParts, deletePart, getContainers, getParts, getSuppliers, updatePart } from '@/api/baseInfo'
import * as XLSX from 'xlsx'

const parts = ref([])
const suppliers = ref([])
const containers = ref([])
const selected = ref([])
const page = ref(1)
const pageSize = ref(10)
const search = ref({ partCode: '', partName: '', supplier: '' })
const dialogVisible = ref(false)
const batchText = ref('')
const form = ref({})

const containerTypes = computed(() => [...new Set(containers.value.map(item => item.type).filter(Boolean))])
const pagedParts = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return parts.value.slice(start, start + pageSize.value)
})

onMounted(async () => {
  suppliers.value = (await getSuppliers()).data || []
  containers.value = (await getContainers()).data || []
  await load()
})

async function load() {
  parts.value = (await getParts(search.value)).data || []
  page.value = 1
  selected.value = []
}

async function resetSearch() {
  search.value = { partCode: '', partName: '', supplier: '' }
  await load()
}

function openDialog(row) {
  form.value = row ? { ...row } : {
    code: '',
    name: '',
    spec: '',
    unit: '个',
    supplierId: null,
    customerBarcode: '',
    lowStock: 50,
    highStock: 500,
    originalPackageQty: 100,
    targetPackageQty: 20,
    repackContainerType: '转包器具'
  }
  dialogVisible.value = true
}

function validatePart(item) {
  if (!item.code || !item.name || !item.supplierId) return '请完整填写零件编号、名称和供应商'
  if (!item.unit) return '请填写单位'
  if (Number(item.highStock || 0) > 0 && Number(item.highStock || 0) <= Number(item.lowStock || 0)) return '高储阈值必须大于低储阈值'
  if (Number(item.originalPackageQty || 0) <= 0) return '请填写原包装容量'
  if (Number(item.targetPackageQty || 0) <= 0) return '请填写转包容量'
  if (!item.repackContainerType) return '请填写转包器具类型'
  if (Number(item.targetPackageQty) > Number(item.originalPackageQty)) return '转包容量不能大于原包装容量'
  return ''
}

async function save() {
  const error = validatePart(form.value)
  if (error) return ElMessage.warning(error)
  if (form.value.id) await updatePart(form.value)
  else await addPart(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function parseBatchParts() {
  if (!batchText.value.trim()) return
  const rows = batchText.value.trim().split('\n').filter(line => line.trim()).map(line => line.split(',').map(value => value.trim()))
  await importRows(rows)
  batchText.value = ''
}

async function handleExcelUpload(file) {
  try {
    const data = await file.raw.arrayBuffer()
    const wb = XLSX.read(data, { type: 'array' })
    const ws = wb.Sheets[wb.SheetNames[0]]
    const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' })
    if (rows.length < 2) return ElMessage.warning('Excel至少需要表头和一行数据')
    const headers = rows[0].map(value => String(value || '').trim())
    const bodyRows = rows.slice(1)
      .filter(row => row.some(cell => String(cell || '').trim()))
      .map(row => normalizeExcelRow(headers, row))
    await importRows(bodyRows)
  } catch (e) {
    ElMessage.error('Excel解析失败：' + e.message)
  }
}

function normalizeExcelRow(headers, row) {
  const get = patterns => {
    const index = headers.findIndex(header => patterns.some(pattern => pattern.test(header)))
    return index >= 0 ? String(row[index] ?? '').trim() : ''
  }
  return [
    get([/零件编号/, /零件号/, /编码/, /partCode/i]),
    get([/零件名称/, /零件名/, /名称/, /partName/i]),
    get([/规格/, /spec/i]),
    get([/单位/, /unit/i]),
    get([/供应商/, /supplier/i]),
    get([/低储/, /lowStock/i]),
    get([/高储/, /highStock/i]),
    get([/客户条码/, /条码/, /barcode/i]),
    get([/原包装容量/, /原包装/, /original/i]),
    get([/转包容量/, /targetPackage/, /repack.*capacity/i]),
    get([/转包器具类型/, /器具类型/, /containerType/i])
  ]
}

async function importRows(rows) {
  const data = []
  const errors = []
  rows.forEach((row, index) => {
    const item = rowToPart(row)
    const error = validatePart(item)
    if (error) errors.push(`第 ${index + 1} 行：${error}`)
    else data.push(item)
  })
  if (errors.length) return ElMessage.warning(errors.slice(0, 3).join('；'))
  if (!data.length) return ElMessage.warning('没有可导入的零件数据')
  const res = await batchAddParts(data)
  const result = res.data || {}
  const count = result.count || data.length
  const skipped = result.errors || []
  if (skipped.length) ElMessage.warning(`成功导入 ${count} 条，跳过 ${skipped.length} 条`)
  else ElMessage.success(`成功导入 ${count} 条零件`)
  await load()
}

function rowToPart(row) {
  const supplierText = row[4] || ''
  const supplier = suppliers.value.find(item => item.code === supplierText || item.name === supplierText)
  return {
    code: row[0] || '',
    name: row[1] || '',
    spec: row[2] || '',
    unit: row[3] || '个',
    supplierId: supplier?.id || null,
    lowStock: toInt(row[5], 50),
    highStock: toInt(row[6], 500),
    customerBarcode: row[7] || '',
    originalPackageQty: toInt(row[8], 100),
    targetPackageQty: toInt(row[9], 20),
    repackContainerType: row[10] || '转包器具'
  }
}

function toInt(value, fallback) {
  const parsed = parseInt(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除零件 ${row.code} 吗？`, '提示', { type: 'warning' })
  await deletePart(row.id)
  ElMessage.success('删除成功')
  await load()
}

async function batchRemove() {
  await ElMessageBox.confirm(`确定批量删除 ${selected.value.length} 个零件吗？`, '批量删除', { type: 'warning' })
  for (const row of selected.value) await deletePart(row.id)
  ElMessage.success('批量删除成功')
  await load()
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.search-card { margin-bottom: 16px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
.excel-tip { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; color: #909399; font-size: 12px; }
.mb-8 { margin-bottom: 8px; }
</style>
