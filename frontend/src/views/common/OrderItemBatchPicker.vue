<template>
  <div class="page-container">
    <el-card class="header-card">
      <div class="header-row">
        <div>
          <h3>批量添加明细</h3>
          <div class="sub-title">
            {{ typeLabel }}：{{ supplier.code || '' }} {{ supplier.name || '' }}
            <span v-if="recommendedWarehouseName">，推荐仓库：{{ recommendedWarehouseName }}</span>
          </div>
        </div>
        <div>
          <el-button @click="goBack">返回建单界面</el-button>
          <el-button type="primary" @click="confirmAdd">确认添加选中明细</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="零件编号">
          <el-input v-model="search.partCode" clearable placeholder="请输入零件编号" style="width:160px" />
        </el-form-item>
        <el-form-item label="零件名称">
          <el-input v-model="search.partName" clearable placeholder="请输入零件名称" style="width:160px" />
        </el-form-item>
        <el-form-item><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-alert :title="alertTitle" type="info" :closable="false" class="mb-12" />
      <div v-if="!isOutbound" class="batch-storage-bar">
        <span class="muted">已选 {{ selectedRows.length }} 条</span>
        <el-select v-model="batchWarehouseId" filterable placeholder="批量选择仓库" size="small" style="width:190px" @change="onBatchWarehouseChange">
          <el-option v-for="item in warehouses" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
        </el-select>
        <el-select v-model="batchLocationId" filterable placeholder="批量选择库位" size="small" style="width:170px">
          <el-option v-for="item in batchLocationOptions" :key="item.id" :label="`${item.code}（容${item.capacity || 0}）`" :value="item.id" />
        </el-select>
        <el-button type="primary" size="small" :disabled="!selectedRows.length || !batchWarehouseId" @click="applyBatchStorage">应用到选中明细</el-button>
      </div>

      <el-table ref="tableRef" :data="filteredRows" row-key="partId" border stripe height="560" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="48" reserve-selection />
        <el-table-column prop="partCode" label="零件编号" width="130" />
        <el-table-column prop="partName" label="零件名称" min-width="160" />
        <el-table-column prop="spec" label="规格" min-width="120" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column :label="qtyLabel" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.planQty" :min="1" :disabled="(isOutbound || isRepack) && Number(row.availableQty || 0) <= 0" size="small" style="width:120px" />
          </template>
        </el-table-column>
        <el-table-column v-if="isOutbound || isRepack" prop="availableQty" label="可用库存" width="100" />
        <el-table-column v-if="isOutbound" prop="warehouseName" label="仓库" min-width="150" />
        <el-table-column v-if="isOutbound" prop="locationName" label="库位" min-width="150" />
        <el-table-column v-if="isOutbound || isRepack" label="库存提示" width="170">
          <template #default="{ row }"><el-tag :type="stockTipType(row)" size="small">{{ stockTip(row) }}</el-tag></template>
        </el-table-column>
        <el-table-column v-if="!isOutbound" label="仓库" width="190">
          <template #default="{ row }">
            <el-select v-model="row.warehouseId" filterable placeholder="选择仓库" size="small" style="width:165px" @change="onWarehouseChange(row)">
              <el-option v-for="item in warehouses" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="!isOutbound" label="库位" width="160">
          <template #default="{ row }">
            <el-select v-model="row.locationId" filterable placeholder="选择库位" size="small" style="width:135px" @change="syncLocation(row)">
              <el-option v-for="item in locationOptions(row)" :key="item.id" :label="`${item.code}（容${item.capacity || 0}）`" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="!isOutbound" label="器具" width="230">
          <template #default="{ row }">
            <el-select v-model="row.containerId" clearable filterable placeholder="选择器具" size="small" style="width:205px" @change="syncContainer(row)">
              <el-option v-for="item in containerOptions(row)" :key="item.id" :label="containerLabel(item)" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="isRepack" label="推荐器具类型" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.targetContainerType" size="small">{{ row.targetContainerType }}</el-tag>
            <span v-else class="muted">未维护</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isRepack" label="转包容量" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.targetPackageQty" :min="1" size="small" style="width:110px" />
          </template>
        </el-table-column>
        <el-table-column v-if="!isOutbound" label="容量提示" min-width="210">
          <template #default="{ row }">
            <el-tag v-if="capacityTip(row)" :type="capacityTipType(row)" size="small">{{ capacityTip(row) }}</el-tag>
            <span v-else class="muted">未选择器具</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getContainers, getLocations, getParts, getSuppliers, getWarehouses } from '@/api/baseInfo'
