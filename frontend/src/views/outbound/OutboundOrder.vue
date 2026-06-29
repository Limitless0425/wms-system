<template>
  <div class="page-container">
    <el-card style="margin-bottom:16px">
      <el-form :inline="true">
        <el-form-item label="供应商"><el-select v-model="searchForm.supplier" placeholder="请选择供应商" clearable style="width:180px" @change="loadOrders"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.name" /></el-select></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="选择" style="width:130px" @change="loadOrders">
            <el-option label="待出库" value="PENDING" /><el-option label="部分出库" value="PARTIAL" />
            <el-option label="已完成" value="COMPLETED" /><el-option label="已作废" value="VOIDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="出库单号"><el-input v-model="searchForm.orderNo" placeholder="请输入出库单号" clearable style="width:170px" @keyup.enter="loadOrders" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadOrders">查询</el-button></el-form-item>
        <el-form-item><el-button @click="resetSearch">重置</el-button></el-form-item>
        <el-form-item><el-button type="success" @click="openCreate">新建出库单</el-button></el-form-item>
      </el-form>

    </el-card>

    <el-card style="margin-bottom:16px">
      <div class="table-toolbar">
        <span>共 {{ orders.length }} 条</span>
        <el-button type="danger" size="small" :disabled="!selectedOrders.length" @click="handleBatchDelete">批量删除</el-button>
      </div>
      <el-table :data="pagedOrders" border stripe @selection-change="selectedOrders = $event">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="orderNo" label="出库单号" width="190" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="supplierName" label="供应商" min-width="140" />
        <el-table-column prop="outboundType" label="出库类型" width="150" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="st(row.status)">{{ sl(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row:r }">
            <el-button size="small" type="success" @click="handlePrint(r)">打印</el-button>
            <el-button size="small" type="primary" @click="viewKanbans(r)">看板</el-button>
            <el-button size="small" type="warning" @click="handleManualOutbound(r)" v-if="r.status!=='COMPLETED'&&r.status!=='VOIDED'">手工出库</el-button>
            <el-button size="small" type="danger" @click="handleVoid(r)" v-if="r.status!=='COMPLETED'&&r.status!=='VOIDED'">作废</el-button>
            <el-button size="small" type="danger" @click="handleDelete(r)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="orders.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </el-card>

    <el-dialog title="关联出库看板" v-model="kanbanVisible" width="1200px">
      <div class="selection-tip">勾选看板后可批量打印</div>
      <el-table :data="currentKanbans" border stripe @selection-change="onKanbanSelectionChange" ref="kanbanTableRef">
        <el-table-column type="selection" width="50" />
        <el-table-column label="二维码" width="150"><template #default="{ row }"><img :src="row.qrDataUrl" style="width:120px;height:120px;display:block;margin:2px auto" /></template></el-table-column>
        <el-table-column prop="kanbanNo" label="看板编号" width="220" />
        <el-table-column prop="partCode" label="零件编码" width="120" />
        <el-table-column prop="partName" label="零件名称" min-width="140" />
        <el-table-column prop="actualQty" label="数量" width="80" />
        <el-table-column prop="warehouseName" label="仓库" min-width="120" />
        <el-table-column prop="locationName" label="库位" width="110" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="kbStatusType(row.status)" size="small">{{ kbStatusLabel(row.status) }}</el-tag></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="kanbanVisible=false">关闭</el-button>
        <el-button type="primary" @click="batchPrintKanbans" :disabled="!selectedKanbans.length">批量打印</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="1300px" @close="onDialogClose">
      <el-form :model="form" label-width="70px" :inline="true">
        <el-form-item label="客户">
          <el-select v-model="form.customerId" placeholder="请选择客户" filterable style="width:180px">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" placeholder="请选择" style="width:180px" @change="onSupplierChange">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="出库类型">
          <el-select v-model="form.outboundType" placeholder="选择" style="width:140px">
            <el-option v-for="t in outboundTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
      </el-form>

      <OrderItemBuilder
        v-if="form.supplierId"
        v-model="partList"
        :supplier-id="form.supplierId"
        :suppliers="suppliers"
        :parts="allParts"
        :warehouses="warehouses"
        :locations="allLocations"
        :containers="containers"
        qty-label="出库数量"
        mode="outbound"
        @pick="openBatchPicker"
      />

      <el-collapse v-if="false" style="margin-bottom:10px">
        <el-collapse-item title="批量导入物料（粘贴文本）">
          <el-input v-model="batchText" type="textarea" :rows="4" placeholder="一行一种零件，格式：零件编码,数量,仓库编码,库位编码" />
          <el-button type="primary" size="small" style="margin-top:6px" @click="parseBatch">解析并勾选</el-button>
        </el-collapse-item>
      </el-collapse>

      <el-table v-if="false" :data="partList" border stripe max-height="400" @selection-change="onSelectionChange" ref="partTableRef">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="code" label="零件编码" width="120" />
        <el-table-column prop="name" label="零件名称" min-width="140" />
        <el-table-column label="计划出库数量"><template #default="{ row }"><el-input-number v-model="row.planQty" :min="1" size="small" style="width:100px" /></template></el-table-column>
        <el-table-column label="仓库" width="160"><template #default="{ row }"><el-select v-model="row.warehouseId" placeholder="选择仓库" size="small" style="width:140px" @change="(v)=>onWhChange(row,v)"><el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" /></el-select></template></el-table-column>
        <el-table-column label="库位" width="160"><template #default="{ row }"><el-select v-model="row.locationId" placeholder="选择库位" size="small" style="width:140px"><el-option v-for="l in filterLocations(row.warehouseId)" :key="l.id" :label="l.code" :value="l.id" /></el-select></template></el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import { getOutboundOrders, createOutboundOrder, voidOutboundOrder, deleteOutboundOrder, getOutboundOrderKanbans } from '@/api/outbound'
import { getSuppliers, getParts, getWarehouses, getLocations as fetchLocations, getContainers, getCustomers } from '@/api/baseInfo'
import OrderItemBuilder from '@/components/OrderItemBuilder.vue'

const orders = ref([])
const currentKanbans = ref([])
const selectedKanbans = ref([])
const selectedOrders = ref([])
const kanbanTableRef = ref(null)
const page = ref(1)
const pageSize = ref(10)

const searchForm = reactive({ supplier: '', status: '', orderNo: '' })
const suppliers = ref([])
const customers = ref([])
const warehouses = ref([])
const allLocations = ref([])
const containers = ref([])
const allParts = ref([])

const dialogVisible = ref(false)
const dialogTitle = ref('新建出库单')
const kanbanVisible = ref(false)
const form = reactive({ customerId: null, supplierId: null, outboundType: '出库', remark: '', items: [] })
const partList = ref([])
const selectedParts = ref([])
const partTableRef = ref(null)
const batchText = ref('')
const outboundTypes = ref(['出库', '退货', '调账出库', '调账退货（无实物）'])
const router = useRouter()
const route = useRoute()
const draftKey = 'outbound-order-item-draft'
const pagedOrders = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return orders.value.slice(start, start + pageSize.value)
})

