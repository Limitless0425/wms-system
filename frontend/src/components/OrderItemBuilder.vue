<template>
  <div class="order-item-builder">
    <div class="builder-toolbar">
      <div class="toolbar-left">
        <span class="hint">{{ builderHint }}</span>
        <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" @change="handleExcelUpload">
          <el-button type="success" size="small" plain :disabled="!supplierId">导入 Excel</el-button>
        </el-upload>
        <el-button type="warning" size="small" plain :disabled="!supplierId" @click="textImportVisible = true">粘贴文本</el-button>
      </div>
      <el-button type="primary" :disabled="!supplierId" @click="$emit('pick')">批量添加</el-button>
    </div>

    <el-dialog v-model="textImportVisible" title="粘贴文本导入" width="640px" append-to-body>
      <el-alert title="一行一条明细，格式：零件编号或名称,数量,仓库编号或名称,库位编号,器具编号。出库单可只填零件和数量。" type="info" :closable="false" class="mb-12" />
      <el-input v-model="batchText" type="textarea" :rows="8" placeholder="PT001,200,WH01,A-01-01,CTN001" />
      <template #footer>
        <el-button @click="textImportVisible = false">取消</el-button>
        <el-button type="primary" @click="parseTextImport">解析并添加</el-button>
      </template>
    </el-dialog>

    <el-table :data="items" border stripe empty-text="暂无明细，请点击右上角批量添加" max-height="360">
      <el-table-column prop="partCode" label="零件编号" width="120" />
      <el-table-column prop="partName" label="零件名称" min-width="150" />
      <el-table-column prop="spec" label="规格" width="110" />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column :label="qtyLabel" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.planQty" :min="1" :disabled="isOutbound && Number(row.availableQty || 0) <= 0" size="small" style="width:120px" @change="emitItems" />
        </template>
      </el-table-column>
      <el-table-column v-if="isOutbound" prop="availableQty" label="可用库存" width="100" />
      <el-table-column v-if="isOutbound" prop="warehouseName" label="仓库" min-width="130" />
      <el-table-column v-if="isOutbound" prop="locationName" label="库位" min-width="130" />
      <el-table-column v-if="isOutbound" label="库存提示" width="160">
        <template #default="{ row }"><el-tag :type="stockTipType(row)" size="small">{{ stockTip(row) }}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="!isOutbound" label="仓库" width="180">
        <template #default="{ row }">
          <el-select v-model="row.warehouseId" filterable placeholder="选择仓库" size="small" style="width:155px" @change="onWarehouseChange(row)">
            <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="`${warehouse.code} ${warehouse.name}`" :value="warehouse.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column v-if="!isOutbound" label="库位" width="160">
        <template #default="{ row }">
          <el-select v-model="row.locationId" filterable placeholder="选择库位" size="small" style="width:135px" @change="onLocationChange(row)">
            <el-option v-for="location in locationOptions(row)" :key="location.id" :label="location.code" :value="location.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column v-if="!isOutbound" label="首选器具" width="220">
        <template #default="{ row }">
          <el-select v-model="row.containerId" clearable filterable placeholder="选择器具" size="small" style="width:195px" @change="onContainerChange(row)">
            <el-option v-for="container in containerOptions(row)" :key="container.id" :label="containerLabel(container)" :value="container.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column v-if="isRepack" label="转包容量" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.targetPackageQty" :min="1" size="small" style="width:100px" @change="emitItems" />
        </template>
      </el-table-column>
      <el-table-column v-if="!isOutbound" label="容量提示" min-width="180">
        <template #default="{ row }">
          <el-tag v-if="capacityTip(row)" :type="capacityTipType(row)" size="small">{{ capacityTip(row) }}</el-tag>
          <span v-else class="muted">未选择器具</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ $index }">
          <el-button type="danger" link @click="removeItem($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import * as XLSX from 'xlsx'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  supplierId: { type: [Number, String], default: null },
  suppliers: { type: Array, default: () => [] },
  parts: { type: Array, default: () => [] },
  warehouses: { type: Array, default: () => [] },
  locations: { type: Array, default: () => [] },
  containers: { type: Array, default: () => [] },
  qtyLabel: { type: String, default: '数量' },
  mode: { type: String, default: 'inbound' }
})
const emit = defineEmits(['update:modelValue', 'pick'])

const items = ref([])
const textImportVisible = ref(false)
const batchText = ref('')
const isOutbound = computed(() => props.mode === 'outbound')
const isRepack = computed(() => props.mode === 'repack')
const builderHint = computed(() => {
  if (!props.supplierId) return '请先选择供应商'
  if (isOutbound.value) return '请进入批量添加页面选择零件和数量，仓库、库位按库存位置展示'
  return '请进入批量添加页面选择零件、数量、仓库、库位和器具'
})

watch(() => props.modelValue, value => {
  items.value = (value || []).map(item => ({ ...item }))
}, { immediate: true, deep: true })

function locationOptions(row) {
  return props.locations.filter(location => location.warehouseId === row.warehouseId)
}

function containerOptions(row) {
  const supplier = props.suppliers.find(item => item.id === props.supplierId) || {}
  const supplierCode = supplier.code || ''
  const targetCapacity = Number(row.targetPackageQty || 0)
  const candidates = props.containers.filter(container => {
    const supplierOk = !container.supplierCode || !supplierCode || container.supplierCode.toLowerCase() === supplierCode.toLowerCase()
    const partOk = !container.partCode || !row.partCode || container.partCode.toLowerCase() === row.partCode.toLowerCase()
    return supplierOk && partOk
  })
  if (isRepack.value && targetCapacity > 0) {
    return [...candidates].sort((a, b) => (Number(b.capacity || 0) === targetCapacity) - (Number(a.capacity || 0) === targetCapacity))
  }
  return candidates
}