import { getInboundSupplierStorage } from '@/api/inbound'
import { getOutboundStockAvailability } from '@/api/outbound'
import { getRepackStockAvailability } from '@/api/operations'

const route = useRoute()
const router = useRouter()
const suppliers = ref([])
const warehouses = ref([])
const locations = ref([])
const containers = ref([])
const rows = ref([])
const selectedRows = ref([])
const stockRows = ref([])
const tableRef = ref(null)
const search = reactive({ partCode: '', partName: '' })
const draft = ref({})
const storagePreference = ref(null)
const batchWarehouseId = ref(null)
const batchLocationId = ref(null)

const supplierId = computed(() => Number(route.query.supplierId))
const draftKey = computed(() => String(route.query.draftKey || 'order-item-draft'))
const returnName = computed(() => String(route.query.returnName || 'InboundOrder'))
const qtyLabel = computed(() => String(route.query.qtyLabel || '数量'))
const typeLabel = computed(() => ({ inbound: '入库单', outbound: '出库单', repack: '转包单' }[route.query.type] || '单据'))
const isOutbound = computed(() => route.query.type === 'outbound')
const isRepack = computed(() => route.query.type === 'repack')
const supplier = computed(() => suppliers.value.find(item => item.id === supplierId.value) || {})
const stockMap = computed(() => new Map(stockRows.value.map(item => [item.partId, item])))
const recommendedWarehouseName = computed(() => storagePreference.value?.recommendedWarehouseName || supplier.value.preferredWarehouseName || '')
const batchLocationOptions = computed(() => locations.value.filter(item => item.warehouseId === batchWarehouseId.value))
const alertTitle = computed(() => {
  if (isOutbound.value) return '勾选需要出库的零件并填写数量；仓库、库位只作为库存位置展示，无需手动选择。'
  if (isRepack.value) return '勾选需要转包的零件并填写数量；系统会按零件维护的转包容量推荐容量匹配的器具，并提前检查可转包库存。'
  return '勾选需要加入单据的零件，并在本页面一次性填写数量、仓库、库位和器具。'
})
const filteredRows = computed(() => rows.value.filter(row => contains(row.partCode, search.partCode) && contains(row.partName, search.partName)))

onMounted(async () => {
  draft.value = JSON.parse(sessionStorage.getItem(draftKey.value) || '{}')
  const [supplierRes, warehouseRes, locationRes, containerRes, partRes, stockRes] = await Promise.all([
    getSuppliers(),
    getWarehouses(),
    getLocations(),
    getContainers(),
    getParts(supplierId.value),
    isOutbound.value
      ? getOutboundStockAvailability({ supplierId: supplierId.value })
      : isRepack.value
        ? getRepackStockAvailability({ supplierId: supplierId.value })
        : Promise.resolve({ data: [] })
  ])
  suppliers.value = supplierRes.data || []
  warehouses.value = warehouseRes.data || []
  locations.value = locationRes.data || []
  containers.value = containerRes.data || []
  stockRows.value = stockRes.data || []
  if (route.query.type === 'inbound') {
    storagePreference.value = (await getInboundSupplierStorage(supplierId.value)).data
  }
  const existingMap = new Map((draft.value.items || []).map(item => [item.partId, item]))
  rows.value = (partRes.data || []).map(part => buildRow(part, existingMap.get(part.id)))
  await nextTick()
  rows.value.forEach(row => {
    if (existingMap.has(row.partId)) tableRef.value?.toggleRowSelection(row, true)
  })
})

