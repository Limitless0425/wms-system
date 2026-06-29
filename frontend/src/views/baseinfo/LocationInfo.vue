<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="仓库">
          <el-select v-model="warehouseFilter" clearable placeholder="按仓库筛选" style="width: 220px" @change="page = 1">
            <el-option v-for="item in warehouses" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button @click="warehouseFilter = null; page = 1">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header">
          <strong>库位管理</strong>
          <div class="actions">
            <el-button type="danger" size="small" :disabled="!selected.length" @click="batchRemove">批量删除</el-button>
            <el-button type="primary" size="small" @click="openLocation()">单独新增库位</el-button>
            <el-button type="success" size="small" @click="openBatchLocation">批量新增库位</el-button>
          </div>
        </div>
      </template>

      <el-table :data="pagedLocations" border stripe empty-text="暂无库位数据" @selection-change="selected = $event">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="warehouseName" label="所属仓库" min-width="160" />
        <el-table-column prop="code" label="库位编号" width="160" />
        <el-table-column prop="name" label="库位名称" min-width="160" />
        <el-table-column prop="capacity" label="容量" width="110" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="openLocation(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeLocation(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="filteredLocations.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>

    <el-dialog v-model="locationVisible" :title="locationForm.id ? '编辑库位' : '单独新增库位'" width="470px">
      <el-form :model="locationForm" label-width="95px">
        <el-form-item label="所属仓库" required>
          <el-select v-model="locationForm.warehouseId" placeholder="选择仓库" style="width: 100%">
            <el-option v-for="item in warehouses" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位编号" required><el-input v-model="locationForm.code" /></el-form-item>
        <el-form-item label="库位名称" required><el-input v-model="locationForm.name" /></el-form-item>
        <el-form-item label="库位容量"><el-input-number v-model="locationForm.capacity" :min="0" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="locationVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLocation">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchVisible" title="批量新增库位" width="500px">
      <el-alert title="系统将按“仓库编号-L001”格式连续编号，并自动跳过已有编号。" type="info" :closable="false" class="batch-tip" />
      <el-form :model="batchForm" label-width="120px">
        <el-form-item label="所属仓库" required>
          <el-select v-model="batchForm.warehouseId" filterable placeholder="选择仓库" style="width: 100%">
            <el-option v-for="item in warehouses" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="新增库位数量"><el-input-number v-model="batchForm.quantity" :min="1" :max="500" style="width: 100%" /></el-form-item>
        <el-form-item label="每个库位容量"><el-input-number v-model="batchForm.capacity" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="增加总容量"><strong>{{ batchForm.quantity * batchForm.capacity }}</strong></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="saveBatchLocations">批量生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addLocation, addLocationsBatch, deleteLocation, getLocations, getWarehouses, updateLocation } from '@/api/baseInfo'

const warehouses = ref([])
const locations = ref([])
const warehouseFilter = ref(null)
const selected = ref([])
const page = ref(1)
const pageSize = ref(10)
const locationVisible = ref(false)
const locationForm = ref({})
const batchVisible = ref(false)
const batchSaving = ref(false)
const batchForm = ref({ warehouseId: null, quantity: 1, capacity: 0 })

const filteredLocations = computed(() => locations.value.filter(item => !warehouseFilter.value || item.warehouseId === warehouseFilter.value))
const pagedLocations = computed(() => filteredLocations.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

onMounted(loadAll)

async function loadAll() {
  const [warehouseRes, locationRes] = await Promise.all([getWarehouses(), getLocations()])
  warehouses.value = warehouseRes.data || []
  locations.value = locationRes.data || []
  selected.value = []
  page.value = 1
}

function openLocation(row = null) {
  locationForm.value = row ? { ...row } : { code: '', name: '', warehouseId: warehouseFilter.value || null, capacity: 0 }
  locationVisible.value = true
}

async function saveLocation() {
  const value = locationForm.value
  if (!value.warehouseId || !value.code || !value.name) return ElMessage.warning('请完整填写仓库、库位编号和名称')
  const warning = capacityWarning(value)
  if (warning) await ElMessageBox.confirm(warning + '，确定继续保存吗？', '容量警告', { type: 'warning' })
  const res = value.id ? await updateLocation(value) : await addLocation(value)
  showLocationResult(res, '库位保存成功')
  locationVisible.value = false
  await loadAll()
}

async function removeLocation(row) {
  await ElMessageBox.confirm(`确定删除库位 ${row.code} 吗？`, '提示', { type: 'warning' })
  const res = await deleteLocation(row.id)
  showLocationResult(res, '库位删除成功')
  await loadAll()
}

async function batchRemove() {
  await ElMessageBox.confirm(`确定批量删除 ${selected.value.length} 个库位吗？`, '批量删除', { type: 'warning' })
  for (const row of selected.value) await deleteLocation(row.id)
  ElMessage.success('批量删除成功')
  await loadAll()
}

function openBatchLocation() {
  batchForm.value = { warehouseId: warehouseFilter.value || null, quantity: 1, capacity: 0 }
  batchVisible.value = true
}

async function saveBatchLocations() {
  if (!batchForm.value.warehouseId) return ElMessage.warning('请选择仓库')
  const warning = batchCapacityWarning()
  if (warning) await ElMessageBox.confirm(warning + '，确定继续生成吗？', '容量警告', { type: 'warning' })
  batchSaving.value = true
  try {
    const res = await addLocationsBatch(batchForm.value)
    showLocationResult(res, '批量新增成功')
    batchVisible.value = false
    await loadAll()
  } finally {
    batchSaving.value = false
  }
}

function capacityWarning(value) {
  const warehouse = warehouses.value.find(item => item.id === value.warehouseId)
  if (!warehouse || !warehouse.capacity) return ''
  const used = locations.value
    .filter(item => item.warehouseId === value.warehouseId && item.id !== value.id)
    .reduce((sum, item) => sum + Number(item.capacity || 0), 0)
  const projected = used + Number(value.capacity || 0)
  if (projected > Number(warehouse.capacity || 0)) {
    return `仓库 ${warehouse.name} 的库位容量合计将达到 ${projected}，超过仓库总容量 ${warehouse.capacity}`
  }
  return ''
}

function batchCapacityWarning() {
  const warehouse = warehouses.value.find(item => item.id === batchForm.value.warehouseId)
  if (!warehouse || !warehouse.capacity) return ''
  const used = locations.value
    .filter(item => item.warehouseId === batchForm.value.warehouseId)
    .reduce((sum, item) => sum + Number(item.capacity || 0), 0)
  const added = Number(batchForm.value.quantity || 0) * Number(batchForm.value.capacity || 0)
  const projected = used + added
  if (projected > Number(warehouse.capacity || 0)) {
    return `仓库 ${warehouse.name} 的库位容量合计将达到 ${projected}，超过仓库总容量 ${warehouse.capacity}`
  }
  return ''
}

function showLocationResult(res, fallback) {
  const warnings = res.warnings || []
  if (warnings.length) ElMessage.warning(res.message || warnings.join('；'))
  else ElMessage.success(res.message || fallback)
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.search-card { margin-bottom: 16px; }
.card-header, .actions { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
.batch-tip { margin-bottom: 18px; }
</style>
