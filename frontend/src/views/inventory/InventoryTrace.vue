<template>
  <div class="page-container">
    <el-card style="margin-bottom:16px">
      <template #header><span>零件库存追溯</span></template>
      <el-select
        v-model="tracePartCode"
        filterable
        clearable
        placeholder="请选择零件编号"
        style="width:260px;margin-right:10px"
        @change="doTracePart"
      >
        <el-option
          v-for="part in parts"
          :key="part.id"
          :label="`${part.code} ${part.name || ''}`"
          :value="part.code"
        />
      </el-select>
      <el-button type="primary" @click="doTracePart">查询追溯</el-button>

      <div v-if="traceResult" style="margin-top:16px">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="零件编码">{{ traceResult.partCode }}</el-descriptions-item>
          <el-descriptions-item label="累计入库">{{ traceResult.totalInbound }}</el-descriptions-item>
          <el-descriptions-item label="累计出库">{{ traceResult.totalOutbound }}</el-descriptions-item>
          <el-descriptions-item label="当前库存">
            <span :style="{color:traceResult.currentStock<=0?'red':'#67c23a',fontWeight:'bold',fontSize:'18px'}">{{ traceResult.currentStock }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="traceResult.records" border stripe style="margin-top:10px" max-height="320">
          <el-table-column prop="kanbanNo" label="看板编号" width="220" />
          <el-table-column prop="sourceKanbanNo" label="来源看板" width="220" />
          <el-table-column prop="targetKanbanNo" label="目标看板" width="220" />
          <el-table-column prop="type" label="类型" width="110">
            <template #default="{ row }"><el-tag :type="recordType(row.type)">{{ row.typeLabel || recordLabel(row.type) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="当前状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column prop="locationName" label="库位" width="110" />
          <el-table-column prop="refOrderNo" label="关联单号" width="180" />
          <el-table-column prop="createTime" label="时间" width="180" />
        </el-table>
      </div>
    </el-card>

    <el-card>
      <template #header><span>看板追溯</span></template>
      <el-input v-model="traceKanbanNo" placeholder="输入普通看板或转包看板编号" style="width:300px;margin-right:10px" @keyup.enter="doTraceKanban" />
      <el-button type="primary" @click="doTraceKanban">查询</el-button>

      <el-table v-if="kanbanRecords.length" :data="kanbanRecords" border stripe style="margin-top:10px" max-height="320">
        <el-table-column prop="kanbanNo" label="看板编号" width="220" />
        <el-table-column prop="sourceKanbanNo" label="来源看板" width="220" />
        <el-table-column prop="targetKanbanNo" label="目标看板" width="220" />
        <el-table-column prop="partCode" label="零件编码" width="120" />
        <el-table-column prop="partName" label="零件名称" min-width="150" />
        <el-table-column prop="type" label="类型" width="110">
          <template #default="{ row }"><el-tag :type="recordType(row.type)">{{ row.typeLabel || recordLabel(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="当前状态" width="100">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="locationName" label="库位" width="110" />
        <el-table-column prop="refOrderNo" label="关联单号" width="180" />
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { traceByPart, traceByKanban } from '@/api/inventory'
import { getParts } from '@/api/baseInfo'

const tracePartCode = ref('')
const traceResult = ref(null)
const traceKanbanNo = ref('')
const kanbanRecords = ref([])
const parts = ref([])

onMounted(async () => {
  parts.value = (await getParts()).data || []
})

async function doTracePart() {
  if (!tracePartCode.value.trim()) return
  traceResult.value = (await traceByPart(tracePartCode.value.trim())).data
}

async function doTraceKanban() {
  if (!traceKanbanNo.value.trim()) return
  kanbanRecords.value = (await traceByKanban(traceKanbanNo.value.trim())).data
}

function recordLabel(type) {
  return {
    MANUAL_INBOUND: '手工入库',
    INBOUND: '扫码入库',
    OUTBOUND: '出库',
    OUTBOUND_DIRECT: '不带单出库',
    STOCK_KANBAN: '库存看板状态',
    OUTBOUND_KANBAN: '出库看板',
    RETURN_INBOUND: '退库入库',
    REPACK_KANBAN: '转包看板',
    REPACK_OUTBOUND: '转包出库',
    REPACK_INBOUND: '转包入库'
  }[type] || type
}

function recordType(type) {
  if (type === 'REPACK_KANBAN' || type === 'STOCK_KANBAN' || type === 'OUTBOUND_KANBAN') return 'warning'
  return ['OUTBOUND', 'OUTBOUND_DIRECT', 'REPACK_OUTBOUND'].includes(type) ? 'danger' : 'success'
}

function statusLabel(status) {
  return {
    PRINTED: '待扫描',
    SCANNED: '已入库',
    OUTBOUND: '已出库',
    VOIDED: '已作废',
    REPACKED: '已转包',
    REPACK_INBOUND: '转包入库',
    REPACK_OUTBOUND: '转包出库'
  }[status] || ''
}
</script>

<style scoped>
.page-container { min-height: 300px; }
</style>