function buildRow(part, existing) {
  const warehouse = preferredWarehouse()
  const location = preferredLocation(warehouse)
  const container = preferredContainer(part)
  const stock = stockMap.value.get(part.id) || {}
  const row = {
    partId: part.id,
    partCode: part.code,
    partName: part.name,
    code: part.code,
    name: part.name,
    spec: part.spec,
    unit: part.unit,
    planQty: isRepack.value && part.originalPackageQty ? part.originalPackageQty : 1,
    warehouseId: warehouse?.id || null,
    warehouseName: warehouse?.name || stock.warehouseName || '',
    locationId: location.id || null,
    locationName: location.code || stock.locationName || '',
    containerId: container?.id || null,
    containerCode: container?.code || '',
    containerName: container?.name || '',
    targetContainerType: part.repackContainerType || container?.type || '',
    originalPackageQty: part.originalPackageQty || null,
    targetPackageQty: part.targetPackageQty || container?.capacity || null,
    availableQty: stock.availableQty || 0
  }
  if (isOutbound.value) {
    row.warehouseId = null
    row.locationId = null
    row.containerId = null
    row.containerCode = ''
    row.containerName = ''
  }
  return existing ? { ...row, ...existing } : row
}

function preferredWarehouse() {
  return warehouses.value.find(item => item.id === storagePreference.value?.recommendedWarehouseId)
    || warehouses.value.find(item => item.id === supplier.value.preferredWarehouseId)
    || warehouses.value[0]
    || null
}

function preferredLocation(warehouse) {
  if (!warehouse?.id) return {}
  return locations.value.find(item => item.id === storagePreference.value?.recommendedLocationId && item.warehouseId === warehouse.id)
    || locations.value.find(item => item.warehouseId === warehouse.id)
    || {}
}

function preferredContainer(part) {
  const supplierCode = supplier.value.code || ''
  const targetCapacity = Number(part.targetPackageQty || 0)
  const candidates = containers.value.filter(container => {
    const supplierOk = !container.supplierCode || !supplierCode || container.supplierCode.toLowerCase() === supplierCode.toLowerCase()
    const partOk = !container.partCode || container.partCode.toLowerCase() === part.code.toLowerCase()
    return supplierOk && partOk
  })
  const typed = part.repackContainerType
    ? candidates.filter(container => String(container.type || '').toLowerCase() === String(part.repackContainerType).toLowerCase())
    : candidates
  return typed.find(container => targetCapacity > 0 && Number(container.capacity || 0) === targetCapacity)
    || candidates.find(container => targetCapacity > 0 && Number(container.capacity || 0) === targetCapacity)
    || typed[0]
    || candidates[0]
    || null
}

function locationOptions(row) {
  return locations.value.filter(item => item.warehouseId === row.warehouseId)
}

function containerOptions(row) {
  const supplierCode = supplier.value.code || ''
  const targetCapacity = Number(row.targetPackageQty || 0)
  const candidates = containers.value.filter(container => {
    const supplierOk = !container.supplierCode || !supplierCode || container.supplierCode.toLowerCase() === supplierCode.toLowerCase()
    const partOk = !container.partCode || !row.partCode || container.partCode.toLowerCase() === row.partCode.toLowerCase()
    return supplierOk && partOk
  })
  if (!isRepack.value || targetCapacity <= 0) return candidates
  return [...candidates].sort((a, b) => (Number(b.capacity || 0) === targetCapacity) - (Number(a.capacity || 0) === targetCapacity))
}

function onWarehouseChange(row) {
  const warehouse = warehouses.value.find(item => item.id === row.warehouseId) || {}
  const location = locationOptions(row)[0] || {}
  row.warehouseName = warehouse.name || ''
  row.locationId = location.id || null
  row.locationName = location.code || ''
}

