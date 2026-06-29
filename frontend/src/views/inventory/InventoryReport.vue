<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="库存状态">
          <el-select v-model="search.alert" clearable placeholder="选择" style="width: 150px">
            <el-option label="缺货" value="缺货" />
            <el-option label="低储" value="低储" />
            <el-option label="高储" value="高储" />
            <el-option label="正常" value="正常" />
          </el-select>
        </el-form-item>
        <el-form-item label="零件编号">
          <el-input v-model="search.partCode" clearable placeholder="输入零件编号" style="width: 160px" @keyup.enter="page = 1" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="search.supplier" clearable placeholder="输入供应商" style="width: 180px" @keyup.enter="page = 1" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="page = 1">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div class="header">
          <strong>总库存报表</strong>
          <span class="result-count">共 {{ filteredReport.length }} 条</span>
        </div>
      </template>
      <el-table :data="pagedReport" border stripe>
        <el-table-column prop="partCode" label="零件编号" width="120" />
        <el-table-column prop="partName" label="零件名称" min-width="160" />
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="qty" label="总库存" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="lowStock" label="低储阈值" width="100" />
        <el-table-column label="高储阈值" width="100">
          <template #default="{ row }">{{ row.highStock || '未设置' }}</template>
        </el-table-column>
        <el-table-column label="库存状态" width="110">
          <template #default="{ row }"><el-tag :type="alertType(row.alert)">{{ row.alert }}</el-tag></template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="filteredReport.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { getTotalStockReport } from '@/api/inventory'

const totalStockReport = ref([])
const page = ref(1)
const pageSize = ref(10)
const search = reactive({ alert: '', partCode: '', supplier: '' })

const filteredReport = computed(() => totalStockReport.value.filter(row => {
  const alertOk = !search.alert || row.alert === search.alert
  const partOk = contains(row.partCode, search.partCode)
  const supplierOk = contains(row.supplierName, search.supplier)
  return alertOk && partOk && supplierOk
}))

const pagedReport = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredReport.value.slice(start, start + pageSize.value)
})

watch(search, () => { page.value = 1 })

onMounted(load)

async function load() {
  totalStockReport.value = (await getTotalStockReport()).data || []
  page.value = 1
}

function resetSearch() {
  search.alert = ''
  search.partCode = ''
  search.supplier = ''
  page.value = 1
}

function contains(value, keyword) {
  return !keyword || String(value || '').toLowerCase().includes(String(keyword).trim().toLowerCase())
}

function alertType(value) {
  return { 缺货: 'danger', 低储: 'warning', 高储: 'primary', 正常: 'success' }[value] || 'info'
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.search-card { margin-bottom: 16px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.result-count { color: #909399; font-size: 13px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
