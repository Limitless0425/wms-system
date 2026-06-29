<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="供应商">
          <el-select v-model="searchForm.supplier" clearable filterable placeholder="选择供应商" style="width:180px" @change="loadOrders">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="选择状态" style="width:130px" @change="loadOrders">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="部分入库" value="PARTIAL" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已作废" value="VOIDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库单号">
          <el-input v-model="searchForm.inboundOrderNo" clearable placeholder="请输入入库单号" style="width:170px" @keyup.enter="loadOrders" />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="searchForm.orderNo" clearable placeholder="请输入订单号" style="width:160px" @keyup.enter="loadOrders" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="loadOrders">查询</el-button></el-form-item>
        <el-form-item><el-button @click="resetSearch">重置</el-button></el-form-item>
        <el-form-item><el-button type="success" @click="openCreate">新建入库单</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-toolbar">
        <span>共 {{ orders.length }} 条</span>
        <el-button type="danger" size="small" :disabled="!selectedOrders.length" @click="handleBatchDelete">批量删除</el-button>
      </div>
      <el-table :data="pagedOrders" border stripe @selection-change="selectedOrders = $event">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="orderNo" label="入库单号" width="170" />
        <el-table-column prop="sourceOrderNo" label="订单号" width="150" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="inboundType" label="入库类型" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)" v-if="row.status === 'DRAFT' || row.status === 'CONFIRMED'">修改</el-button>
            <el-button size="small" type="success" @click="handlePrint(row)">打印</el-button>
            <el-button size="small" type="primary" @click="viewKanbans(row)">看板</el-button>
            <el-button size="small" type="warning" @click="handleManualInbound(row)" v-if="row.status !== 'COMPLETED' && row.status !== 'VOIDED'">手工入库</el-button>
            <el-button size="small" type="danger" @click="handleVoid(row)" v-if="row.status !== 'COMPLETED' && row.status !== 'VOIDED'">作废</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" v-if="row.status !== 'VOIDED'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="orders.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>

    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="1300px" @close="onDialogClose">
      <el-form :model="form" label-width="80px" :inline="true">
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" filterable placeholder="请选择供应商" style="width:200px" @change="onSupplierChange">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库类型">
          <el-select v-model="form.inboundType" placeholder="选择类型" style="width:150px">
            <el-option label="正常入库" value="正常入库" />
            <el-option label="整托入库" value="整托入库" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="form.sourceOrderNo" placeholder="来源订单号" style="width:180px" />
        </el-form-item>
      </el-form>

      <el-alert v-if="form.supplierId && storagePreference" :title="storagePreferenceTip" type="info" :closable="false" class="mb-12" />

      <OrderItemBuilder
        v-if="form.supplierId"
        v-model="partList"
        :supplier-id="form.supplierId"
        :suppliers="suppliers"
        :parts="allParts"
        :warehouses="warehouses"
        :locations="allLocations"
        :containers="containers"
        qty-label="入库数量"
        mode="inbound"
        @pick="openBatchPicker"
      />
      <el-empty v-else description="请先选择供应商" />

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="入库单打印" v-model="printVisible" width="760px">
      <div id="print-area" class="print-area" v-if="printOrder">
        <h2>入库单</h2>
        <p>单号：{{ printOrder.orderNo }}　供应商：{{ printOrder.supplierName }}</p>
        <p>类型：{{ printOrder.inboundType }}　日期：{{ printOrder.createTime }}</p>
        <table border="1" cellpadding="4" cellspacing="0">
          <thead><tr><th>零件编号</th><th>零件名称</th><th>规格</th><th>单位</th><th>计划数量</th><th>仓库</th><th>库位</th><th>器具</th></tr></thead>
          <tbody>
            <tr v-for="item in printOrder.items || []" :key="item.id || item.partId">
              <td>{{ item.partCode }}</td><td>{{ item.partName }}</td><td>{{ item.spec }}</td><td>{{ item.unit }}</td><td>{{ item.planQty }}</td><td>{{ item.warehouseName || '-' }}</td><td>{{ item.locationName || '-' }}</td><td>{{ item.containerCode || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer><el-button @click="printVisible = false">关闭</el-button><el-button type="primary" @click="doPrint">打印</el-button></template>
    </el-dialog>

    <el-dialog title="关联看板" v-model="kanbanVisible" width="1200px">
      <div class="selection-tip">勾选看板后可批量打印</div>
      <el-table :data="currentKanbans" border stripe @selection-change="onKanbanSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column label="二维码" width="150">
          <template #default="{ row }"><img :src="row.qrDataUrl" class="kanban-qr" /></template>
        </el-table-column>
        <el-table-column prop="kanbanNo" label="看板编号" width="220" />
        <el-table-column prop="partCode" label="零件编号" width="120" />
        <el-table-column prop="partName" label="零件名称" min-width="140" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="warehouseName" label="仓库" min-width="120" />
        <el-table-column prop="locationName" label="库位" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="linkedKanbanStatusType(row.status)" size="small">{{ linkedKanbanStatusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="封存" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canSealKanban(row)" type="warning" link @click="sealLinkedKanban(row)">封存</el-button>
            <el-button v-if="row.sealed" type="success" link @click="unsealLinkedKanban(row)">解封</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="kanbanVisible = false">关闭</el-button>
        <el-button type="primary" @click="batchPrintKanbans" :disabled="!selectedKanbans.length">批量打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import { getSuppliers, getWarehouses, getContainers, getParts, getLocations } from '@/api/baseInfo'
import {
  getInboundOrders,
  getInboundOrder,
  createInboundOrder,
  updateInboundOrder,
  deleteInboundOrder,
  voidInboundOrder,
  manualInboundOrder,
  getOrderKanbans,
  getInboundSupplierStorage
} from '@/api/inbound'
import { sealKanban, unsealKanban } from '@/api/kanban'
import OrderItemBuilder from '@/components/OrderItemBuilder.vue'

const router = useRouter()
const route = useRoute()
const draftKey = 'inbound-order-item-draft'

const orders = ref([])
const suppliers = ref([])
const warehouses = ref([])
const containers = ref([])
const allParts = ref([])
const allLocations = ref([])
const partList = ref([])
const selectedOrders = ref([])
const page = ref(1)
const pageSize = ref(10)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const printVisible = ref(false)
const printOrder = ref(null)
const kanbanVisible = ref(false)
const currentKanbans = ref([])
const currentKanbanOrderId = ref(null)
const selectedKanbans = ref([])
const storagePreference = ref(null)
const searchForm = ref({ supplier: '', status: '', inboundOrderNo: '', orderNo: '' })
const form = ref(emptyForm())

const pagedOrders = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return orders.value.slice(start, start + pageSize.value)
})
const storagePreferenceTip = computed(() => {
  const pref = storagePreference.value
  if (!pref) return ''
  const warehouseName = pref.recommendedWarehouseName || '默认仓库'
  if (pref.hasInboundOrders) {
    return `该供应商已有入库记录，系统推荐存放到 ${warehouseName}；如果改选其他仓库，保存时会确认是否更改以后首选仓库。`
  }
  return `该供应商还没有入库单，系统先推荐 ${warehouseName}；本次可直接改选仓库，保存后会作为以后首选仓库。`
})

onMounted(async () => {
  const [supplierRes, warehouseRes, containerRes, locationRes] = await Promise.all([getSuppliers(), getWarehouses(), getContainers(), getLocations()])
  suppliers.value = supplierRes.data || []
  warehouses.value = warehouseRes.data || []
  containers.value = containerRes.data || []
  allLocations.value = locationRes.data || []
  applyRouteSearch()
  await loadOrders()
  await restoreDraftFromRoute()
})
watch(() => route.query.restoreKey, restoreDraftFromRoute)
watch(() => route.query.inboundOrderNo, async () => {
  applyRouteSearch()
  await loadOrders()
})

function emptyForm() {
  return { supplierId: null, supplierName: '', sourceOrderNo: '', inboundType: '正常入库', items: [] }
}

function applyRouteSearch() {
  const inboundOrderNo = String(route.query.inboundOrderNo || '').trim()
  if (!inboundOrderNo) return
  searchForm.value = { ...searchForm.value, inboundOrderNo }
}

async function loadOrders() {
  orders.value = (await getInboundOrders(searchForm.value)).data || []
  page.value = 1
  selectedOrders.value = []
}

function resetSearch() {
  searchForm.value = { supplier: '', status: '', inboundOrderNo: '', orderNo: '' }
  loadOrders()
}

function statusType(status) {
  return { DRAFT: 'info', CONFIRMED: 'warning', PARTIAL: '', COMPLETED: 'success', VOIDED: 'danger' }[status] || ''
}

function statusLabel(status) {
  return { DRAFT: '草稿', CONFIRMED: '已确认', PARTIAL: '部分入库', COMPLETED: '已完成', VOIDED: '已作废' }[status] || status
}

async function onSupplierChange(value) {
  const supplier = suppliers.value.find(item => item.id === value) || {}
  form.value.supplierName = supplier.name || ''
  storagePreference.value = value ? (await getInboundSupplierStorage(value)).data : null
  allParts.value = value ? (await getParts(value)).data || [] : []
  if (!isEdit.value) partList.value = []
}

function openCreate() {
  dialogTitle.value = '新建入库单'
  isEdit.value = false
  form.value = emptyForm()
  storagePreference.value = null
  allParts.value = []
  partList.value = []
  dialogVisible.value = true
}

async function openEdit(row) {
  dialogTitle.value = '修改入库单'
  isEdit.value = true
  const orderData = (await getInboundOrder(row.id)).data
  form.value = JSON.parse(JSON.stringify(orderData))
  storagePreference.value = form.value.supplierId ? (await getInboundSupplierStorage(form.value.supplierId)).data : null
  allParts.value = form.value.supplierId ? (await getParts(form.value.supplierId)).data || [] : []
  partList.value = (orderData.items || []).map(item => ({
    partId: item.partId,
    id: item.partId,
    code: item.partCode,
    name: item.partName,
    partCode: item.partCode,
    partName: item.partName,
    spec: item.spec,
    unit: item.unit,
    planQty: item.planQty,
    warehouseId: item.warehouseId,
    warehouseName: item.warehouseName,
    containerId: item.containerId,
    containerCode: item.containerCode,
    containerName: item.containerName,
    locationId: item.locationId,
    locationName: item.locationName
  }))
  dialogVisible.value = true
}

function openBatchPicker() {
  if (!form.value.supplierId) {
    ElMessage.warning('请先选择供应商')
    return
  }
  sessionStorage.setItem(draftKey, JSON.stringify({ form: form.value, items: partList.value, isEdit: isEdit.value }))
  dialogVisible.value = false
  router.push({ name: 'OrderItemBatchPicker', query: { type: 'inbound', supplierId: form.value.supplierId, draftKey, returnName: 'InboundOrder', qtyLabel: '入库数量' } })
}

async function restoreDraftFromRoute() {
  if (route.query.restoreKey !== draftKey) return
  const raw = sessionStorage.getItem(draftKey)
  if (!raw) return
  const draft = JSON.parse(raw)
  isEdit.value = !!draft.isEdit
  dialogTitle.value = isEdit.value ? '修改入库单' : '新建入库单'
  form.value = { ...draft.form }
  storagePreference.value = form.value.supplierId ? (await getInboundSupplierStorage(form.value.supplierId)).data : null
  allParts.value = form.value.supplierId ? (await getParts(form.value.supplierId)).data || [] : []
  partList.value = draft.items || []
  dialogVisible.value = true
  router.replace({ name: 'InboundOrder' })
}

function onDialogClose() {
  if (!isEdit.value) loadOrders()
}

async function confirmSupplierWarehouseChange(items) {
  if (isEdit.value) return true
  const pref = storagePreference.value
  if (!pref?.hasInboundOrders || !pref.recommendedWarehouseId) return true
  const selectedWarehouseId = items.find(item => item.warehouseId && item.warehouseId !== pref.recommendedWarehouseId)?.warehouseId
  if (!selectedWarehouseId) return true
  const selectedWarehouse = warehouses.value.find(item => item.id === selectedWarehouseId)
  try {
    await ElMessageBox.confirm(
      `该供应商的货物已存放在 ${pref.recommendedWarehouseName || '原推荐仓库'}，是否确定更改以后的存放位置为 ${selectedWarehouse?.name || '新仓库'}？`,
      '更改供应商首选仓库',
      { type: 'warning', confirmButtonText: '确定更改', cancelButtonText: '取消' }
    )
    form.value.warehouseId = selectedWarehouseId
    form.value.warehouseName = selectedWarehouse?.name || ''
    return true
  } catch {
    return false
  }
}

function applySelectedWarehouseHeader(items) {
  const item = items.find(row => row.warehouseId)
  if (!item) return
  const warehouse = warehouses.value.find(row => row.id === item.warehouseId)
  form.value.warehouseId = item.warehouseId
  form.value.warehouseName = warehouse?.name || item.warehouseName || ''
}

async function handleSave() {
  const items = partList.value
    .filter(item => item.partId && Number(item.planQty || 0) > 0)
    .map(item => {
      const warehouse = warehouses.value.find(row => row.id === item.warehouseId)
      const container = containers.value.find(row => row.id === item.containerId)
      const location = allLocations.value.find(row => row.id === item.locationId)
      return {
        partId: item.partId,
        partCode: item.partCode || item.code,
        partName: item.partName || item.name,
        spec: item.spec,
        unit: item.unit,
        planQty: Number(item.planQty || 0),
        actualQty: item.actualQty || 0,
        warehouseId: item.warehouseId,
        warehouseName: warehouse?.name || item.warehouseName || '',
        locationId: item.locationId,
        locationName: location?.code || item.locationName || '',
        containerId: item.containerId,
        containerCode: container?.code || item.containerCode || '',
        containerName: container?.name || item.containerName || ''
      }
    })
  if (!items.length) {
    ElMessage.warning('请至少添加一条明细')
    return
  }
  if (!(await confirmSupplierWarehouseChange(items))) return
  applySelectedWarehouseHeader(items)
  form.value.items = items
  try {
    if (isEdit.value) await updateInboundOrder(form.value)
    else await createInboundOrder(form.value)
    ElMessage.success(isEdit.value ? '修改成功' : '创建成功')
    dialogVisible.value = false
    await loadOrders()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定删除该入库单吗？', '提示', { type: 'warning' })
  await deleteInboundOrder(row.id)
  ElMessage.success('删除成功')
  await loadOrders()
}

async function handleBatchDelete() {
  const rows = selectedOrders.value.filter(row => row.status !== 'VOIDED')
  if (!rows.length) return ElMessage.warning('请选择可删除的入库单')
  await ElMessageBox.confirm(`确定批量删除 ${rows.length} 条入库单吗？`, '批量删除', { type: 'warning' })
  for (const row of rows) await deleteInboundOrder(row.id)
  ElMessage.success('批量删除成功')
  await loadOrders()
}

async function handleVoid(row) {
  await ElMessageBox.confirm('确定作废该入库单吗？未入库的看板也会同步作废。', '提示', { type: 'warning' })
  await voidInboundOrder(row.id)
  ElMessage.success('已作废')
  await loadOrders()
}

async function handleManualInbound(row) {
  await ElMessageBox.confirm('手工入库会把剩余数量直接计入库存，并删除所有未扫描入库看板。确定继续吗？', '手工入库', { type: 'warning' })
  const res = await manualInboundOrder(row.id)
  ElMessage.success(res.message || '手工入库完成')
  await loadOrders()
}

async function handlePrint(row) {
  printOrder.value = (await getInboundOrder(row.id)).data
  printVisible.value = true
}

function doPrint() {
  const win = window.open('', '_blank')
  if (!win) return ElMessage.warning('浏览器阻止了打印窗口')
  win.document.write(document.getElementById('print-area').innerHTML)
  win.document.close()
  win.print()
}

async function viewKanbans(row) {
  const data = (await getOrderKanbans(row.id)).data || []
  currentKanbanOrderId.value = row.id
  currentKanbans.value = await Promise.all(data.map(async item => ({ ...item, qrDataUrl: await QRCode.toDataURL(item.kanbanNo, { width: 200, margin: 1 }) })))
  selectedKanbans.value = []
  kanbanVisible.value = true
}

function onKanbanSelectionChange(rows) {
  selectedKanbans.value = rows
}

function canSealKanban(row) {
  return !row.sealed && !['OUTBOUND', 'VOIDED', 'REPACKED'].includes(row.status)
}

function linkedKanbanStatusLabel(status) {
  return { PRINTED: '待入库', SCANNED: '已入库', OUTBOUND: '已出库', REPACKED: '已转包', VOIDED: '已作废' }[status] || status
}

function linkedKanbanStatusType(status) {
  return { PRINTED: 'warning', SCANNED: 'success', OUTBOUND: 'info', REPACKED: 'primary', VOIDED: 'danger' }[status] || 'info'
}

async function reloadLinkedKanbans() {
  if (!currentKanbanOrderId.value) return
  const data = (await getOrderKanbans(currentKanbanOrderId.value)).data || []
  currentKanbans.value = await Promise.all(data.map(async item => ({ ...item, qrDataUrl: await QRCode.toDataURL(item.kanbanNo, { width: 200, margin: 1 }) })))
}

async function sealLinkedKanban(row) {
  const { value } = await ElMessageBox.prompt('请输入封存原因', `封存看板 ${row.kanbanNo}`, { inputPlaceholder: '质量异常、待检等' })
  await sealKanban(row.id, value)
  ElMessage.success('看板已封存')
  await reloadLinkedKanbans()
}

async function unsealLinkedKanban(row) {
  await ElMessageBox.confirm(`确定解封看板 ${row.kanbanNo} 吗？`, '提示')
  await unsealKanban(row.id)
  ElMessage.success('看板已解封')
  await reloadLinkedKanbans()
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]))
}

