<template>
  <div class="mobile-page">
    <div class="topbar">
      <el-button link @click="$router.push('/mobile')">返回</el-button>
      <strong>封存 / 解封</strong>
      <span />
    </div>

    <el-card>
      <el-input v-model="kanbanNo" size="large" placeholder="输入或扫码库存看板号" @keyup.enter="query" />
      <div class="btn-row">
        <el-button type="primary" size="large" @click="query">查询看板</el-button>
        <el-button type="success" size="large" @click="openCamera">摄像头扫码</el-button>
      </div>
    </el-card>

    <el-card v-if="kanban.id" class="info-card">
      <template #header>
        <div class="card-header">
          <strong>{{ kanban.kanbanNo }}</strong>
          <el-tag :type="statusType(kanban)">{{ statusLabel(kanban) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="入库单号">{{ kanban.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ kanban.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="零件">{{ kanban.partCode }} {{ kanban.partName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ kanban.qty }} {{ kanban.unit }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ kanban.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ kanban.locationName || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="kanban.sealed" label="封存原因">{{ kanban.sealReason || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions">
        <el-button v-if="canSeal" type="warning" @click="seal">封存</el-button>
        <el-button v-if="kanban.sealed" type="success" @click="unseal">解封</el-button>
      </div>
    </el-card>

    <el-dialog v-model="cameraVisible" title="摄像头扫码库存看板" width="92%" destroy-on-close @closed="stopCamera">
      <div id="mobile-kanban-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { getKanbanByNo, sealKanban, unsealKanban } from '@/api/kanban'

const kanbanNo = ref('')
const kanban = ref({})
const cameraVisible = ref(false)
const cameraError = ref('')
let scanner = null
let handled = false

const canSeal = computed(() => kanban.value.id && !kanban.value.sealed && !['OUTBOUND', 'VOIDED', 'REPACKED'].includes(kanban.value.status))

onBeforeUnmount(stopCamera)

async function query() {
  if (!kanbanNo.value.trim()) return ElMessage.warning('请输入看板号')
  try {
    kanban.value = (await getKanbanByNo(kanbanNo.value.trim())).data
  } catch {
    kanban.value = {}
  }
}

async function seal() {
  const { value } = await ElMessageBox.prompt('请输入封存原因', `封存看板 ${kanban.value.kanbanNo}`, { inputPlaceholder: '质量异常、待检等' })
  await sealKanban(kanban.value.id, value)
  ElMessage.success('看板已封存')
  await query()
}

async function unseal() {
  await ElMessageBox.confirm(`确定解封看板 ${kanban.value.kanbanNo} 吗？`, '提示')
  await unsealKanban(kanban.value.id)
  ElMessage.success('看板已解封')
  await query()
}

async function openCamera() {
  handled = false
  cameraError.value = ''
  cameraVisible.value = true
  await nextTick()
  try {
    scanner = new Html5Qrcode('mobile-kanban-reader')
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

function statusLabel(row) {
  if (row.sealed) return '已封存'
  return { PRINTED: '待入库', SCANNED: '已入库', OUTBOUND: '已出库', REPACKED: '已转包', VOIDED: '已作废' }[row.status] || row.status
}

function statusType(row) {
  if (row.sealed) return 'danger'
  return { PRINTED: 'warning', SCANNED: 'success', OUTBOUND: 'info', REPACKED: 'primary', VOIDED: 'danger' }[row.status] || 'info'
}
</script>

<style scoped>
.mobile-page { min-height: 100vh; background: #f5f7fb; padding: 12px; box-sizing: border-box; }
.topbar, .card-header { display: flex; justify-content: space-between; align-items: center; }
.btn-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 12px; }
.info-card { margin-top: 12px; }
.actions { margin-top: 14px; display: flex; gap: 10px; justify-content: center; }
.camera-reader { width: 100%; min-height: 320px; }
.camera-error { color: #f56c6c; text-align: center; margin-top: 10px; }
</style>
