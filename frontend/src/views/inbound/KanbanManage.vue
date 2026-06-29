<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="状态">
          <el-select v-model="search.status" clearable placeholder="选择状态" style="width:150px">
            <el-option label="已入库" value="INBOUND" />
            <el-option label="未入库/待扫描" value="PENDING_SCAN" />
            <el-option label="待出库" value="PENDING_OUTBOUND" />
            <el-option label="已出库" value="OUTBOUND" />
            <el-option label="封存" value="SEALED" />
            <el-option label="转包入库" value="TRANSFER_INBOUND" />
            <el-option label="转包看板" value="REPACK" />
            <el-option label="待转包" value="REPACK_PENDING" />
            <el-option label="已转包" value="REPACKED" />
            <el-option label="已作废" value="VOIDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="单据号">
          <el-input v-model="search.inboundOrderNo" clearable placeholder="请输入单据号" style="width:170px" @keyup.enter="load({ resetPage: true, showLoading: true })" />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select v-model="search.warehouse" clearable filterable placeholder="选择仓库" style="width:160px">
            <el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="看板号">
          <el-input v-model="search.kanbanNo" clearable placeholder="请输入看板号" style="width:190px" @keyup.enter="load({ resetPage: true, showLoading: true })" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="search.supplier" clearable filterable placeholder="选择供应商" style="width:170px">
            <el-option v-for="item in suppliers" :key="item.id" :label="item.name" :value="item.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="零件号">
          <el-select v-model="search.partNo" clearable filterable placeholder="选择零件" style="width:180px">
            <el-option v-for="item in parts" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load({ resetPage: true, showLoading: true })">查询</el-button></el-form-item>
        <el-form-item><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="toolbar">
        <span v-if="selected.length">已选择 {{ selected.length }} 张看板</span>
        <span v-else>共 {{ kanbans.length }} 张看板</span>
        <div>
          <el-button type="primary" :disabled="!selected.length" @click="batchPrint">批量打印看板</el-button>
          <el-button type="success" @click="exportCsv">导出 CSV</el-button>
        </div>
      </div>
      <el-table ref="tableRef" v-loading="loading" :data="pagedKanbans" row-key="kanbanNo" border stripe empty-text="暂无看板记录" @selection-change="selected = $event">
        <el-table-column type="selection" width="45" reserve-selection />
        <el-table-column prop="businessLabel" label="看板类型" width="120" />
        <el-table-column prop="kanbanNo" label="看板号" width="210" />
        <el-table-column prop="orderNo" label="单据号" width="170" />
        <el-table-column prop="warehouseName" label="仓库" width="140" />
        <el-table-column prop="supplierName" label="供应商" width="160" />
        <el-table-column prop="partCode" label="零件号" width="110" />
        <el-table-column prop="partName" label="零件名称" min-width="140" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="containerCode" label="器具" width="110" />
        <el-table-column prop="locationName" label="库位" width="110" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><el-tag :type="statusType(row)">{{ statusLabel(row) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="printOne(row)">打印</el-button>
            <el-button v-if="canSeal(row) && !row.sealed" type="warning" link @click="seal(row)">封存</el-button>
            <el-button v-if="row.sealed" type="success" link @click="unseal(row)">解封</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="kanbans.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import { getParts, getSuppliers, getWarehouses } from '@/api/baseInfo'
import { getAllKanbans, sealKanban, unsealKanban } from '@/api/kanban'

const kanbans = ref([])
const warehouses = ref([])
const suppliers = ref([])
const parts = ref([])
const selected = ref([])
const tableRef = ref(null)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const search = ref({ status: '', inboundOrderNo: '', warehouse: '', kanbanNo: '', supplier: '', partNo: '' })
let refreshTimer = null
let requestSeq = 0

const pagedKanbans = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return kanbans.value.slice(start, start + pageSize.value)
})