function batchPrintKanbans() {
  if (!selectedKanbans.value.length) return
  const cards = selectedKanbans.value.map(item => `
    <section class="kanban-card">
      <h2>入库看板</h2>
      <img src="${item.qrDataUrl}" alt="${escapeHtml(item.kanbanNo)}">
      <p class="kanban-no">${escapeHtml(item.kanbanNo)}</p>
      <table>
        <tr><td>零件</td><td>${escapeHtml(item.partCode)} ${escapeHtml(item.partName)}</td></tr>
        <tr><td>数量</td><td>${escapeHtml(item.qty)} ${escapeHtml(item.unit || '')}</td></tr>
        <tr><td>器具</td><td>${escapeHtml(item.containerCode || '-')}</td></tr>
        <tr><td>库位</td><td>${escapeHtml(item.locationName || '-')}</td></tr>
        <tr><td>入库单</td><td>${escapeHtml(item.orderNo || '-')}</td></tr>
      </table>
    </section>`).join('')
  const win = window.open('', '_blank')
  if (!win) return ElMessage.warning('浏览器阻止了打印窗口')
  win.document.write(`<!doctype html><html><head><title>批量打印看板</title><style>body{margin:0;font-family:SimSun,serif}.kanban-card{box-sizing:border-box;width:100%;padding:24px;text-align:center;page-break-after:always}.kanban-card:last-child{page-break-after:auto}.kanban-card img{width:200px;height:200px}.kanban-no{font-size:18px;font-weight:bold}table{width:100%;border-collapse:collapse}td{border:1px solid #333;padding:8px;text-align:left}td:first-child{width:25%;text-align:center}</style></head><body>${cards}</body></html>`)
  win.document.close()
  win.onload = () => { win.focus(); win.print() }
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.search-card { margin-bottom: 16px; }
.table-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; color: #606266; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
.mb-12 { margin-bottom: 12px; }
.kanban-qr { display: block; width: 120px; height: 120px; margin: 2px auto; }
.selection-tip { margin-bottom: 10px; color: #606266; }
.print-area { padding: 10px; font-family: SimSun, serif; }
.print-area h2 { text-align: center; }
.print-area table { width: 100%; border-collapse: collapse; margin-top: 10px; }
</style>
