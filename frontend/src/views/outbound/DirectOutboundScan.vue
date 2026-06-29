<template>
  <div class="page-container">
    <el-card>
      <template #header><strong>不带单出库</strong></template>
      <div class="scan-area">
        <el-input v-model="kanbanNo" placeholder="扫描或输入库存看板号" size="large" class="scan-input" @keyup.enter="queryKanban" />
        <el-button type="primary" size="large" @click="queryKanban">查询库存看板</el-button>
        <el-button type="success" size="large" @click="openCamera">摄像头扫码</el-button>
      </div>
      <div class="scan-tip">不带单出库直接扣减当前库存看板，不生成出库单。</div>
    </el-card>

    <el-card v-if="kanban.id" class="info-card">
      <template #header><div class="card-header"><strong>库存看板详细信息</strong><el-tag :type="kanban.sealed?'danger':statusType(kanban.status)">{{ kanban.sealed?'已封存':statusLabel(kanban.status) }}</el-tag></div></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="库存看板号">{{ kanban.kanbanNo }}</el-descriptions-item>
        <el-descriptions-item label="零件编号">{{ kanban.partCode }}</el-descriptions-item>
        <el-descriptions-item label="零件名称">{{ kanban.partName }}</el-descriptions-item>
        <el-descriptions-item label="可用数量">{{ kanban.qty }} {{ kanban.unit }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ kanban.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ kanban.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ kanban.locationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源看板">{{ kanban.sourceKanbanNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="封存">{{ kanban.sealed?'是':'否' }}</el-descriptions-item>
      </el-descriptions>
      <div class="direct-form"><el-input-number v-model="outboundQty" :min="1" :max="kanban.qty||1" /><el-input v-model="operator" placeholder="操作人" /></div>
      <div class="scan-actions"><el-button type="warning" size="large" :disabled="kanban.status!=='SCANNED'||kanban.sealed" @click="submit">确认不带单出库</el-button></div>
    </el-card>

    <el-card class="list-card">
      <template #header><div class="card-header"><strong>可出库库存看板</strong><div><el-button type="success" size="small" @click="openBatchDialog">批量出库</el-button><el-button type="primary" link @click="loadStockKanbans" style="margin-left:8px">刷新</el-button></div></div></template>
      <el-table :data="stockKanbans" border stripe max-height="400" @row-click="selectStockKanban" style="cursor:pointer">
        <el-table-column prop="kanbanNo" label="库存看板号" width="220" />
        <el-table-column prop="partCode" label="零件号" width="110" />
        <el-table-column prop="partName" label="零件名称" min-width="130" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="supplierName" label="供应商" min-width="140" />
        <el-table-column prop="warehouseName" label="仓库" min-width="120" />
        <el-table-column prop="locationName" label="库位" width="110" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button size="small" type="warning" @click.stop="quickOutbound(row)">选择</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Batch direct outbound dialog -->
    <el-dialog v-model="batchVisible" title="批量出库" width="1000px">
      <el-table :data="batchList" border stripe max-height="450" @selection-change="onBatchSelection" ref="batchTable">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="kanbanNo" label="库存看板号" width="220" />
        <el-table-column prop="partCode" label="零件号" width="100" />
        <el-table-column prop="partName" label="零件名称" min-width="120" />
        <el-table-column prop="supplierName" label="供应商" min-width="130" />
        <el-table-column prop="warehouseName" label="仓库" min-width="110" />
        <el-table-column prop="qty" label="总数" width="70" />
        <el-table-column label="出库数量" width="140">
          <template #default="{ row:r }"><el-input-number v-model="batchQtys[r.kanbanNo]" :min="1" :max="r.qty" size="small" controls-position="right" style="width:120px" /></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="batchVisible=false">取消</el-button>
        <el-button type="primary" :disabled="!batchSelected.length" @click="confirmBatchDirect">确认批量出库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cameraVisible" title="摄像头扫描库存看板二维码" width="540px" destroy-on-close @closed="stopCamera">
      <el-alert title="请扫描已入库且未封存的库存看板。" type="info" :closable="false" class="camera-tip" />
      <div id="direct-outbound-camera-reader" class="camera-reader" />
      <div v-if="cameraError" class="camera-error">{{ cameraError }}</div>
      <template #footer><el-button @click="cameraVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { directOutbound } from '@/api/outbound'
import { getKanbanByNo, getKanbans } from '@/api/kanban'

const kanbanNo = ref('')
const kanban = ref({})
const stockKanbans = ref([])
const outboundQty = ref(1)
const operator = ref('admin')
const cameraVisible = ref(false)
const cameraError = ref('')
const batchVisible = ref(false)
const batchList = ref([])
const batchSelected = ref([])
const batchQtys = reactive({})
const batchTable = ref(null)
let scanner = null
let scanHandled = false

onMounted(loadStockKanbans)
onBeforeUnmount(stopCamera)

async function loadStockKanbans() {
  try { const r=await getKanbans({status:'INBOUND'}); stockKanbans.value=(r.data||[]).sort((a,b)=>(!a.scanTime?1:!b.scanTime?-1:new Date(b.scanTime)-new Date(a.scanTime))) }
  catch { stockKanbans.value=[] }
}

function selectStockKanban(row) { kanbanNo.value=row.kanbanNo; queryKanban(); window.scrollTo({top:0,behavior:'smooth'}) }
async function quickOutbound(row) { kanbanNo.value=row.kanbanNo; await queryKanban() }

function openBatchDialog() {
  batchList.value = [...stockKanbans.value]
  batchSelected.value = []
  for (const r of batchList.value) { if (!(r.kanbanNo in batchQtys)) batchQtys[r.kanbanNo] = r.qty }
  batchVisible.value = true
}

function onBatchSelection(rows) { batchSelected.value = rows }

async function confirmBatchDirect() {
  if (!batchSelected.value.length) return
  await ElMessageBox.confirm(`确定批量出库 ${batchSelected.value.length} 张看板吗？`,'批量出库',{type:'warning'})
  for (const row of batchSelected.value) {
    try { await directOutbound({kanbanNo:row.kanbanNo, qty:batchQtys[row.kanbanNo]||row.qty, operator:'admin'}) }
    catch(e) { ElMessage.error(`看板 ${row.kanbanNo} 出库失败: ${e.response?.data?.message||e.message}`) }
  }
  ElMessage.success('批量出库完成')
  batchVisible.value = false
  kanban.value = {}
  await loadStockKanbans()
}

async function queryKanban() {
  const v=kanbanNo.value.trim()
  if(!v) return ElMessage.warning('请输入或扫描库存看板号')
  try { kanban.value=(await getKanbanByNo(v)).data; outboundQty.value=Number(kanban.value.qty||1) }
  catch { kanban.value={} }
}

async function submit() {
  try { await directOutbound({kanbanNo:kanbanNo.value.trim(),qty:outboundQty.value,operator:operator.value||'admin'}); ElMessage.success('不带单出库成功'); kanbanNo.value=''; kanban.value={}; outboundQty.value=1; await loadStockKanbans() }
  catch(e) { ElMessage.error(e.response?.data?.message||e.message||'出库失败') }
}

async function openCamera() {
  cameraError.value=''; scanHandled=false; cameraVisible.value=true; await nextTick()
  try {
    scanner=new Html5Qrcode('direct-outbound-camera-reader')
    const cams=await Html5Qrcode.getCameras()
    if(!cams.length) throw new Error('未检测到可用摄像头')
    const cam=cams.find(i=>/back|rear|environment|后置/i.test(i.label))||cams[0]
    await scanner.start(cam.id,{fps:10,qrbox:{width:260,height:260}},handleCameraResult,()=>{})
  } catch(e) { cameraError.value=cameraMessage(e) }
}

async function handleCameraResult(t) { if(scanHandled)return; scanHandled=true; kanbanNo.value=t.trim(); await stopCamera(); cameraVisible.value=false; await queryKanban(); if(kanban.value.id) ElMessage.success('二维码识别成功') }

async function stopCamera() { if(!scanner)return; try{if(scanner.isScanning)await scanner.stop();await scanner.clear()}catch{}finally{scanner=null} }

function cameraMessage(e) {
  const m=String(e?.message||e||'')
  if(/permission|notallowed/i.test(m))return'摄像头权限被拒绝，请在浏览器设置中允许摄像头权限'
  if(/secure|https/i.test(m))return'摄像头需要 HTTPS 或 localhost 安全环境'
  return m||'摄像头启动失败，请检查设备是否被其他程序占用'
}

function statusLabel(v) { return{PRINTED:'待入库',SCANNED:'已入库',OUTBOUND:'已出库',REPACKED:'已转包',VOIDED:'已作废'}[v]||v }
function statusType(v) { return{PRINTED:'warning',SCANNED:'success',OUTBOUND:'info',REPACKED:'primary',VOIDED:'danger'}[v]||'info' }
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