onMounted(async () => {
  const [supplierRes, customerRes, warehouseRes, locationRes, containerRes] = await Promise.all([
    getSuppliers(), getCustomers(), getWarehouses(), fetchLocations(), getContainers()
  ])
  suppliers.value = supplierRes.data
  customers.value = customerRes.data
  warehouses.value = warehouseRes.data
  allLocations.value = locationRes.data
  containers.value = containerRes.data
  await loadOrders()
  await restoreDraftFromRoute()
})
watch(() => route.query.restoreKey, async () => { await restoreDraftFromRoute() })

async function loadOrders() {
  const res = await getOutboundOrders(searchForm)
  orders.value = res.data
  page.value = 1
  selectedOrders.value = []
}

async function resetSearch() {
  searchForm.supplier = ''
  searchForm.status = ''
  searchForm.orderNo = ''
  await loadOrders()
}

function openCreate() {
  dialogTitle.value = '新建出库单'
  form.customerId = null
  form.supplierId = null
  form.outboundType = '出库'
  form.remark = ''
  form.items = []
  partList.value = []
  selectedParts.value = []
  batchText.value = ''
  dialogVisible.value = true
}

function onSelectionChange(rows) { selectedParts.value = rows }

async function onSupplierChange() {
  partList.value = []
  selectedParts.value = []
  batchText.value = ''
  if (!form.supplierId) return
  const partRes = await getParts()
  allParts.value = (partRes.data || []).filter(p => p.supplierId === form.supplierId)
  partList.value = []
}
function openBatchPicker() {
  if (!form.supplierId) { ElMessage.warning('请先选择供应商'); return }
  sessionStorage.setItem(draftKey, JSON.stringify({
    form: { ...form },
    items: partList.value
  }))
  dialogVisible.value = false
  router.push({
    name: 'OrderItemBatchPicker',
    query: { type: 'outbound', supplierId: form.supplierId, draftKey, returnName: 'OutboundOrder', qtyLabel: '出库数量' }
  })
}

