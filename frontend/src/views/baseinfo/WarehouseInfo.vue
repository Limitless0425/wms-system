<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <strong>仓库管理</strong>
          <div class="actions">
            <el-button type="success" size="small" plain @click="importVisible = true">批量导入</el-button>
            <el-button type="danger" size="small" :disabled="!selectedWarehouses.length" @click="batchRemoveWarehouse">批量删除</el-button>
            <el-button type="primary" size="small" @click="openWarehouse()">新增仓库</el-button>
          </div>
        </div>
      </template>
      <el-table :data="pagedWarehouses" border stripe empty-text="暂无仓库数据" @selection-change="selectedWarehouses = $event">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="code" label="仓库编号" width="120" />
        <el-table-column prop="name" label="仓库名称" />
        <el-table-column prop="area" label="区域" width="130" />
        <el-table-column prop="capacity" label="仓库总容量" width="120" />
        <el-table-column prop="locationCapacity" label="库位容量合计" width="130" />
        <el-table-column prop="remainingCapacity" label="剩余容量" width="110">
          <template #default="{ row }">
            <el-tag :type="row.overCapacity ? 'danger' : 'success'">{{ row.remainingCapacity ?? 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.overCapacity ? 'danger' : 'success'">{{ row.overCapacity ? '超出容量' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="openWarehouse(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeWarehouse(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="warehousePage" v-model:page-size="warehousePageSize" :page-sizes="[10,20,50,100]" :total="warehouses.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>

    <el-dialog v-model="warehouseVisible" :title="warehouseForm.id ? '编辑仓库' : '新增仓库'" width="430px">
      <el-form :model="warehouseForm" label-width="105px">
        <el-form-item label="仓库编号" required><el-input v-model="warehouseForm.code" /></el-form-item>
        <el-form-item label="仓库名称" required><el-input v-model="warehouseForm.name" /></el-form-item>
        <el-form-item label="区域"><el-input v-model="warehouseForm.area" /></el-form-item>
        <el-form-item label="仓库总容量" required>
          <el-input-number v-model="warehouseForm.capacity" :min="0" style="width:100%" />
        </el-form-item>
        <el-alert v-if="warehouseForm.id && projectedOverCapacity" title="当前库位容量合计已超过这个总容量，保存后仓库会显示超出容量警告。" type="warning" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="warehouseVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWarehouse">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="批量导入仓库" width="740px">
      <el-alert title="文本格式：仓库编号,仓库名称,区域,仓库总容量" type="info" :closable="false" class="import-tip" />
      <el-input v-model="batchText" type="textarea" :rows="7" placeholder="WH100,新仓库A,华南区,5000" />
      <div class="import-actions">
        <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" @change="handleExcelUpload">
          <el-button type="success" plain>识别Excel表格</el-button>
        </el-upload>
        <span class="muted">Excel表头：仓库编号、仓库名称、区域、仓库总容量</span>
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
import { addWarehouse, batchAddWarehouses, deleteWarehouse, getWarehouses, updateWarehouse } from '@/api/baseInfo'

const warehouses = ref([])
const selectedWarehouses = ref([])
const warehousePage = ref(1)
const warehousePageSize = ref(10)
const warehouseVisible = ref(false)
const importVisible = ref(false)
const batchText = ref('')
const warehouseForm = ref({})
const pagedWarehouses = computed(() => warehouses.value.slice((warehousePage.value - 1) * warehousePageSize.value, warehousePage.value * warehousePageSize.value))
const projectedOverCapacity = computed(() => {
  const used = Number(warehouseForm.value.locationCapacity || 0)
  const capacity = Number(warehouseForm.value.capacity || 0)
  return capacity > 0 && used > capacity
})

onMounted(loadAll)

async function loadAll() {
  warehouses.value = (await getWarehouses()).data || []
  selectedWarehouses.value = []
  warehousePage.value = 1
}

function openWarehouse(row = null) {
  warehouseForm.value = row ? { ...row } : { code: '', name: '', area: '', capacity: 0 }
  warehouseVisible.value = true
}

async function saveWarehouse() {
  if (!warehouseForm.value.code || !warehouseForm.value.name) return ElMessage.warning('请填写仓库编号和名称')
  if (projectedOverCapacity.value) {
    await ElMessageBox.confirm('仓库总容量小于当前库位容量合计，保存后会显示超出容量警告，确定继续吗？', '容量警告', { type: 'warning' })
  }
  if (warehouseForm.value.id) await updateWarehouse(warehouseForm.value)
  else await addWarehouse(warehouseForm.value)
  ElMessage.success('仓库保存成功')
  warehouseVisible.value = false
  await loadAll()
}

async function removeWarehouse(row) {
  await ElMessageBox.confirm(`确定删除仓库 ${row.name} 吗？`, '提示', { type: 'warning' })
  await deleteWarehouse(row.id)
  ElMessage.success('仓库删除成功')
  await loadAll()
}

async function batchRemoveWarehouse() {
  await ElMessageBox.confirm(`确定批量删除 ${selectedWarehouses.value.length} 个仓库吗？`, '批量删除', { type: 'warning' })
  for (const row of selectedWarehouses.value) await deleteWarehouse(row.id)
  ElMessage.success('批量删除成功')
  await loadAll()
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
      pick(row, headers, /仓库编号|编号|code/i),
      pick(row, headers, /仓库名称|名称|name/i),
      pick(row, headers, /区域|area/i),
      pick(row, headers, /仓库总容量|总容量|容量|capacity/i)
    ]))
  } catch (e) {
    ElMessage.error('Excel解析失败：' + e.message)
  }
}

async function importRows(rows) {
  const data = rows.filter(row => row[0] || row[1]).map(row => ({
    code: row[0] || '',
    name: row[1] || '',
    area: row[2] || '',
    capacity: Number(row[3] || 0)
  }))
  if (!data.length) return ElMessage.warning('没有可导入的仓库数据')
  const res = await batchAddWarehouses(data)
  const errors = res.data?.errors || []
  if (errors.length) ElMessage.warning(`成功导入 ${res.data?.count || 0} 条，跳过 ${errors.length} 条`)
  else ElMessage.success(`成功导入 ${res.data?.count || 0} 条仓库`)
  importVisible.value = false
  await loadAll()
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
.card-header, .actions { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
.import-tip { margin-bottom: 12px; }
.import-actions { display: flex; gap: 12px; align-items: center; margin-top: 12px; }
.muted { color: #909399; font-size: 13px; }
</style>
