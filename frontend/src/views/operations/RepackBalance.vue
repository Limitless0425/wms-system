<template>
  <div class="page-container">
    <el-card style="margin-bottom:16px">
      <el-form :inline="true">
        <el-form-item label="转包单号">
          <el-input v-model="search.orderNo" clearable placeholder="请输入转包单号" style="width:180px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item label="零件编号">
          <el-input v-model="search.partCode" clearable placeholder="请输入零件编号" style="width:180px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="pagedRows" border stripe>
        <el-table-column prop="orderNo" label="转包单号" width="190" />
        <el-table-column prop="partCode" label="零件编号" width="140" />
        <el-table-column prop="partName" label="零件名称" min-width="160" />
        <el-table-column prop="supplierName" label="供应商" min-width="140" />
        <el-table-column prop="planQty" label="计划转包" width="110" />
        <el-table-column prop="actualQty" label="已完成" width="100" />
        <el-table-column prop="balanceQty" label="转包结余" width="110">
          <template #default="{ row }">
            <el-tag :type="row.balanceQty > 0 ? 'warning' : 'success'">{{ row.balanceQty }} {{ row.unit || '' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="rows.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getRepackBalances } from '@/api/operations'

const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const search = reactive({ orderNo: '', partCode: '' })
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

onMounted(loadData)

async function loadData() {
  rows.value = (await getRepackBalances(search)).data || []
  page.value = 1
}

function resetSearch() {
  search.orderNo = ''
  search.partCode = ''
  loadData()
}

function statusLabel(value) {
  return { PENDING: '待转包', PROCESSING: '转包中', COMPLETED: '已完成', VOIDED: '作废' }[value] || value
}

function statusType(value) {
  return { PENDING: 'warning', PROCESSING: 'primary', COMPLETED: 'success', VOIDED: 'danger' }[value] || 'info'
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
