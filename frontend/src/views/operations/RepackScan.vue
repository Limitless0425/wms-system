<template>
  <div class="page-container">
    <el-card>
      <template #header><strong>转包作业</strong></template>
      <div class="scan-area">
        <el-input v-model="kanbanNo" placeholder="扫描或输入看板号（支持入库/出库/转包看板）" size="large" class="scan-input" @keyup.enter="queryKanban" />
        <el-button type="primary" size="large" @click="queryKanban">查询看板</el-button>
        <el-button type="success" size="large" @click="openCamera"><el-icon><Camera /></el-icon>摄像头扫码</el-button>
      </div>
      <div class="scan-tip">可扫描未入库看板、已入库看板、未出库看板、转包看板，确认数量后执行转包。</div>
    </el-card>

    <el-card v-if="kanbanInfo" class="info-card">
      <template #header>
        <div class="card-header">
          <strong>看板详细信息</strong>
          <div>
            <el-tag :type="statusType">{{ statusLabel }}</el-tag>
            <el-tag v-if="kanbanType" type="info" style="margin-left:8px">{{ kanbanType }}</el-tag>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="看板号">{{ kanbanInfo.kanbanNo }}</el-descriptions-item>
        <el-descriptions-item label="关联单号">{{ kanbanInfo.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="零件编号">{{ kanbanInfo.partCode }}</el-descriptions-item>
        <el-descriptions-item label="零件名称">{{ kanbanInfo.partName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ kanbanInfo.qty || kanbanInfo.actualQty }} {{ kanbanInfo.unit }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ kanbanInfo.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源仓库">{{ kanbanInfo.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源库位">{{ kanbanInfo.locationName || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="kanbanInfo.sourceBusinessType" label="来源类型">{{ sourceTypeLabel(kanbanInfo.sourceBusinessType) }}</el-descriptions-item>
        <el-descriptions-item v-if="kanbanInfo.sourceKanbanNo" label="来源看板">{{ kanbanInfo.sourceKanbanNo }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <el-form :inline="true" label-width="80px" class="repack-form">
        <el-form-item label="目标仓库">
          <el-select v-model="targetWarehouseId" placeholder="请选择目标仓库" style="width:180px" @change="onWarehouseChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="`${w.code} ${w.name}`" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标库位">
          <el-select v-model="targetLocationId" placeholder="请选择目标库位" style="width:180px">
            <el-option v-for="l in filteredLocations" :key="l.id" :label="l.code" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标器具">
          <el-select v-model="targetContainerCode" placeholder="请选择目标器具" style="width:220px">
            <el-option v-for="c in containers" :key="c.id" :label="containerLabel(c)" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-tag v-if="recommendedContainerText" type="success">{{ recommendedContainerText }}</el-tag>
      </el-form>

      <div v-if="canRepack" class="scan-actions">
        <el-form :inline="true">
          <el-form-item label="转包数量"><el-input-number v-model="scanQty" :min="1" :max="maxRepackQty" /></el-form-item>
          <el-form-item><el-button type="success" size="large" @click="confirmRepack" :loading="scanning">确认转包</el-button></el-form-item>
        </el-form>
        <div v-if="maxRepackQty > 0" class="muted">最大可转包数量：{{ maxRepackQty }}</div>
      </div>
      <el-alert v-else :title="cantRepackReason" type="warning" show-icon :closable="false" class="status-alert" />
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="card-header">
          <strong>未转包记录</strong>
          <div>
            <el-button type="success" size="small" :disabled="!selectedPending.length" @click="batchRepackPending">批量转包</el-button>
            <el-button type="primary" link @click="loadLists">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="pendingRepackKanbans" border stripe max-height="320" empty-text="暂无未转包记录" @selection-change="selectedPending = $event" @row-click="selectPendingKanban">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="kanbanNo" label="转包看板号" width="220" />
        <el-table-column prop="orderNo" label="转包单号" width="180" />
        <el-table-column label="来源类型" width="100"><template #default="{ row }">{{ sourceTypeLabel(row.sourceBusinessType) }}</template></el-table-column>
        <el-table-column prop="partCode" label="零件号" width="110" />
        <el-table-column prop="partName" label="零件名称" min-width="120" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="supplierName" label="供应商" min-width="140" />
        <el-table-column prop="warehouseName" label="仓库" min-width="120" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button size="small" type="success" @click.stop="quickRepack(row)">转包</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="card-header">
          <strong>最近转包记录</strong>
          <el-button type="primary" link @click="loadLists">刷新</el-button>
        </div>
      </template>
      <el-table :data="recentRepackRecords" border stripe max-height="300" empty-text="暂无转包记录">
        <el-table-column prop="kanbanNo" label="看板号" width="220" />
        <el-table-column prop="orderNo" label="转包单号" width="180" />
        <el-table-column prop="partCode" label="零件号" width="110" />
        <el-table-column prop="partName" label="零件名称" min-width="120" />
        <el-table-column prop="actualQty" label="实际数量" width="90" />
        <el-table-column label="结余" width="80">
          <template #default="{ row }"><el-tag v-if="row.balanceQty > 0" type="warning" size="small">{{ row.balanceQty }}</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="130" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="repackStatusType(row.status)" size="small">{{ repackStatusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="repackTime" label="转包时间" width="170" />
      </el-table>
    </el-card>

    <el-dialog v-model="cameraVisible" title="摄像头扫码" width="540px" destroy-on-close @closed="stopCamera">
      <el-alert title="请允许浏览器使用摄像头，并将看板二维码完整放入扫描框。" type="info" :closable="false" class="camera-tip" />
      <div id="repack-camera-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
      <template #footer><el-button @click="cameraVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { getPendingRepackKanbans, getRecentRepackRecords, lookupKanban, universalRepackScan } from '@/api/operations'
import { getContainers, getLocations, getParts, getSuppliers, getWarehouses } from '@/api/baseInfo'

const kanbanNo = ref('')
const kanbanInfo = ref(null)
const kanbanType = ref('')
const scanQty = ref(0)
const scanning = ref(false)
const pendingRepackKanbans = ref([])
const selectedPending = ref([])
const recentRepackRecords = ref([])
const cameraVisible = ref(false)
const cameraError = ref('')
let scanner = null
let scanHandled = false

const targetWarehouseId = ref(null)
const targetLocationId = ref(null)
const targetContainerCode = ref('')
const warehouses = ref([])
const locations = ref([])
const containers = ref([])
const parts = ref([])
const suppliers = ref([])

const filteredLocations = computed(() => !targetWarehouseId.value ? locations.value : locations.value.filter(l => l.warehouseId === targetWarehouseId.value))
const maxRepackQty = computed(() => kanbanInfo.value ? (kanbanInfo.value.qty || kanbanInfo.value.actualQty || 0) : 0)
const recommendedContainerText = computed(() => {
  const container = containers.value.find(item => item.code === targetContainerCode.value)
  return container ? `推荐器具：${container.code} ${container.name || ''}（容量 ${container.capacity || '-'}）` : ''
})

const canRepack = computed(() => {
  if (!kanbanInfo.value) return false
  if (kanbanType.value === '转包看板') return kanbanInfo.value.status === 'PRINTED'
  if (kanbanType.value === '入库看板') return ['PRINTED', 'SCANNED'].includes(kanbanInfo.value.status) && !kanbanInfo.value.sealed
  if (kanbanType.value === '出库看板') return kanbanInfo.value.status === 'PRINTED'
  return false
})

const cantRepackReason = computed(() => {
  if (!kanbanInfo.value) return ''
  if (kanbanType.value === '入库看板' && kanbanInfo.value.sealed) return '该看板已封存，无法转包'
  return '该看板当前状态不允许转包'
})

const statusType = computed(() => {
  if (!kanbanInfo.value) return 'info'
  if (kanbanInfo.value.sealed) return 'danger'
  return { PRINTED: 'warning', SCANNED: 'success', OUTBOUND: 'info', REPACKED: 'primary', VOIDED: 'danger', REPACK_INBOUND: 'success', REPACK_OUTBOUND: 'primary' }[kanbanInfo.value.status] || 'info'
})

const statusLabel = computed(() => {
  if (!kanbanInfo.value) return ''
  if (kanbanInfo.value.sealed) return '已封存'
  return repackStatusLabel(kanbanInfo.value.status)
})

onMounted(() => { loadLists(); loadBaseInfo() })
onBeforeUnmount(stopCamera)

async function loadBaseInfo() {
  const [whRes, locRes, conRes, partRes, supplierRes] = await Promise.all([getWarehouses(), getLocations(), getContainers(), getParts(), getSuppliers()])
  warehouses.value = whRes.data || []
  locations.value = locRes.data || []
  containers.value = conRes.data || []
  parts.value = partRes.data || []
  suppliers.value = supplierRes.data || []
}

async function loadLists() {
  try {
    const [pendingRes, recentRes] = await Promise.all([getPendingRepackKanbans(), getRecentRepackRecords()])
    pendingRepackKanbans.value = pendingRes.data || []
    recentRepackRecords.value = recentRes.data || []
    selectedPending.value = []
  } catch {
    pendingRepackKanbans.value = []
    recentRepackRecords.value = []
  }
}

function onWarehouseChange() {
  targetLocationId.value = filteredLocations.value[0]?.id || null
}

function selectPendingKanban(row) {
  kanbanNo.value = row.kanbanNo
  queryKanban()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function quickRepack(row) {
  await executeOne(row.kanbanNo, row.qty)
}

async function batchRepackPending() {
  if (!selectedPending.value.length) return
  await ElMessageBox.confirm(`确定批量转包 ${selectedPending.value.length} 张看板吗？`, '批量转包', { type: 'warning' })
  let success = 0
  for (const row of selectedPending.value) {
    const ok = await executeOne(row.kanbanNo, row.qty, false)
    if (ok) success++
  }
  ElMessage.success(`批量转包完成，成功 ${success} 张`)
  await loadLists()
}

async function executeOne(no, qty, showMessage = true) {
  try {
    const res = await universalRepackScan({ kanbanNo: no, actualQty: qty, operator: 'admin' })
    if (showMessage) ElMessage.success(res.message || '转包成功')
    await loadLists()
    return true
  } catch {
    if (showMessage) ElMessage.error('转包失败')
    return false
  }
}

async function queryKanban() {
  const value = kanbanNo.value.trim()
  if (!value) return ElMessage.warning('请输入或扫描看板号')
  kanbanInfo.value = null
  kanbanType.value = ''
  targetWarehouseId.value = null
  targetLocationId.value = null
  targetContainerCode.value = ''
  try {
    const res = await lookupKanban(value)
    const data = res.data
    if (data.type === 'INBOUND') {
      kanbanType.value = '入库看板'
      kanbanInfo.value = data.data
      scanQty.value = data.data.qty || 0
    } else if (data.type === 'OUTBOUND') {
      kanbanType.value = '出库看板'
      kanbanInfo.value = data.data
      scanQty.value = data.data.actualQty || 0
    } else if (data.type === 'REPACK') {
      kanbanType.value = '转包看板'
      kanbanInfo.value = data.data
      scanQty.value = data.data.qty || 0
    }
    applyRecommendedTarget()
  } catch {
    kanbanInfo.value = null
    ElMessage.error('未找到该看板，请检查看板号')
  }
}

function applyRecommendedTarget() {
  const info = kanbanInfo.value || {}
  targetWarehouseId.value = warehouseByName(info.warehouseName)?.id || warehouses.value[0]?.id || null
  targetLocationId.value = filteredLocations.value.find(item => item.code === info.locationName)?.id || filteredLocations.value[0]?.id || null
  const part = parts.value.find(item => item.id === info.partId || item.code === info.partCode)
  const supplier = suppliers.value.find(item => item.name === info.supplierName)
  const recommended = recommendContainer(part, supplier)
  targetContainerCode.value = recommended?.code || info.targetContainerCode || info.containerCode || ''
}

function recommendContainer(part, supplier) {
  if (!part) return null
  const supplierCode = supplier?.code || ''
  const candidates = containers.value.filter(container => {
    const supplierOk = !container.supplierCode || !supplierCode || container.supplierCode.toLowerCase() === supplierCode.toLowerCase()
    const partOk = !container.partCode || container.partCode.toLowerCase() === String(part.code || '').toLowerCase()
    return supplierOk && partOk
  })
  const targetCapacity = Number(part.targetPackageQty || 0)
  const typed = candidates.filter(container => !part.repackContainerType || String(container.type || '').toLowerCase() === String(part.repackContainerType).toLowerCase())
  return typed.find(container => targetCapacity > 0 && Number(container.capacity || 0) === targetCapacity)
    || candidates.find(container => targetCapacity > 0 && Number(container.capacity || 0) === targetCapacity)
    || typed[0]
    || candidates[0]
    || null
}

async function confirmRepack() {
  if (scanQty.value <= 0) return ElMessage.warning('转包数量必须大于0')
  if (scanQty.value > maxRepackQty.value) return ElMessage.warning('转包数量不能超过看板数量')
  scanning.value = true
  try {
    const body = { kanbanNo: kanbanNo.value.trim(), actualQty: scanQty.value, operator: 'admin' }
    if (targetWarehouseId.value) body.warehouseId = targetWarehouseId.value
    if (targetLocationId.value) body.locationId = targetLocationId.value
    if (targetContainerCode.value) body.containerCode = targetContainerCode.value
    const res = await universalRepackScan(body)
    ElMessage.success(res.message || '转包操作成功')
    kanbanNo.value = ''
    kanbanInfo.value = null
    kanbanType.value = ''
    await loadLists()
  } finally {
    scanning.value = false
  }
}

async function openCamera() {
  cameraError.value = ''
  scanHandled = false
  cameraVisible.value = true
  await nextTick()
  try {
    scanner = new Html5Qrcode('repack-camera-reader')
    const cameras = await Html5Qrcode.getCameras()
    if (!cameras.length) throw new Error('未检测到可用摄像头')
    const camera = cameras.find(item => /back|rear|environment|后置/i.test(item.label)) || cameras[0]
    await scanner.start(camera.id, { fps: 10, qrbox: { width: 260, height: 260 } }, handleCameraResult, () => {})
  } catch (error) {
    cameraError.value = cameraMessage(error)
  }
}

async function handleCameraResult(text) {
  if (scanHandled) return
  scanHandled = true
  kanbanNo.value = text.trim()
  await stopCamera()
  cameraVisible.value = false
  await queryKanban()
  if (kanbanInfo.value) ElMessage.success('二维码识别成功')
}

async function stopCamera() {
  if (!scanner) return
  try {
    if (scanner.isScanning) await scanner.stop()
    await scanner.clear()
  } finally {
    scanner = null
  }
}

function warehouseByName(name) {
  return warehouses.value.find(item => item.name === name || item.code === name)
}

function containerLabel(item) {
  return `${item.code} ${item.name || ''}（容量 ${item.capacity || '-'}）`
}

function sourceTypeLabel(value) {
  return { INBOUND: '入库看板', OUTBOUND: '出库看板', REPACK: '转包看板', SCANNED: '已入库看板' }[value] || value || '-'
}

function repackStatusLabel(value) {
  return {
    PRINTED: '待转包',
    SCANNED: '已入库',
    OUTBOUND: '已出库',
    REPACK_INBOUND: '转包入库',
    REPACK_OUTBOUND: '转包出库',
    BALANCE: '有结余',
    REPACKED: '已转包',
    VOIDED: '已作废'
  }[value] || value
}

function repackStatusType(value) {
  return { PRINTED: 'warning', REPACK_INBOUND: 'success', REPACK_OUTBOUND: 'primary', BALANCE: 'warning', REPACKED: 'success', VOIDED: 'danger' }[value] || 'info'
}

function cameraMessage(error) {
  const message = String(error?.message || error || '')
  if (/permission|notallowed/i.test(message)) return '摄像头权限被拒绝，请在浏览器设置中允许摄像头权限'
  if (/secure|https/i.test(message)) return '摄像头需要 HTTPS 或 localhost 安全环境'
  return message || '摄像头启动失败，请检查设备是否被其他程序占用'
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.scan-area { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 28px 0 8px; }
.scan-input { width: 420px; }
.scan-tip, .muted { text-align: center; color: #909399; font-size: 13px; }
.info-card, .list-card { margin-top: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.repack-form { margin-top: 8px; }
.scan-actions { margin-top: 18px; text-align: center; }
.status-alert { margin-top: 18px; }
.camera-tip { margin-bottom: 16px; }
.camera-reader { width: 100%; min-height: 360px; }
.camera-error { margin-top: 12px; color: #f56c6c; text-align: center; }
</style>
