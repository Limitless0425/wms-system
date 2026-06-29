<template>
  <div class="mobile-page">
    <div class="topbar">
      <el-button link @click="$router.push('/mobile')">返回</el-button>
      <strong>转包单</strong>
      <el-button link @click="loadOrders">刷新</el-button>
    </div>

    <el-empty v-if="!orders.length" description="暂无转包单" />
    <el-card v-for="order in orders" :key="order.id" class="order-card">
      <template #header>
        <div class="card-header">
          <strong>{{ order.orderNo }}</strong>
          <el-tag :type="statusType(order.status)">{{ statusLabel(order.status) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="供应商">{{ order.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="方向">{{ order.repackDirection || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源看板">{{ order.sourceKanbanNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ order.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions">
        <el-button type="primary" plain @click="viewKanbans(order)">查看看板</el-button>
        <el-button type="success" :disabled="!canExecute(order)" @click="executeRepack(order)">执行转包</el-button>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="转包看板" width="92%">
      <div class="dialog-actions">
        <el-button type="success" :disabled="!kanbans.some(canScan)" @click="recordAllKanbans">一键记录待记录看板</el-button>
      </div>
      <el-table :data="kanbans" border stripe max-height="420">
        <el-table-column prop="kanbanNo" label="看板号" width="210" />
        <el-table-column prop="partCode" label="零件号" width="120" />
        <el-table-column prop="partName" label="零件名称" min-width="140" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="targetContainerName" label="目标器具" width="130" />
        <el-table-column prop="warehouseName" label="仓库" width="120" />
        <el-table-column prop="locationName" label="库位" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ repackStatusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="success" link :disabled="!canScan(row)" @click="recordKanban(row)">记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { executeRepackOrder, getRepackOrderKanbans, getRepackOrders, scanRepackKanban } from '@/api/operations'

const orders = ref([])
const kanbans = ref([])
const dialogVisible = ref(false)

onMounted(loadOrders)

async function loadOrders() {
  orders.value = (await getRepackOrders()).data || []
}

async function viewKanbans(order) {
  kanbans.value = (await getRepackOrderKanbans(order.id)).data || []
  dialogVisible.value = true
}

async function executeRepack(order) {
  await ElMessageBox.confirm(`确定执行转包单 ${order.orderNo} 吗？`, '执行转包', { type: 'warning' })
  await executeRepackOrder(order.id)
  ElMessage.success('转包执行完成')
  await loadOrders()
}

async function recordKanban(row) {
  await scanRepackKanban(row.kanbanNo, { operator: 'mobile-order' })
  row.status = 'DONE'
  ElMessage.success(`已记录 ${row.kanbanNo}`)
  await loadOrders()
}

async function recordAllKanbans() {
  const pending = kanbans.value.filter(canScan)
  for (const row of pending) await recordKanban(row)
}

function canExecute(order) {
  return order.status === 'PENDING'
}

function canScan(row) {
  return row.status === 'PRINTED'
}

function statusLabel(value) {
  return { PENDING: '待转包', PROCESSING: '转包中', COMPLETED: '已完成', VOIDED: '作废' }[value] || value
}

function statusType(value) {
  return { COMPLETED: 'success', VOIDED: 'danger', PROCESSING: 'warning', PENDING: 'primary' }[value] || 'info'
}

function repackStatusLabel(value) {
  return { PRINTED: '待记录', REPACK_INBOUND: '转包入库', REPACK_OUTBOUND: '转包出库', REPACKED: '已转包', DONE: '已记录', VOIDED: '作废' }[value] || value
}
</script>

<style scoped>
.mobile-page { min-height: 100vh; background: #f5f7fb; padding: 12px; box-sizing: border-box; }
.topbar, .card-header, .actions { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.order-card { margin-bottom: 12px; }
.actions { margin-top: 12px; justify-content: flex-end; }
.dialog-actions { margin-bottom: 10px; text-align: right; }
</style>
