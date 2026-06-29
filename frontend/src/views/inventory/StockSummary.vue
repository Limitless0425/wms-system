<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="仓库">
          <el-select v-model="search.warehouseId" clearable placeholder="请选择仓库" style="width: 180px" @change="onWarehouseChange">
            <el-option v-for="item in warehouses" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位">
          <el-select v-model="search.locationId" clearable filterable placeholder="请选择库位" style="width: 170px">
            <el-option v-for="item in availableLocations" :key="item.id" :label="item.code" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="零件编号">
          <el-select v-model="search.partCode" clearable filterable placeholder="请选择零件" style="width: 190px">
            <el-option v-for="item in parts" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="search.supplierId" clearable filterable placeholder="请选择供应商" style="width: 190px">
            <el-option v-for="item in suppliers" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="queryStock">查询</el-button></el-form-item>
        <el-form-item><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="!showDetails">
      <template #header><strong>仓库库存容量概览</strong></template>
      <el-alert title="选择上方任一条件并查询，可查看具体库位和零件库存明细。" type="info" :closable="false" class="summary-tip" />
      <el-table :data="pagedWarehouseSummary" border stripe>
        <el-table-column prop="warehouseCode" label="仓库编号" width="120" />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="180" />
        <el-table-column prop="area" label="区域" width="120" />
        <el-table-column prop="locationCount" label="库位数量" width="100" />
        <el-table-column prop="stockQty" label="目前总库存量" width="130" />
        <el-table-column prop="capacity" label="可容纳库存量" width="130" />
        <el-table-column label="剩余容量" width="120">
          <template #default="{ row }">
            <span :class="{ over: row.remainingCapacity < 0 }">{{ row.remainingCapacity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="容量使用率" min-width="190">
          <template #default="{ row }">
            <el-progress :percentage="Math.min(row.usageRate, 100)" :status="row.usageRate >= 100 ? 'exception' : undefined">
              <span>{{ row.usageRate }}%</span>
            </el-progress>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="summaryPage" v-model:page-size="summaryPageSize" :page-sizes="[10,20,50,100]" :total="warehouseSummary.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
    </el-card>

    <el-card v-else>
      <template #header>
        <div class="header">
          <strong>库存明细</strong>
          <span class="result-count">共 {{ stockDetails.length }} 条</span>
        </div>
      </template>
      <el-table :data="pagedStockDetails" border stripe>
        <el-table-column prop="warehouseName" label="仓库" min-width="150" />
        <el-table-column prop="locationCode" label="库位编号" width="120" />
        <el-table-column prop="partCode" label="零件编号" width="120" />
        <el-table-column prop="partName" label="零件名称" min-width="150" />
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="qty" label="当前库存" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="locationCapacity" label="库位容量" width="100" />
        <el-table-column prop="lowStock" label="低储阈值" width="100" />
        <el-table-column label="高储阈值" width="100">
          <template #default="{ row }">{{ row.highStock || '未设置' }}</template>
        </el-table-column>
        <el-table-column label="库存状态" width="100">
          <template #default="{ row }"><el-tag :type="alertType(row.alert)">{{ row.alert }}</el-tag></template>
        </el-table-column>
      </el-table>
      <div class="pagination" v-if="stockDetails.length">
        <el-pagination v-model:current-page="detailPage" v-model:page-size="detailPageSize" :page-sizes="[10,20,50,100]" :total="stockDetails.length" layout="total, sizes, prev, pager, next, jumper" />
      </div>
      <el-empty v-if="!stockDetails.length" description="没有符合条件的库存记录" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getLocations, getParts, getSuppliers, getWarehouses } from '@/api/baseInfo'
import { getStockSummary, getWarehouseStockSummary } from '@/api/inventory'

const warehouses = ref([])
const locations = ref([])
const parts = ref([])
const suppliers = ref([])
const warehouseSummary = ref([])
const stockDetails = ref([])
const showDetails = ref(false)
const search = ref({ warehouseId: null, locationId: null, partCode: '', supplierId: null })
const summaryPage = ref(1)
const summaryPageSize = ref(10)
const detailPage = ref(1)
const detailPageSize = ref(10)

const availableLocations = computed(() => search.value.warehouseId
  ? locations.value.filter(item => item.warehouseId === search.value.warehouseId)
  : locations.value)
const pagedWarehouseSummary = computed(() => {
  const start = (summaryPage.value - 1) * summaryPageSize.value
  return warehouseSummary.value.slice(start, start + summaryPageSize.value)
})
const pagedStockDetails = computed(() => {
  const start = (detailPage.value - 1) * detailPageSize.value
  return stockDetails.value.slice(start, start + detailPageSize.value)
})

onMounted(async () => {
  const [warehouseRes, locationRes, partRes, supplierRes, summaryRes] = await Promise.all([
    getWarehouses(), getLocations(), getParts(), getSuppliers(), getWarehouseStockSummary()
  ])
  warehouses.value = warehouseRes.data
  locations.value = locationRes.data
  parts.value = partRes.data
  suppliers.value = supplierRes.data
  warehouseSummary.value = summaryRes.data
})

function hasSearchCondition() {
  const value = search.value
  return Boolean(value.warehouseId || value.locationId || value.partCode || value.supplierId)
}

async function queryStock() {
  if (!hasSearchCondition()) {
    showDetails.value = false
    warehouseSummary.value = (await getWarehouseStockSummary()).data
    summaryPage.value = 1
    return
  }
  stockDetails.value = (await getStockSummary(search.value)).data
  showDetails.value = true
  detailPage.value = 1
}

async function resetSearch() {
  search.value = { warehouseId: null, locationId: null, partCode: '', supplierId: null }
  stockDetails.value = []
  showDetails.value = false
  warehouseSummary.value = (await getWarehouseStockSummary()).data
  summaryPage.value = 1
}

function onWarehouseChange() {
  if (search.value.locationId && !availableLocations.value.some(item => item.id === search.value.locationId)) {
    search.value.locationId = null
  }
}

function alertType(value) {
  return { 缺货: 'danger', 低储: 'warning', 高储: 'primary', 正常: 'success' }[value] || 'info'
}
</script>

<style scoped>
.page-container { min-height: 300px; }
.search-card { margin-bottom: 16px; }
.summary-tip { margin-bottom: 16px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.result-count { color: #909399; font-size: 13px; }
.over { color: #f56c6c; font-weight: bold; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
