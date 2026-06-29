<template>
  <div class="mobile-page">
    <div class="topbar">
      <el-button link @click="$router.push('/mobile')">返回</el-button>
      <strong>不带单出库</strong>
      <span />
    </div>

    <el-card>
      <el-input v-model="kanbanNo" size="large" placeholder="输入或扫码库存看板号" @keyup.enter="query" />
      <div class="btn-row">
        <el-button type="primary" size="large" @click="query">查询</el-button>
        <el-button type="success" size="large" @click="openCamera">摄像头扫码</el-button>
      </div>
    </el-card>

    <el-card v-if="kanban.id" class="info-card">
      <template #header>
        <div class="card-header">
          <strong>{{ kanban.kanbanNo }}</strong>
          <el-tag :type="kanban.sealed ? 'danger' : kanban.status === 'SCANNED' ? 'success' : 'warning'">
            {{ kanban.sealed ? '已封存' : statusLabel(kanban.status) }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="零件">{{ kanban.partCode }} {{ kanban.partName }}</el-descriptions-item>
        <el-descriptions-item label="可用数量">{{ kanban.qty }} {{ kanban.unit }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ kanban.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ kanban.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ kanban.locationName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-input-number v-model="qty" :min="1" :max="kanban.qty || 1" class="qty" />
      <el-button class="full-btn" type="warning" size="large" :disabled="kanban.status !== 'SCANNED' || kanban.sealed" @click="submit">确认不带单出库</el-button>
    </el-card>

    <el-dialog v-model="cameraVisible" title="摄像头扫码库存看板" width="92%" destroy-on-close @closed="stopCamera">
      <div id="mobile-direct-outbound-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { directOutbound } from '@/api/outbound'
import { getKanbanByNo } from '@/api/kanban'

const kanbanNo = ref('')
const kanban = ref({})
const qty = ref(1)
const cameraVisible = ref(false)
const cameraError = ref('')
let scanner = null
let handled = false

onBeforeUnmount(stopCamera)

async function query() {
  if (!kanbanNo.value.trim()) return ElMessage.warning('请输入库存看板号')
  try {
    kanban.value = (await getKanbanByNo(kanbanNo.value.trim())).data
    qty.value = Number(kanban.value.qty || 1)
  } catch {
    kanban.value = {}
  }
}

async function submit() {
  await directOutbound({ kanbanNo: kanbanNo.value.trim(), qty: qty.value, operator: 'mobile' })
  ElMessage.success('不带单出库成功')
  kanbanNo.value = ''
  kanban.value = {}
  qty.value = 1
}

async function openCamera() {
  handled = false
  cameraError.value = ''
  cameraVisible.value = true
  await nextTick()
  try {
    scanner = new Html5Qrcode('mobile-direct-outbound-reader')
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
  return { PRINTED: '待入库', SCANNED: '已入库', OUTBOUND: '已出库', REPACKED: '已转包', VOIDED: '已作废' }[value] || value
}
</script>

<style scoped>
.mobile-page { min-height: 100vh; background: #f5f7fb; padding: 12px; box-sizing: border-box; }
.topbar, .card-header { display: flex; justify-content: space-between; align-items: center; }
.btn-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 12px; }
.info-card { margin-top: 12px; }
.qty, .full-btn { width: 100%; margin-top: 12px; }
.camera-reader { width: 100%; min-height: 320px; }
.camera-error { color: #f56c6c; text-align: center; margin-top: 10px; }
</style>
