<template>
  <div class="mobile-page">
    <div class="topbar">
      <el-button link @click="$router.push('/mobile')">返回</el-button>
      <strong>带单出库</strong>
      <el-button link type="primary" @click="$router.push('/mobile/outbound-orders')">出库单</el-button>
    </div>

    <el-card>
      <el-input v-model="kanbanNo" size="large" placeholder="输入或扫码出库看板号" @keyup.enter="query" />
      <div class="btn-row">
        <el-button type="primary" size="large" @click="query">查询</el-button>
        <el-button type="success" size="large" @click="openCamera">摄像头扫码</el-button>
      </div>
    </el-card>

    <el-card v-if="kanban.id" class="info-card">
      <template #header>
        <div class="card-header">
          <strong>{{ kanban.kanbanNo }}</strong>
          <el-tag :type="kanban.status === 'PRINTED' ? 'warning' : 'success'">{{ statusLabel(kanban.status) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="出库单号">{{ kanban.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源看板">{{ kanban.sourceKanbanNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="零件">{{ kanban.partCode }} {{ kanban.partName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ kanban.actualQty }} {{ kanban.unit }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ kanban.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ kanban.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ kanban.locationName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-button class="full-btn" type="primary" size="large" :disabled="kanban.status !== 'PRINTED'" @click="submit(false)">确认出库</el-button>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="card-header">
          <strong>最近带单出库记录</strong>
          <el-button type="primary" link @click="loadHistory">刷新</el-button>
        </div>
      </template>
      <el-table :data="history" border stripe max-height="300">
        <el-table-column prop="kanbanNo" label="出库看板号" width="220" />
        <el-table-column prop="orderNo" label="出库单号" width="180" />
        <el-table-column prop="partCode" label="零件号" width="110" />
        <el-table-column prop="partName" label="零件名称" min-width="140" />
        <el-table-column prop="actualQty" label="数量" width="80" />
        <el-table-column prop="outboundTime" label="出库时间" width="180" />
      </el-table>
    </el-card>

    <el-dialog v-model="cameraVisible" title="摄像头扫码出库看板" width="92%" destroy-on-close @closed="stopCamera">
      <div id="mobile-outbound-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { getOutboundKanbanByNo, getRecentOutbound, scanOutbound } from '@/api/outbound'

const kanbanNo = ref('')
const kanban = ref({})
const history = ref([])
const cameraVisible = ref(false)
const cameraError = ref('')
let scanner = null
let handled = false

onMounted(loadHistory)
onBeforeUnmount(stopCamera)

async function loadHistory() {
  history.value = (await getRecentOutbound()).data || []
}

async function query() {
  if (!kanbanNo.value.trim()) return ElMessage.warning('请输入出库看板号')
  try {
    kanban.value = (await getOutboundKanbanByNo(kanbanNo.value.trim())).data
  } catch {
    kanban.value = {}
  }
}

async function submit(force) {
  try {
    await scanOutbound({ kanbanNo: kanbanNo.value.trim(), operator: 'mobile', force })
    ElMessage.success(force ? '已强制出库' : '出库成功')
    kanbanNo.value = ''
    kanban.value = {}
    await loadHistory()
  } catch (error) {
    if (!force && error.message?.includes('不是最早入库批次')) {
      await ElMessageBox.confirm('该库存看板不是最早入库批次，是否强制出库？', '先进先出预警', {
        type: 'warning',
        confirmButtonText: '强制出库',
        cancelButtonText: '取消'
      })
      await submit(true)
    }
  }
}

async function openCamera() {
  handled = false
  cameraError.value = ''
  cameraVisible.value = true
  await nextTick()
  try {
    scanner = new Html5Qrcode('mobile-outbound-reader')
    const cameras = await Html5Qrcode.getCameras()
    const camera = cameras.find(item => /back|rear|environment|后置/i.test(item.label)) || cameras[0]
    if (!camera) throw new Error('未检测到可用摄像头')
    await scanner.start(camera.id, { fps: 10, qrbox: { width: 250, height: 250 } }, handleResult, () => {})
  } catch (error) {
    cameraError.value = String(error?.message || error || '摄像头启动失败')
  }
}

async function handleResult(text) {
  if (handled) return
  handled = true
  kanbanNo.value = text.trim()
  await stopCamera()
  cameraVisible.value = false
  await query()
}

async function stopCamera() {
  if (!scanner) return
  try {
    if (scanner.isScanning) await scanner.stop()
    await scanner.clear()
  } catch {
    // ignore released camera
  } finally {
    scanner = null
  }
}

function statusLabel(value) {
  return { PRINTED: '待出库', OUTBOUND: '已出库', VOIDED: '已作废' }[value] || value
}
</script>

<style scoped>
.mobile-page { min-height: 100vh; background: #f5f7fb; padding: 12px; box-sizing: border-box; }
.topbar, .card-header { display: flex; justify-content: space-between; align-items: center; }
.btn-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 12px; }
.info-card, .list-card { margin-top: 12px; }
.full-btn { width: 100%; margin-top: 12px; }
.camera-reader { width: 100%; min-height: 320px; }
.camera-error { color: #f56c6c; text-align: center; margin-top: 10px; }
</style>