async function restoreDraftFromRoute() {
  if (route.query.restoreKey !== draftKey) return
  const raw = sessionStorage.getItem(draftKey)
  if (!raw) return
  const draft = JSON.parse(raw)
  Object.assign(form, draft.form || {})
  if (form.supplierId) {
    const partRes = await getParts(form.supplierId)
    allParts.value = partRes.data || []
  }
  partList.value = draft.items || []
  dialogTitle.value = '新建出库单'
  dialogVisible.value = true
  router.replace({ name: 'OutboundOrder' })
}

function onWhChange(row, val) {
  row.locationId = null
}

function filterLocations(warehouseId) {
  if (!warehouseId) return []
  return allLocations.value.filter(l => l.warehouseId === warehouseId)
}

function parseBatch() {
  if (!batchText.value.trim()) return
  const lines = batchText.value.trim().split('\n').filter(l => l.trim())
  const parsed = []
  for (const line of lines) {
    const parts = line.split(',').map(s => s.trim())
    if (parts.length < 2) continue
    const code = parts[0]
    const qty = parseInt(parts[1]) || 1
    const whCode = parts[2] || ''
    const locCode = parts[3] || ''
    const item = partList.value.find(p => p.code === code)
    if (item) {
      item.planQty = qty
      if (whCode) {
        const wh = warehouses.value.find(w => w.code === whCode || w.name === whCode)
        if (wh) item.warehouseId = wh.id
      }
      if (locCode) {
        const loc = allLocations.value.find(l => l.code === locCode)
        if (loc) item.locationId = loc.id
      }
      parsed.push(item)
    }
  }
  if (parsed.length) {
    partTableRef.value?.clearSelection()
    parsed.forEach(p => partTableRef.value?.toggleRowSelection(p, true))
  }
  ElMessage.success(`解析 ${parsed.length} 条物料`)
}

async function handleSave() {
  if (!form.customerId) { ElMessage.warning('请选择客户'); return }
  if (!form.supplierId) { ElMessage.warning('请选择供应商'); return }

  const items = partList.value
    .filter(p => p.partId && p.planQty > 0)
    .map(p => {
      const wh = warehouses.value.find(x => x.id === p.warehouseId)
      const l = allLocations.value.find(x => x.id === p.locationId)
      return {
        partId: p.partId, partCode: p.partCode || p.code, partName: p.partName || p.name,
        unit: p.unit, planQty: p.planQty, actualQty: 0,
        supplierName: suppliers.value.find(s => s.id === form.supplierId)?.name || '',
        warehouseId: p.warehouseId || null, warehouseName: p.warehouseName || (wh ? wh.name : ''),
        locationId: p.locationId || null, locationName: p.locationName || (l ? l.code : '')
      }
    })
  if (!items.length) { ElMessage.warning('请勾选至少一种零件'); return }
  form.items = items
  try {
    await createOutboundOrder(form)
    ElMessage.success('出库单创建成功，已自动生成出库看板')
    dialogVisible.value = false
    await loadOrders()
  } catch (e) { ElMessage.error(e.response?.data?.message || '操作失败') }
}

function onDialogClose() {
  partList.value = []
  selectedParts.value = []
  batchText.value = ''
}

async function handleVoid(r) {
  await ElMessageBox.confirm('确定作废? 未出库的看板将同时作废', '提示', { type: 'warning' })
  await voidOutboundOrder(r.id)
  ElMessage.success('已作废')
  await loadOrders()
}

