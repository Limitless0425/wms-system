<template>
  <div class="page-container">
    <el-card>
      <template #header><strong>看板扫码入库</strong></template>
      <div class="scan-area">
        <el-input v-model="kanbanNo" placeholder="扫描或输入看板号" size="large" class="scan-input" @keyup.enter="queryKanban" />
        <el-button type="primary" size="large" @click="queryKanban">查询看板</el-button>
        <el-button type="success" size="large" @click="openCamera"><el-icon><Camera /></el-icon>摄像头扫码</el-button>
      </div>
      <div class="scan-tip">摄像头识别二维码后，会自动查询并显示对应看板的完整信息。</div>
    </el-card>

    <el-card v-if="kanbanInfo" class="info-card">
      <template #header><div class="card-header"><strong>看板详细信息</strong><el-tag :type="statusType(kanbanInfo)">{{ statusLabel(kanbanInfo) }}</el-tag></div></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="看板号">{{ kanbanInfo.kanbanNo }}</el-descriptions-item>
        <el-descriptions-item label="入库单号">{{ kanbanInfo.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ kanbanInfo.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="零件编号">{{ kanbanInfo.partCode }}</el-descriptions-item>
        <el-descriptions-item label="零件名称">{{ kanbanInfo.partName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ kanbanInfo.qty }} {{ kanbanInfo.unit }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ kanbanInfo.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标库位">{{ kanbanInfo.locationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="器具">{{ kanbanInfo.containerCode || '-' }} {{ kanbanInfo.containerName || '' }}</el-descriptions-item>
        <el-descriptions-item label="打印时间">{{ kanbanInfo.printTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入库时间">{{ kanbanInfo.scanTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ kanbanInfo.scanner || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="kanbanInfo.sourceKanbanNo" label="来源看板">{{ kanbanInfo.sourceKanbanNo }}</el-descriptions-item>
        <el-descriptions-item v-if="kanbanInfo.sealed" label="封存原因">{{ kanbanInfo.sealReason || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="seal-actions">
        <el-button v-if="canSealCurrent" type="warning" @click="sealCurrentKanban">封存该看板</el-button>
        <el-button v-if="kanbanInfo.sealed" type="success" @click="unsealCurrentKanban">解封该看板</el-button>
      </div>
      <div v-if="kanbanInfo.status === 'PRINTED' && !kanbanInfo.sealed" class="scan-actions">
        <el-form :inline="true">
          <el-form-item label="本次入库数量"><el-input-number v-model="scanQty" :min="1" :max="kanbanInfo.qty" /></el-form-item>
          <el-form-item><el-button type="success" size="large" @click="confirmScan">确认入库</el-button></el-form-item>
        </el-form>
      </div>
      <el-alert v-else :title="kanbanInfo.sealed ? '该看板已封存，解封后才能入库' : '该看板当前状态不能重复入库'" type="warning" show-icon :closable="false" class="status-alert" />
    </el-card>

    <el-card class="list-card">
      <template #header><div class="card-header"><strong>未入库看板</strong><div><el-button type="success" size="small" @click="openBatchDialog">批量入库</el-button><el-button type="primary" link @click="loadKanbanLists" style="margin-left:8px">刷新</el-button></div></div></template>
      <el-table :data="pendingKanbans" border stripe max-height="320" @row-click="selectPendingKanban" style="cursor:pointer">
        <el-table-column prop="kanbanNo" label="看板号" width="220" />
        <el-table-column prop="orderNo" label="入库单号" width="180" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="warehouseName" label="仓库" min-width="140" />
        <el-table-column prop="partCode" label="零件号" width="100" />
        <el-table-column prop="partName" label="零件名称" min-width="120" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button size="small" type="success" @click.stop="selectPendingKanban(row)">选择</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="list-card">
      <template #header><div class="card-header"><strong>最近入库记录</strong><el-button type="primary" link @click="loadKanbanLists">刷新</el-button></div></template>
      <el-table :data="scanRecords" border stripe max-height="300">
        <el-table-column prop="kanbanNo" label="看板号" width="220" />
        <el-table-column prop="orderNo" label="入库单号" width="180" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="warehouseName" label="仓库" min-width="140" />
        <el-table-column prop="partCode" label="零件号" width="100" />
        <el-table-column prop="partName" label="零件名称" min-width="120" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="scanner" label="操作人" width="100" />
        <el-table-column prop="scanTime" label="入库时间" width="180" />
      </el-table>
    </el-card>

    <!-- Batch inbound dialog -->
    <el-dialog v-model="batchVisible" title="批量入库" width="1000px">
      <el-table :data="batchList" border stripe max-height="450" @selection-change="onBatchSelection" ref="batchTable">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="kanbanNo" label="看板号" width="220" />
        <el-table-column prop="orderNo" label="入库单号" width="180" />
        <el-table-column prop="supplierName" label="供应商" min-width="140" />
        <el-table-column prop="partCode" label="零件号" width="100" />
        <el-table-column prop="partName" label="零件名称" min-width="120" />
        <el-table-column prop="qty" label="总数" width="70" />
        <el-table-column label="入库数量" width="140">
          <template #default="{ row:r }">
            <el-input-number v-model="batchQtys[r.kanbanNo]" :min="1" :max="r.qty" size="small" controls-position="right" style="width:120px" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="batchVisible=false">取消</el-button>
        <el-button type="primary" :disabled="!batchSelected.length" @click="confirmBatchInbound">确认批量入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cameraVisible" title="摄像头扫描看板二维码" width="540px" destroy-on-close @closed="stopCamera">
      <el-alert title="请允许浏览器使用摄像头，并将看板二维码完整放入扫描框。" type="info" :closable="false" class="camera-tip" />
      <div id="kanban-camera-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
      <template #footer><el-button @click="cameraVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { getKanbanByNo, getKanbans, scanInbound, sealKanban, unsealKanban } from '@/api/kanban'

const kanbanNo = ref('')
const kanbanInfo = ref(null)
const scanQty = ref(0)
const pendingKanbans = ref([])
const scanRecords = ref([])
const cameraVisible = ref(false)
const cameraError = ref('')
const batchVisible = ref(false)
const batchList = ref([])
const batchSelected = ref([])
const batchQtys = reactive({})
const batchTable = ref(null)
let qrScanner = null
let scanHandled = false

const canSealCurrent = computed(() => kanbanInfo.value && !kanbanInfo.value.sealed && !['OUTBOUND','VOIDED','REPACKED'].includes(kanbanInfo.value.status))

onMounted(loadKanbanLists)
onBeforeUnmount(stopCamera)

async function loadKanbanLists() {
  const [p,s] = await Promise.all([getKanbans({status:'PENDING_SCAN'}), getKanbans({status:'INBOUND'})])
  pendingKanbans.value = p.data || []
  scanRecords.value = (s.data||[]).sort((a,b)=>(!a.scanTime?1:!b.scanTime?-1:new Date(b.scanTime)-new Date(a.scanTime)))
}

async function selectPendingKanban(row) { kanbanNo.value=row.kanbanNo; await queryKanban(); window.scrollTo({top:0,behavior:'smooth'}) }

function openBatchDialog() {
  batchList.value = [...pendingKanbans.value]
  batchSelected.value = []
  // Initialize qtys
  for (const r of batchList.value) { if (!(r.kanbanNo in batchQtys)) batchQtys[r.kanbanNo] = r.qty }
  batchVisible.value = true
}

function onBatchSelection(rows) { batchSelected.value = rows }

async function confirmBatchInbound() {
  if (!batchSelected.value.length) return
  await ElMessageBox.confirm(`确定批量入库 ${batchSelected.value.length} 张看板吗？`,'批量入库',{type:'warning'})
  for (const row of batchSelected.value) {
    try { await scanInbound(row.kanbanNo, {qty:String(batchQtys[row.kanbanNo]||row.qty), scanner:'admin'}) }
    catch(e) { ElMessage.error(`看板 ${row.kanbanNo} 入库失败: ${e.response?.data?.message||e.message}`) }
  }
  ElMessage.success('批量入库完成')
  batchVisible.value = false
  await loadKanbanLists()
}

async function queryKanban() {
  const v=kanbanNo.value.trim()
  if(!v) return ElMessage.warning('请输入或扫描看板号')
  try { const r=await getKanbanByNo(v); kanbanInfo.value=r.data; scanQty.value=r.data.qty } catch { kanbanInfo.value=null }
}

async function openCamera() {
  cameraError.value=''; scanHandled=false; cameraVisible.value=true; await nextTick()
  try {
    qrScanner=new Html5Qrcode('kanban-camera-reader')
    const cams=await Html5Qrcode.getCameras()
    if(!cams.length) throw new Error('未检测到可用摄像头')
    const cam=cams.find(i=>/back|rear|environment|后置/i.test(i.label))||cams[0]
    await qrScanner.start(cam.id,{fps:10,qrbox:{width:260,height:260}},handleCameraResult,()=>{})
  } catch(e) { cameraError.value=cameraMessage(e) }
}

async function handleCameraResult(t) { if(scanHandled)return; scanHandled=true; kanbanNo.value=t.trim(); await stopCamera(); cameraVisible.value=false; await queryKanban(); if(kanbanInfo.value) ElMessage.success('二维码识别成功') }

async function stopCamera() { if(!qrScanner)return; try{if(qrScanner.isScanning)await qrScanner.stop();await qrScanner.clear()}catch{}finally{qrScanner=null} }

function cameraMessage(e) {
  const m=String(e?.message||e||'')
  if(/permission|notallowed/i.test(m)) return '摄像头权限被拒绝，请在浏览器设置中允许摄像头权限'
  if(/secure|https/i.test(m)) return '摄像头需要 HTTPS 或 localhost 安全环境'
  return m||'摄像头启动失败，请检查设备是否被其他程序占用'
}

async function confirmScan() { await scanInbound(kanbanInfo.value.kanbanNo,{qty:String(scanQty.value),scanner:'admin'}); ElMessage.success('扫码入库成功'); kanbanInfo.value=null; kanbanNo.value=''; await loadKanbanLists() }

async function sealCurrentKanban() {
  const {value}=await ElMessageBox.prompt('请输入封存原因',`封存看板 ${kanbanInfo.value.kanbanNo}`,{inputPlaceholder:'质量异常、待检等'})
  await sealKanban(kanbanInfo.value.id,value); ElMessage.success('看板已封存'); await queryKanban(); await loadKanbanLists()
}

async function unsealCurrentKanban() { await ElMessageBox.confirm(`确定解封看板 ${kanbanInfo.value.kanbanNo} 吗？`,'提示'); await unsealKanban(kanbanInfo.value.id); ElMessage.success('看板已解封'); await queryKanban(); await loadKanbanLists() }

function statusLabel(r) { if(r.sealed)return'已封存'; return{PRINTED:'待入库',SCANNED:'已入库',OUTBOUND:'已出库',REPACKED:'已转包',VOIDED:'已作废'}[r.status]||r.status }
function statusType(r) { if(r.sealed)return'danger'; return{PRINTED:'warning',SCANNED:'success',OUTBOUND:'info',REPACKED:'primary',VOIDED:'danger'}[r.status]||'info' }
</script>

<style scoped>
.page-container{min-height:300px}
.scan-area{display:flex;align-items:center;justify-content:center;gap:12px;padding:28px 0 8px}
.scan-input{width:400px}
.scan-tip{text-align:center;color:#909399;font-size:13px}
.info-card,.list-card{margin-top:16px}
.card-header{display:flex;align-items:center;justify-content:space-between}
.scan-actions,.seal-actions{margin-top:18px;text-align:center}
.status-alert{margin-top:18px}
.camera-tip{margin-bottom:16px}
.camera-reader{width:100%;min-height:360px}
.camera-error{margin-top:12px;color:#f56c6c;text-align:center}
</style>