function onBatchWarehouseChange() {
  const location = batchLocationOptions.value[0] || {}
  batchLocationId.value = location.id || null
}

function applyBatchStorage() {
  if (!selectedRows.value.length) return ElMessage.warning('请先勾选明细')
  const warehouse = warehouses.value.find(item => item.id === batchWarehouseId.value)
  if (!warehouse) return ElMessage.warning('请选择仓库')
  const location = locations.value.find(item => item.id === batchLocationId.value && item.warehouseId === warehouse.id)
  selectedRows.value.forEach(row => {
    row.warehouseId = warehouse.id
    row.warehouseName = warehouse.name || ''
    row.locationId = location?.id || null
    row.locationName = location?.code || ''
  })
  ElMessage.success(`已应用到 ${selectedRows.value.length} 条明细`)
}

function syncLocation(row) {
  const location = locations.value.find(item => item.id === row.locationId) || {}
  row.locationName = location.code || ''
}

function syncContainer(row) {
  const container = containers.value.find(item => item.id === row.containerId) || {}
  row.containerCode = container.code || ''
  row.containerName = container.name || ''
  row.targetContainerType = row.targetContainerType || container.type || ''
}

function containerLabel(container) {
  return `${container.code} ${container.name || ''}（容量${container.capacity || '-'}）`
}

function capacityTip(row) {
  const container = containers.value.find(item => item.id === row.containerId)
  const capacity = Number((isRepack.value && row.targetPackageQty) || container?.capacity || 0)
  if (!capacity) return ''
  const qty = Number(row.planQty || 0)
  const boxes = Math.ceil(qty / capacity)
  const remainder = qty % capacity
  if (remainder === 0) return `共 ${boxes} 个器具，刚好装满`
  return `需 ${boxes} 个器具，最后一个未装满（余 ${remainder}/${capacity}）`
}

function capacityTipType(row) {
  const container = containers.value.find(item => item.id === row.containerId)
  const capacity = Number((isRepack.value && row.targetPackageQty) || container?.capacity || 0)
  if (!capacity) return 'info'
  return Number(row.planQty || 0) % capacity === 0 ? 'success' : 'warning'
}

function stockTip(row) {
  const available = Number(row.availableQty || 0)
  const qty = Number(row.planQty || 0)
  if (available <= 0) return '无库存'
  if (qty > available) return `库存不足，可用 ${available}`
  return '库存充足'
}

function stockTipType(row) {
  const available = Number(row.availableQty || 0)
  const qty = Number(row.planQty || 0)
  if (available <= 0) return 'danger'
  if (qty > available) return 'warning'
  return 'success'
}

function confirmAdd() {
  if (!selectedRows.value.length) return ElMessage.warning('请选择需要添加的明细')
  if (isOutbound.value || isRepack.value) {
    const invalid = selectedRows.value.find(item => Number(item.availableQty || 0) <= 0 || Number(item.planQty || 0) > Number(item.availableQty || 0))
    if (invalid) return ElMessage.warning(`零件 ${invalid.partCode} 库存不足，请调整数量`)
  }
  const items = selectedRows.value.filter(item => item.partId && Number(item.planQty || 0) > 0).map(item => ({ ...item }))
  if (!items.length) return ElMessage.warning('请填写有效数量')
  sessionStorage.setItem(draftKey.value, JSON.stringify({ ...draft.value, items }))
  router.push({ name: returnName.value, query: { restoreKey: draftKey.value } })
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function goBack() {
  router.push({ name: returnName.value, query: { restoreKey: draftKey.value } })
}

function resetSearch() {
  search.partCode = ''
  search.partName = ''
}

function contains(value, keyword) {
  return !keyword || String(value || '').toLowerCase().includes(String(keyword).trim().toLowerCase())
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.header-card, .search-card { margin-bottom: 16px; }
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.header-row h3 { margin: 0 0 6px; }
.sub-title, .muted { color: #909399; font-size: 13px; }
.mb-12 { margin-bottom: 12px; }
.batch-storage-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
</style>
