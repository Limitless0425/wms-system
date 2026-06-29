<template>
  <div class="mobile-page">
    <div class="topbar">
      <el-button link @click="$router.push('/mobile')">返回</el-button>
      <strong>手机转包</strong>
      <el-button link type="primary" @click="$router.push('/mobile/repack-orders')">转包单</el-button>
    </div>

    <el-card>
      <el-alert title="先扫来源看板生成转包单和转包看板，再扫转包看板完成记录。" type="info" :closable="false" class="tip" />
      <el-input v-model="sourceNo" size="large" placeholder="输入或扫描来源看板号" @keyup.enter="querySource" />
      <div class="btn-row">
        <el-button type="primary" size="large" @click="querySource">查询</el-button>
        <el-button type="success" size="large" @click="openCamera('source')">摄像头扫码</el-button>
      </div>
    </el-card>

    <el-card v-if="source.id" class="info-card">
      <template #header>
        <div class="card-header">
          <strong>来源看板信息</strong>
          <el-tag>{{ source.businessType === 'OUTBOUND' ? '未出库看板' : source.status === 'PRINTED' ? '未入库看板' : '库存看板' }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="看板号">{{ source.kanbanNo }}</el-descriptions-item>
        <el-descriptions-item label="零件">{{ source.partCode }} {{ source.partName }}</el-descriptions-item>
        <el-descriptions-item label="可转包数量">{{ source.qty }} {{ source.unit }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ source.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ source.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ source.locationName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="qty-wrap">
        <el-input-number v-model="qty" :min="1" :max="source.qty || 1" class="qty" />
        <el-select v-model="targetContainerCode" placeholder="选择目标器具" filterable class="full-control">
          <el-option v-for="item in containers" :key="item.id" :label="containerLabel(item)" :value="item.code" />
        </el-select>
        <el-tag v-if="capacityTip" type="info">{{ capacityTip }}</el-tag>
        <el-input v-model="operator" placeholder="操作人" class="full-control" />
      </div>
      <el-button class="full-btn" type="primary" size="large" :disabled="!canSubmit" @click="submit">生成转包单</el-button>
    </el-card>

    <el-card v-if="createdKanbans.length" class="list-card">
      <template #header>
        <div class="card-header">
          <strong>本次生成的转包看板</strong>
          <el-button type="primary" link :disabled="!createdKanbans.some(canScan)" @click="recordCreatedKanbans">一键记录</el-button>
        </div>
      </template>
      <el-table :data="createdKanbans" border stripe max-height="260">
        <el-table-column prop="kanbanNo" label="看板号" width="220" />
        <el-table-column prop="orderNo" label="转包单" width="180" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="success" link :disabled="!canScan(row)" @click="recordKanban(row)">记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="info-card">
      <template #header><strong>扫描转包看板完成记录</strong></template>
      <el-input v-model="repackKanbanNo" size="large" placeholder="输入或扫描转包看板号" @keyup.enter="scanRepack" />
      <div class="btn-row">
        <el-button type="primary" size="large" @click="scanRepack">确认记录</el-button>
        <el-button type="success" size="large" @click="openCamera('repack')">摄像头扫码</el-button>
      </div>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="card-header">
          <strong>最近转包记录</strong>
          <el-button type="primary" link @click="loadRecords">刷新</el-button>
        </div>
      </template>
      <el-table :data="records" border stripe max-height="320">
        <el-table-column prop="sourceKanbanNo" label="来源看板" width="220" />
        <el-table-column prop="targetKanbanNo" label="目标看板" width="220" />
        <el-table-column prop="partCode" label="零件号" width="120" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="sourceBalance" label="余量" width="80" />
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
    </el-card>

    <el-dialog v-model="cameraVisible" title="摄像头扫码" width="92%" destroy-on-close @closed="stopCamera">
      <div id="mobile-repack-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { getContainers } from '@/api/baseInfo'
import { getRepackRecords, getRepackSource, repackKanban, scanRepackKanban } from '@/api/operations'

const sourceNo = ref('')
const source = ref({})
const qty = ref(1)
const targetContainerCode = ref('')
const operator = ref('mobile')
const repackKanbanNo = ref('')
const createdKanbans = ref([])
const records = ref([])
const containers = ref([])
const cameraVisible = ref(false)
const cameraError = ref('')
const scanMode = ref('source')
let scanner = null
let handled = false

const canSubmit = computed(() => source.value.id && targetContainerCode.value && qty.value > 0 && qty.value <= Number(source.value.qty || 0))
const capacityTip = computed(() => {
  const container = containers.value.find(item => item.code === targetContainerCode.value)
  const capacity = Number(container?.capacity || 0)
  if (!capacity || !qty.value) return ''
  const count = Math.ceil(qty.value / capacity)
  const remainder = qty.value % capacity
  return remainder === 0 ? `需要 ${count} 个器具，全部装满` : `需要 ${count} 个器具，最后一个未装满（${remainder}/${capacity}）`
})

onMounted(async () => {
  try {
    containers.value = (await getContainers()).data || []
  } catch {
    ElMessage.warning('器具列表加载失败，请确认后端已启动并重新登录')
  }
  await loadRecords()
})
onBeforeUnmount(stopCamera)

async function querySource() {
  if (!sourceNo.value.trim()) return ElMessage.warning('请输入来源看板号')
  try {
    source.value = (await getRepackSource(sourceNo.value.trim())).data
    qty.value = Number(source.value.qty || 1)
  } catch {
    source.value = {}
  }
}

async function submit() {
  if (!canSubmit.value) return
  const res = await repackKanban({
    sourceKanbanNo: source.value.kanbanNo,
    targetContainerCode: targetContainerCode.value,
    qty: String(qty.value),
    operator: operator.value || 'mobile'
  })
  createdKanbans.value = res.data?.kanbans || []
  ElMessage.success('已生成转包单和转包看板')
  sourceNo.value = ''
  source.value = {}
  qty.value = 1
  targetContainerCode.value = ''
  await loadRecords()
}

async function scanRepack() {
  if (!repackKanbanNo.value.trim()) return ElMessage.warning('请输入转包看板号')
  const kanbanNo = repackKanbanNo.value.trim()
  await scanRepackKanban(kanbanNo, { operator: operator.value || 'mobile' })
  ElMessage.success('转包看板记录完成')
  repackKanbanNo.value = ''
  createdKanbans.value = createdKanbans.value.map(row => row.kanbanNo === kanbanNo ? { ...row, status: 'DONE' } : row)
  await loadRecords()
}

async function recordKanban(row) {
  await scanRepackKanban(row.kanbanNo, { operator: operator.value || 'mobile' })
  row.status = 'DONE'
  ElMessage.success(`已记录 ${row.kanbanNo}`)
  await loadRecords()
}

async function recordCreatedKanbans() {
  const pending = createdKanbans.value.filter(canScan)
  for (const row of pending) await recordKanban(row)
}

function canScan(row) {
  return row.status === 'PRINTED'
}

async function loadRecords() {
  try {
    records.value = ((await getRepackRecords()).data || []).slice(0, 50)
  } catch {
    records.value = []
  }
}

async function openCamera(mode) {
  scanMode.value = mode
  handled = false
  cameraError.value = ''
  cameraVisible.value = true
  await nextTick()
  try {
    scanner = new Html5Qrcode('mobile-repack-reader')
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
  if (scanMode.value === 'repack') repackKanbanNo.value = text.trim()
  else sourceNo.value = text.trim()
  await stopCamera()
  cameraVisible.value = false
  if (scanMode.value === 'repack') await scanRepack()
  else await querySource()
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

function containerLabel(item) {
  return `${item.code} ${item.name || ''}（容量 ${item.capacity || '-'}）`
}
</script>

<style scoped>
.mobile-page { min-height: 100vh; background: #f5f7fb; padding: 12px; box-sizing: border-box; }
.topbar, .card-header { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.tip { margin-bottom: 12px; }
.btn-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 12px; }
.info-card, .list-card { margin-top: 12px; }
.qty-wrap { display: grid; gap: 10px; margin-top: 12px; }
.qty, .full-control, .full-btn { width: 100%; }
.full-btn { margin-top: 12px; }
.camera-reader { width: 100%; min-height: 320px; }
.camera-error { color: #f56c6c; text-align: center; margin-top: 10px; }
</style>