function containerLabel(container) {
  return `${container.code} ${container.name || ''}（容量${container.capacity || '-'}）`
}

function onWarehouseChange(row) {
  const warehouse = props.warehouses.find(item => item.id === row.warehouseId) || {}
  const location = locationOptions(row)[0] || {}
  row.warehouseName = warehouse.name || ''
  row.locationId = location.id || null
  row.locationName = location.code || ''
  emitItems()
}

function onLocationChange(row) {
  const location = props.locations.find(item => item.id === row.locationId) || {}
  row.locationName = location.code || ''
  emitItems()
}

function onContainerChange(row) {
  const container = props.containers.find(item => item.id === row.containerId) || {}
  row.containerCode = container.code || ''
  row.containerName = container.name || ''
  row.targetContainerType = row.targetContainerType || container.type || ''
  emitItems()
}

function capacityTip(row) {
  const container = props.containers.find(item => item.id === row.containerId)
  const capacity = Number((isRepack.value && row.targetPackageQty) || container?.capacity || 0)
  if (!capacity) return ''
  const qty = Number(row.planQty || 0)
  const boxes = Math.ceil(qty / capacity)
  const remainder = qty % capacity
  if (remainder === 0) return `共 ${boxes} 个器具，刚好装满`
  return `需 ${boxes} 个器具，最后一个未装满（余 ${remainder}/${capacity}）`
}

function capacityTipType(row) {
  const container = props.containers.find(item => item.id === row.containerId)
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

function removeItem(index) {
  items.value.splice(index, 1)
  emitItems()
}

function parseTextImport() {
  if (!batchText.value.trim()) return
  const lines = batchText.value.trim().split('\n').filter(line => line.trim())
  let added = 0
  for (const line of lines) {
    const [keyword, qty, warehouseKey, locationKey, containerKey] = line.split(',').map(item => item.trim())
    const part = props.parts.find(item => item.code === keyword || item.name === keyword)
    if (!part) continue
    const row = buildRow(part, Number(qty || 1))
    applyWarehouse(row, warehouseKey)
    applyLocation(row, locationKey)
    applyContainer(row, containerKey)
    upsertRow(row)
    added += 1
  }
  batchText.value = ''
  textImportVisible.value = false
  emitItems()
  ElMessage.success(`已导入 ${added} 条明细`)
}

async function handleExcelUpload(file) {
  const data = await file.raw.arrayBuffer()
  const workbook = XLSX.read(data)
  const sheet = workbook.Sheets[workbook.SheetNames[0]]
  const rows = XLSX.utils.sheet_to_json(sheet, { defval: '' })
  let added = 0
  for (const raw of rows) {
    const keyword = raw.零件编号 || raw.零件号 || raw.零件名称 || raw.partCode || raw.code
    const part = props.parts.find(item => item.code === keyword || item.name === keyword)
    if (!part) continue
    const row = buildRow(part, Number(raw.数量 || raw.planQty || 1))
    applyWarehouse(row, raw.仓库 || raw.仓库编号 || raw.warehouse)
    applyLocation(row, raw.库位 || raw.库位编号 || raw.location)
    applyContainer(row, raw.器具 || raw.器具编号 || raw.container)
    upsertRow(row)
    added += 1
  }
  emitItems()
  ElMessage.success(`已识别 ${added} 条明细`)
}

function buildRow(part, qty) {
  return {
    partId: part.id,
    partCode: part.code,
    partName: part.name,
    code: part.code,
    name: part.name,
    spec: part.spec,
    unit: part.unit,
    planQty: qty > 0 ? qty : 1,
    warehouseId: null,
    warehouseName: '',
    locationId: null,
    locationName: '',
    containerId: null,
    containerCode: '',
    containerName: '',
    targetContainerType: part.repackContainerType || '',
    targetPackageQty: part.targetPackageQty || null
  }
}

function applyWarehouse(row, keyword) {
  const warehouse = props.warehouses.find(item => item.code === keyword || item.name === keyword)
  if (!warehouse) return
  row.warehouseId = warehouse.id
  row.warehouseName = warehouse.name || ''
}

function applyLocation(row, keyword) {
  const location = props.locations.find(item => item.code === keyword && (!row.warehouseId || item.warehouseId === row.warehouseId))
  if (!location) return
  row.locationId = location.id
  row.locationName = location.code || ''
}

function applyContainer(row, keyword) {
  const container = props.containers.find(item => item.code === keyword || item.name === keyword)
  if (!container) return
  row.containerId = container.id
  row.containerCode = container.code || ''
  row.containerName = container.name || ''
}

function upsertRow(row) {
  const index = items.value.findIndex(item => item.partId === row.partId)
  if (index >= 0) items.value[index] = { ...items.value[index], ...row }
  else items.value.push(row)
}

function emitItems() {
  emit('update:modelValue', items.value.map(item => ({ ...item })))
}
</script>

<style scoped>
.order-item-builder { width: 100%; }
.builder-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 10px; }
.toolbar-left { display: flex; align-items: center; gap: 10px; }
.hint, .muted { color: #909399; font-size: 13px; }
.mb-12 { margin-bottom: 12px; }
</style>
