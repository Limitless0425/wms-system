<template>
  <div class="page-container">
    <el-card>
      <template #header><strong>带单扫码出库</strong></template>
      <div class="scan-area">
        <el-input v-model="kanbanNo" placeholder="扫描或输入出库看板号" size="large" class="scan-input" @keyup.enter="queryKanban" />
        <el-button type="primary" size="large" @click="queryKanban">查询看板</el-button>
        <el-button type="success" size="large" @click="openCamera">摄像头扫码</el-button>
      </div>
      <div class="scan-tip">带单出库只扫描出库单生成的出库看板。</div>
    </el-card>

    <el-card v-if="kanbanInfo" class="info-card">
      <template #header><div class="card-header"><strong>出库看板详细信息</strong><el-tag :type="outboundStatusType(kanbanInfo.status)">{{ outboundStatusLabel(kanbanInfo.status) }}</el-tag></div></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="出库看板号">{{ kanbanInfo.kanbanNo }}</el-descriptions-item>
        <el-descriptions-item label="出库单号">{{ kanbanInfo.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源库存看板">{{ kanbanInfo.sourceKanbanNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="零件编号">{{ kanbanInfo.partCode }}</el-descriptions-item>
        <el-descriptions-item label="零件名称">{{ kanbanInfo.partName }}</el-descriptions-item>
        <el-descriptions-item label="出库数量">{{ kanbanInfo.actualQty || kanbanInfo.qty }} {{ kanbanInfo.unit }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ kanbanInfo.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ kanbanInfo.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ kanbanInfo.locationName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="direct-form"><el-input-number v-model="outboundQty" :min="1" :max="kanbanInfo.actualQty || kanbanInfo.qty || 1" /></div>
      <div class="scan-actions"><el-button type="primary" size="large" :disabled="kanbanInfo.status !== 'PRINTED'" @click="submit(false)">确认出库</el-button></div>
    </el-card>

    <el-card class="list-card">
      <template #header><div class="card-header"><strong>未出库看板</strong><div><el-button type="success" size="small" @click="openBatchDialog">批量出库</el-button><el-button type="primary" link @click="loadPendingKanbans" style="margin-left:8px">刷新</el-button></div></div></template>
      <el-table :data="pendingKanbans" border stripe max-height="320" @row-click="selectPendingKanban" style="cursor:pointer">
        <el-table-column prop="kanbanNo" label="出库看板号" width="220" />
        <el-table-column prop="orderNo" label="出库单号" width="180" />
        <el-table-column prop="partCode" label="零件号" width="110" />
        <el-table-column prop="partName" label="零件名称" min-width="140" />
        <el-table-column prop="actualQty" label="数量" width="80" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="warehouseName" label="仓库" min-width="130" />
        <el-table-column prop="locationName" label="库位" width="110" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button size="small" type="success" @click.stop="batchOutbound(row)">选择</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="list-card">
      <template #header><div class="card-header"><strong>最近带单出库记录</strong><el-button type="primary" link @click="loadOutboundHistory">刷新</el-button></div></template>
      <el-table :data="outboundRecords" border stripe max-height="300">
        <el-table-column prop="kanbanNo" label="出库看板号" width="220" />
        <el-table-column prop="orderNo" label="出库单号" width="180" />
        <el-table-column prop="partCode" label="零件号" width="110" />
        <el-table-column prop="partName" label="零件名称" min-width="140" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="warehouseName" label="仓库" min-width="130" />
        <el-table-column prop="locationName" label="库位" width="110" />
        <el-table-column prop="actualQty" label="数量" width="80" />
        <el-table-column prop="outboundOperator" label="操作人" width="100" />
        <el-table-column prop="outboundTime" label="出库时间" width="180" />
      </el-table>
    </el-card>

    <!-- Batch outbound dialog -->
    <el-dialog v-model="batchVisible" title="批量出库" width="1000px">
      <el-table :data="batchList" border stripe max-height="450" @selection-change="onBatchSelection" ref="batchTable">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="kanbanNo" label="出库看板号" width="220" />
        <el-table-column prop="orderNo" label="出库单号" width="180" />
        <el-table-column prop="partCode" label="零件号" width="100" />
        <el-table-column prop="partName" label="零件名称" min-width="120" />
        <el-table-column prop="actualQty" label="总数" width="70" />
        <el-table-column label="出库数量" width="140">
          <template #default="{ row:r }"><el-input-number v-model="batchQtys[r.kanbanNo]" :min="1" :max="r.actualQty" size="small" controls-position="right" style="width:120px" /></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="batchVisible=false">取消</el-button>
        <el-button type="primary" :disabled="!batchSelected.length" @click="confirmBatchOutbound">确认批量出库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cameraVisible" title="摄像头扫描出库看板二维码" width="540px" destroy-on-close @closed="stopCamera">
      <el-alert title="请允许浏览器使用摄像头，并将出库看板二维码完整放入扫描框。" type="info" :closable="false" class="camera-tip" />
      <div id="outbound-camera-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
      <template #footer><el-button @click="cameraVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { getOutboundKanbanByNo, getRecentOutbound, scanOutbound, getPendingOutbound } from '@/api/outbound'

const kanbanNo = ref('')
const kanbanInfo = ref(null)
const outboundQty = ref(1)
const pendingKanbans = ref([])
const outboundRecords = ref([])
const cameraVisible = ref(false)
const cameraError = ref('')
const batchVisible = ref(false)
const batchList = ref([])
const batchSelected = ref([])
const batchQtys = reactive({})
const batchTable = ref(null)
let scanner = null
let scanHandled = false

onMounted(()=>{loadPendingKanbans();loadOutboundHistory()})
onBeforeUnmount(stopCamera)

async function loadPendingKanbans() {
  try { const r=await getPendingOutbound(); pendingKanbans.value=(r.data||[]).sort((a,b)=>(!a.printTime?1:!b.printTime?-1:new Date(b.printTime)-new Date(a.printTime))) }
  catch { pendingKanbans.value=[] }
}

function selectPendingKanban(row) { kanbanNo.value=row.kanbanNo; queryKanban(); window.scrollTo({top:0,behavior:'smooth'}) }
async function batchOutbound(row) { kanbanNo.value=row.kanbanNo; await queryKanban() }

function openBatchDialog() {
  batchList.value = [...pendingKanbans.value]
  batchSelected.value = []
  for (const r of batchList.value) { if (!(r.kanbanNo in batchQtys)) batchQtys[r.kanbanNo] = r.actualQty }
  batchVisible.value = true
}

function onBatchSelection(rows) { batchSelected.value = rows }

async function confirmBatchOutbound() {
  if (!batchSelected.value.length) return
  await ElMessageBox.confirm(`确定批量出库 ${batchSelected.value.length} 张看板吗？`,'批量出库',{type:'warning'})
  for (const row of batchSelected.value) {
    try { await scanOutbound({kanbanNo:row.kanbanNo, operator:'admin', force:false}) }
    catch(e) { ElMessage.error(`看板 ${row.kanbanNo} 出库失败: ${e.response?.data?.message||e.message}`) }
  }
  ElMessage.success('批量出库完成')
  batchVisible.value = false
  kanbanInfo.value = null
  await loadPendingKanbans()
  await loadOutboundHistory()
}

async function loadOutboundHistory() { outboundRecords.value=(await getRecentOutbound()).data||[] }

async function queryKanban() {
  const v=kanbanNo.value.trim()
  if(!v) return ElMessage.warning('请输入或扫描出库看板号')
  try { kanbanInfo.value=(await getOutboundKanbanByNo(v)).data; outboundQty.value=Number(kanbanInfo.value.actualQty||kanbanInfo.value.qty||1) }
  catch { kanbanInfo.value=null }
}

async function submit(force) {
  try {
    await scanOutbound({kanbanNo:kanbanNo.value.trim(),operator:'admin',force})
    ElMessage.success(force?'已确认先进先出例外并完成出库':'扫码出库成功')
    kanbanNo.value=''; kanbanInfo.value=null; await loadPendingKanbans(); await loadOutboundHistory()
  } catch(e) {
    if(!force && e.message?.includes('不是最早入库批次')) {
      await ElMessageBox.confirm('该库存看板不是此零件最早入库批次，是否记录例外并强制出库？','先进先出预警',{type:'warning',confirmButtonText:'强制出库',cancelButtonText:'取消'})
      await submit(true)
    }
  }
}

async function openCamera() {
  cameraError.value=''; scanHandled=false; cameraVisible.value=true; await nextTick()
  try {
    scanner=new Html5Qrcode('outbound-camera-reader')
    const cams=await Html5Qrcode.getCameras()
    if(!cams.length) throw new Error('未检测到可用摄像头')
    const cam=cams.find(i=>/back|rear|environment|后置/i.test(i.label))||cams[0]
    await scanner.start(cam.id,{fps:10,qrbox:{width:260,height:260}},handleCameraResult,()=>{})
  } catch(e) { cameraError.value=cameraMessage(e) }
}

async function handleCameraResult(t) { if(scanHandled)return; scanHandled=true; kanbanNo.value=t.trim(); await stopCamera(); cameraVisible.value=false; await queryKanban(); if(kanbanInfo.value) ElMessage.success('二维码识别成功') }

async function stopCamera() { if(!scanner)return; try{if(scanner.isScanning)await scanner.stop();await scanner.clear()}catch{}finally{scanner=null} }

function cameraMessage(e) {
  const m=String(e?.message||e||'')
  if(/permission|notallowed/i.test(m)) return '摄像头权限被拒绝，请在浏览器设置中允许摄像头权限'
  if(/secure|https/i.test(m)) return '摄像头需要 HTTPS 或 localhost 安全环境'
  return m||'摄像头启动失败，请检查设备是否被其他程序占用'
}

function outboundStatusLabel(v) { return {PRINTED:'待出库',OUTBOUND:'已出库',VOIDED:'已作废'}[v]||v }
function outboundStatusType(v) { return {PRINTED:'warning',OUTBOUND:'success',VOIDED:'danger'}[v]||'info' }
</script>

<style scoped>
.page-container{min-height:300px}
.scan-area{display:flex;align-items:center;justify-content:center;gap:12px;padding:28px 0 8px}
.scan-input{width:400px}
.scan-tip{text-align:center;color:#909399;font-size:13px}
.info-card,.list-card{margin-top:16px}
.card-header{display:flex;align-items:center;justify-content:space-between}
.direct-form{margin-top:16px;display:flex;justify-content:center;gap:12px}
.scan-actions{margin-top:18px;text-align:center}
.camera-tip{margin-bottom:16px}
.camera-reader{width:100%;min-height:360px}
.camera-error{margin-top:12px;color:#f56c6c;text-align:center}
</style>