onMounted(async () => {
  const [warehouseRes, supplierRes, partRes] = await Promise.all([getWarehouses(), getSuppliers(), getParts()])
  warehouses.value = warehouseRes.data || []
  suppliers.value = supplierRes.data || []
  parts.value = partRes.data || []
  await load({ resetPage: true, showLoading: true })
  window.addEventListener('focus', syncNow)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onActivated(() => {
  startAutoSync()
  syncNow()
})

onDeactivated(stopAutoSync)
onBeforeUnmount(() => {
  stopAutoSync()
  window.removeEventListener('focus', syncNow)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

async function load(options = {}) {
  const resetPage = options.resetPage !== false
  const showLoading = options.showLoading === true
  const keepSelection = options.keepSelection === true
  const currentSeq = ++requestSeq
  const selectedNos = keepSelection ? selected.value.map(row => row.kanbanNo) : []
  if (showLoading) loading.value = true
  try {
    const data = (await getAllKanbans(search.value)).data || []
    if (currentSeq !== requestSeq) return
    kanbans.value = data
    const maxPage = Math.max(1, Math.ceil(data.length / pageSize.value))
    if (page.value > maxPage) page.value = maxPage
    if (resetPage) page.value = 1
    await nextTick()
    tableRef.value?.clearSelection()
    if (keepSelection && selectedNos.length) {
      selected.value = data.filter(row => selectedNos.includes(row.kanbanNo))
      pagedKanbans.value.forEach(row => {
        if (selectedNos.includes(row.kanbanNo)) tableRef.value?.toggleRowSelection(row, true)
      })
    } else {
      selected.value = []
    }
  } finally {
    if (showLoading) loading.value = false
  }
}

async function resetSearch() {
  search.value = { status: '', inboundOrderNo: '', warehouse: '', kanbanNo: '', supplier: '', partNo: '' }
  await load({ resetPage: true, showLoading: true })
}

function startAutoSync() {
  stopAutoSync()
  refreshTimer = window.setInterval(() => {
    if (!document.hidden) syncNow()
  }, 2500)
}

function stopAutoSync() {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function syncNow() {
  return load({ resetPage: false, keepSelection: true })
}

function handleVisibilityChange() {
  if (!document.hidden) syncNow()
}

function canSeal(row) {
  return row.businessType === 'INBOUND' && row.canSeal && !['OUTBOUND', 'VOIDED', 'REPACKED'].includes(row.status)
}

function statusLabel(row) {
  if (row.sealed) return '已封存'
  if (row.businessType === 'OUTBOUND') return { PRINTED: '待出库', OUTBOUND: '已出库', VOIDED: '已作废' }[row.status] || statusFallback(row.status)
  if (row.businessType === 'REPACK') {
    return { PRINTED: '待扫描', BALANCE: '有结余', REPACKED: '已转包', REPACK_INBOUND: '转包入库', REPACK_OUTBOUND: '转包出库', VOIDED: '已作废' }[row.status] || statusFallback(row.status)
  }
  if (row.sourceKanbanNo && row.status === 'SCANNED') return '转包入库'
  return { PRINTED: '待入库', SCANNED: '已入库', OUTBOUND: '已出库', VOIDED: '已作废', REPACKED: '已转包', REPACK_INBOUND: '转包入库', REPACK_OUTBOUND: '转包出库' }[row.status] || statusFallback(row.status)
}

function statusFallback(status) {
  return {
    PENDING: '待处理',
    PROCESSING: '处理中',
    PARTIAL: '处理中',
    COMPLETED: '已完成'
  }[status] || status || '-'
}

function statusType(row) {
  if (row.sealed) return 'danger'
  return { PRINTED: 'warning', SCANNED: 'success', OUTBOUND: 'info', VOIDED: 'danger', REPACKED: 'primary', REPACK_INBOUND: 'success', REPACK_OUTBOUND: 'primary', BALANCE: 'warning' }[row.status] || 'info'
}

async function seal(row) {
  const { value } = await ElMessageBox.prompt('请输入封存原因', `封存看板 ${row.kanbanNo}`, { inputPlaceholder: '质量异常、待检等' })
  await sealKanban(row.id, value)
  ElMessage.success('看板已封存')
  await load({ resetPage: false, showLoading: true, keepSelection: true })
}

async function unseal(row) {
  await ElMessageBox.confirm(`确定解封看板 ${row.kanbanNo} 吗？`, '提示')
  await unsealKanban(row.id)
  ElMessage.success('看板已解封')
  await load({ resetPage: false, showLoading: true, keepSelection: true })
}

function printOne(row) {
  printCards([row])
}

function batchPrint() {
  printCards(selected.value)
}

async function printCards(rows) {
  if (!rows.length) return
  const cards = await Promise.all(rows.map(async row => `
    <section class="card">
      <h2>物料看板</h2>
      <img src="${await QRCode.toDataURL(row.kanbanNo, { width: 210, margin: 1 })}">
      <h3>${escapeHtml(row.kanbanNo)}</h3>
      <table>
        <tr><td>零件</td><td>${escapeHtml(row.partCode)} ${escapeHtml(row.partName)}</td></tr>
        <tr><td>数量</td><td>${escapeHtml(row.qty)} ${escapeHtml(row.unit || '')}</td></tr>
        <tr><td>器具</td><td>${escapeHtml(row.containerCode || '-')}</td></tr>
        <tr><td>库位</td><td>${escapeHtml(row.locationName || '-')}</td></tr>
        <tr><td>看板类型</td><td>${escapeHtml(row.businessLabel || '-')}</td></tr>
        <tr><td>单据号</td><td>${escapeHtml(row.orderNo || '-')}</td></tr>
      </table>
    </section>`))
  const win = window.open('', '_blank')
  if (!win) return ElMessage.warning('浏览器阻止了打印窗口，请允许弹出窗口')
  win.document.write(`<!doctype html><html><head><title>打印看板</title><style>
    body{margin:0;font-family:SimSun,serif}.card{box-sizing:border-box;padding:24px;text-align:center;page-break-after:always}
    .card:last-child{page-break-after:auto}.card img{width:210px;height:210px}table{width:100%;border-collapse:collapse}
    td{border:1px solid #333;padding:8px;text-align:left}td:first-child{width:25%;text-align:center}
  </style></head><body>${cards.join('')}</body></html>`)
  win.document.close()
  win.onload = () => { win.focus(); win.print() }
}

function exportCsv() {
  const rows = kanbans.value.map(row => [
    row.businessLabel,
    row.kanbanNo,
    row.orderNo,
    row.warehouseName,
    row.supplierName,
    row.partCode,
    row.partName,
    row.qty,
    statusLabel(row)
  ])
  const header = ['看板类型', '看板号', '单据号', '仓库', '供应商', '零件号', '零件名称', '数量', '状态']
  const csv = [header, ...rows].map(row => row.map(csvCell).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '看板列表.csv'
  link.click()
  URL.revokeObjectURL(url)
}

function csvCell(value) {
  return `"${String(value ?? '').replace(/"/g, '""')}"`
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]))
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.search-card { margin-bottom: 16px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; color: #606266; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
