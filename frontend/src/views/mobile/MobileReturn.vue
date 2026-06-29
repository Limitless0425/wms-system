<template>
  <div class="mobile-page">
    <div class="topbar">
      <el-button link @click="$router.push('/mobile')">返回</el-button>
      <strong>退库</strong>
      <span />
    </div>

    <el-card>
      <el-alert
        title="扫描已出库看板后，按数量退回库存"
        type="info"
        :closable="false"
        class="tip"
      />
      <el-input v-model="outboundNo" size="large" placeholder="输入或扫码出库看板号" @keyup.enter="queryOutbound" />
      <div class="btn-row">
        <el-button type="primary" size="large" @click="queryOutbound">查询</el-button>
        <el-button type="success" size="large" @click="openCamera">摄像头扫码</el-button>
      </div>
    </el-card>

    <el-card v-if="outbound.id" class="info-card">
      <template #header>
        <div class="card-header">
          <strong>出库看板信息</strong>
          <el-tag :type="outbound.status === 'OUTBOUND' ? 'success' : 'warning'">{{ outbound.status }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="看板号">{{ outbound.kanbanNo }}</el-descriptions-item>
        <el-descriptions-item label="出库单号">{{ outbound.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源看板">{{ outbound.sourceKanbanNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="零件">{{ outbound.partCode }} {{ outbound.partName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ outbound.actualQty }} {{ outbound.unit }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ outbound.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ outbound.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ outbound.locationName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-input-number v-model="returnQty" :min="1" :max="outbound.actualQty || 1" class="qty" />
      <el-input v-model="operator" placeholder="操作人" class="full-control" />
      <el-button class="full-btn" type="warning" size="large" :disabled="!canSubmit" @click="submit">确认退库</el-button>
    </el-card>

    <el-dialog v-model="cameraVisible" title="摄像头扫码出库看板" width="92%" destroy-on-close @closed="stopCamera">
      <div id="mobile-return-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { getOutboundKanbanByNo, returnOutbound } from '@/api/outbound'

const outboundNo = ref('')
const outbound = ref({})
const returnQty = ref(1)
const operator = ref('mobile')
const cameraVisible = ref(false)
const cameraError = ref('')
let scanner = null
let handled = false

const canSubmit = computed(() => outbound.value.id && returnQty.value > 0 && returnQty.value <= Number(outbound.value.actualQty || 0))

onBeforeUnmount(stopCamera)

async function queryOutbound() {
  if (!outboundNo.value.trim()) return ElMessage.warning('请输入出库看板号')
  try {
    outbound.value = (await getOutboundKanbanByNo(outboundNo.value.trim())).data
    returnQty.value = Number(outbound.value.actualQty || 1)
    if (outbound.value.status !== 'OUTBOUND') ElMessage.warning('只有已出库看板才能退库')
  } catch {
    outbound.value = {}
  }
}

async function submit() {
  if (!canSubmit.value) return
  await returnOutbound({
    kanbanNo: outbound.value.kanbanNo,
    qty: returnQty.value,
    operator: operator.value || 'mobile'
  })
  ElMessage.success('退库成功')
  outboundNo.value = ''
  outbound.value = {}
  returnQty.value = 1
}

async function openCamera() {
  handled = false
  cameraError.value = ''
  cameraVisible.value = true
  await nextTick()
  try {
    scanner = new Html5Qrcode('mobile-return-reader')
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
  outboundNo.value = text.trim()
  await stopCamera()
  cameraVisible.value = false
  await queryOutbound()
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
</script>

<style scoped>
.mobile-page { min-height: 100vh; background: #f5f7fb; padding: 12px; box-sizing: border-box; }
.topbar, .card-header { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.tip { margin-bottom: 12px; }
.btn-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 12px; }
.info-card { margin-top: 12px; }
.qty, .full-control, .full-btn { width: 100%; margin-top: 12px; }
.camera-reader { width: 100%; min-height: 320px; }
.camera-error { color: #f56c6c; text-align: center; margin-top: 10px; }
</style>
