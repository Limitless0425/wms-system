<template>
  <div class="mobile-page">
    <div class="topbar">
      <el-button link @click="$router.push('/mobile')">返回</el-button>
      <strong>出库单</strong>
      <el-button link @click="loadOrders">刷新</el-button>
    </div>

    <el-empty v-if="!orders.length" description="暂无出库单" />
    <el-card v-for="order in orders" :key="order.id" class="order-card">
      <template #header>
        <div class="card-header">
          <strong>{{ order.orderNo }}</strong>
          <el-tag :type="statusType(order.status)">{{ statusLabel(order.status) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="供应商">{{ order.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ order.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库类型">{{ order.outboundType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ order.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions">
        <el-button type="primary" plain @click="viewKanbans(order)">查看看板</el-button>
        <el-button type="success" :disabled="!canExecute(order)" @click="executeOutbound(order)">执行出库</el-button>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="出库看板" width="92%">
      <el-table :data="kanbans" border stripe max-height="420">
        <el-table-column prop="kanbanNo" label="看板号" width="210" />
        <el-table-column prop="partCode" label="零件号" width="120" />
        <el-table-column prop="partName" label="零件名称" min-width="140" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="warehouseName" label="仓库" width="120" />
        <el-table-column prop="locationName" label="库位" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOutboundOrders, getOutboundOrderKanbans, scanOutbound } from '@/api/outbound'

const orders = ref([])
const kanbans = ref([])
const dialogVisible = ref(false)

onMounted(loadOrders)

async function loadOrders() {
  orders.value = (await getOutboundOrders()).data || []
}

async function viewKanbans(order) {
  kanbans.value = (await getOutboundOrderKanbans(order.id)).data || []
  dialogVisible.value = true
}

async function executeOutbound(order) {
  const list = (await getOutboundOrderKanbans(order.id)).data || []
  const pending = list.filter(item => item.status === 'PRINTED')
  if (!pending.length) return ElMessage.warning('没有可执行出库的待扫描看板')
  await ElMessageBox.confirm(`确定执行出库单 ${order.orderNo} 的 ${pending.length} 张看板吗？`, '执行出库', { type: 'warning' })
  for (const item of pending) {
    await scanOutbound({ kanbanNo: item.kanbanNo, operator: 'mobile-order' })
  }
  ElMessage.success('出库执行完成')
  await loadOrders()
}

function canExecute(order) {
  return !['COMPLETED', 'VOIDED'].includes(order.status)
}

function statusLabel(value) {
  return { PENDING: '待出库', PARTIAL: '部分出库', COMPLETED: '已完成', VOIDED: '已作废' }[value] || value
}

function statusType(value) {
  return { COMPLETED: 'success', VOIDED: 'danger', PARTIAL: 'warning', PENDING: 'primary' }[value] || 'info'
}
</script>

<style scoped>
.mobile-page { min-height: 100vh; background: #f5f7fb; padding: 12px; box-sizing: border-box; }
.topbar, .card-header, .actions { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.order-card { margin-bottom: 12px; }
.actions { margin-top: 12px; justify-content: flex-end; }
</style>