async function handleDelete(r) {
  await ElMessageBox.confirm('确定删除? 相关看板和库存记录将一并删除', '提示', { type: 'warning' })
  await deleteOutboundOrder(r.id)
  ElMessage.success('已删除')
  await loadOrders()
}
async function handleBatchDelete() {
  if (!selectedOrders.value.length) return
  await ElMessageBox.confirm(`确定批量删除 ${selectedOrders.value.length} 条出库单吗？`, '批量删除', { type: 'warning' })
  for (const row of selectedOrders.value) await deleteOutboundOrder(row.id)
  ElMessage.success('批量删除成功')
  await loadOrders()
}

function handlePrint(r) {
  viewKanbans(r)
  setTimeout(() => {
    if (currentKanbans.value.length) {
      kanbanTableRef.value?.toggleAllSelection()
    }
  }, 500)
}

async function viewKanbans(r) {
  const data = (await getOutboundOrderKanbans(r.id)).data
  currentKanbans.value = await Promise.all(data.map(async row => ({
    ...row,
    qrDataUrl: await QRCode.toDataURL(row.kanbanNo, { width: 200, margin: 1 })
  })))
  selectedKanbans.value = []
  kanbanVisible.value = true
}

function onKanbanSelectionChange(rows) { selectedKanbans.value = rows }

function batchPrintKanbans() {
  if (!selectedKanbans.value.length) return
  const escapeHtml = (v) => String(v ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char])
  const cards = selectedKanbans.value.map(k => `<section class="kanban-card"><h2>出库看板</h2><img src="${k.qrDataUrl}" alt="${escapeHtml(k.kanbanNo)}"><p class="kanban-no">${escapeHtml(k.kanbanNo)}</p><table><tr><td>零件</td><td>${escapeHtml(k.partCode)} ${escapeHtml(k.partName)}</td></tr><tr><td>数量</td><td>${escapeHtml(k.actualQty)} ${escapeHtml(k.unit||'')}</td></tr><tr><td>仓库</td><td>${escapeHtml(k.warehouseName||'-')}</td></tr><tr><td>库位</td><td>${escapeHtml(k.locationName||'-')}</td></tr><tr><td>出库单</td><td>${escapeHtml(k.orderNo||'-')}</td></tr></table></section>`).join('')
  const w = window.open('', '_blank')
  if (!w) { ElMessage.warning('浏览器阻止了打印窗口'); return }
  w.document.write(`<!doctype html><html><head><title>批量打印出库看板</title><style>body{margin:0;font-family:SimSun,serif}.kanban-card{box-sizing:border-box;width:100%;padding:24px;text-align:center;page-break-after:always}.kanban-card:last-child{page-break-after:auto}.kanban-card img{width:200px;height:200px}.kanban-no{font-size:18px;font-weight:bold}table{width:100%;border-collapse:collapse}td{border:1px solid #333;padding:8px;text-align:left}td:first-child{width:25%;text-align:center}</style></head><body>${cards}</body></html>`)
  w.document.close(); w.onload = () => { w.focus(); w.print() }
}

async function handleManualOutbound(r) {
  await ElMessageBox.confirm('手工出库将把剩余数量直接扣除库存，并删除所有未扫描看板。确定继续?', '手工出库', { type: 'warning' })
  ElMessage.info('手工出库功能开发中')
}

function sl(v) { return { PENDING: '待出库', PARTIAL: '部分出库', COMPLETED: '已完成', VOIDED: '已作废' }[v] || v }
function st(v) { return { PENDING: 'warning', PARTIAL: 'primary', COMPLETED: 'success', VOIDED: 'danger' }[v] || 'info' }
function kbStatusLabel(v) { return { PRINTED: '已打印', OUTBOUND: '已出库', VOIDED: '已作废' }[v] || v }
function kbStatusType(v) { return { PRINTED: 'primary', OUTBOUND: 'success', VOIDED: 'danger' }[v] || 'info' }
</script>

<style scoped>
.page-container { min-height: 300px; }
.selection-tip { margin-right: 16px; color: #606266; }
.table-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; color: #606266; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
