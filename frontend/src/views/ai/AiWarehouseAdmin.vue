<template>
  <div class="ai-admin-page">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="metric-card">
          <div class="metric-label">总库存数量</div>
          <div class="metric-value">{{ summary.totalQty || 0 }}</div>
          <div class="metric-sub">零件 {{ summary.partCount || 0 }} 种</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="metric-card warning">
          <div class="metric-label">缺货/低储</div>
          <div class="metric-value">{{ (summary.outOfStockCount || 0) + (summary.lowStockCount || 0) }}</div>
          <div class="metric-sub">缺货 {{ summary.outOfStockCount || 0 }}，低储 {{ summary.lowStockCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="metric-card danger">
          <div class="metric-label">AI预警</div>
          <div class="metric-value">{{ forecast.length }}</div>
          <div class="metric-sub">未来缺货、呆滞、积压</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="metric-card">
          <div class="metric-label">大模型</div>
          <div class="metric-value small">{{ modelStatus.enabled ? '已接入' : '本地规则' }}</div>
          <div class="metric-sub">{{ modelStatus.model || '未配置外部模型' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="main-row">
      <el-col :xs="24" :lg="15">
        <el-card class="chat-card">
          <template #header>
            <div class="card-header">
              <strong>AI仓库管理员</strong>
              <div class="header-actions">
<!--                <span class="muted">一句话查询或执行仓库操作，写操作需要确认</span>-->
                <el-button size="small" type="primary" plain @click="openConfig">AI配置</el-button>
              </div>
            </div>
          </template>

          <div class="quick-questions">
            <el-button v-for="item in quickQuestions" :key="item" size="small" @click="send(item)">
              {{ item }}
            </el-button>
          </div>

          <div class="messages">
            <div v-for="message in messages" :key="message.id" :class="['message', message.role, { loading: message.loading }]">
              <div class="bubble">
                <div class="message-text">{{ message.text }}</div>

                <div v-if="message.plan" class="plan-box">
                  <div class="plan-title">
                    <el-tag :type="riskType(message.plan.risk)" size="small">{{ message.plan.risk }}风险</el-tag>
                    <strong>{{ message.plan.title }}</strong>
                  </div>
                  <div class="plan-summary">{{ message.plan.summary }}</div>
                  <el-descriptions :column="2" size="small" border class="plan-params">
                    <el-descriptions-item v-for="item in planParams(message.plan.params)" :key="item.key" :label="item.key">
                      {{ item.value }}
                    </el-descriptions-item>
                  </el-descriptions>
                  <el-table v-if="planDetailRows(message.plan.params).length" :data="planDetailRows(message.plan.params)" border stripe size="small" class="plan-detail-table">
                    <el-table-column prop="partCode" label="零件编号" width="110" />
                    <el-table-column prop="partName" label="零件名称" min-width="130" />
                    <el-table-column prop="qty" label="数量" width="80" />
                    <el-table-column prop="unit" label="单位" width="70" />
                    <el-table-column prop="warehouse" label="仓库" min-width="120" />
                    <el-table-column prop="location" label="库位" width="110" />
                    <el-table-column prop="container" label="器具" min-width="140" />
                    <el-table-column prop="containerCapacity" label="容量" width="80" />
                    <el-table-column prop="containerUsage" label="器具使用情况" min-width="190" />
                  </el-table>
                  <el-alert
                    v-if="message.plan.missing?.length"
                    type="warning"
                    show-icon
                    :closable="false"
                    :title="`还缺少：${message.plan.missing.join('、')}`"
                  />
                  <div v-else class="plan-actions">
                    <el-button type="primary" :loading="executingPlanId === message.plan.planId" @click="execute(message.plan.planId)">
                      确认执行
                    </el-button>
                    <el-button @click="message.plan = null">取消</el-button>
                  </div>
                </div>

                <el-table v-if="message.items?.length" :data="message.items" border stripe class="result-table">
                  <el-table-column prop="kind" label="类型" width="100" />
                  <el-table-column prop="partCode" label="零件编号" width="120" />
                  <el-table-column prop="partName" label="零件名称" min-width="140" />
                  <el-table-column prop="kanbanNo" label="看板号" min-width="170" />
                  <el-table-column prop="orderNo" label="单据号" min-width="150" />
                  <el-table-column prop="qty" label="数量" width="90" />
                  <el-table-column label="状态" width="110">
                    <template #default="{ row }">{{ row.alertText || row.status || row.type || '-' }}</template>
                  </el-table-column>
                                    <el-table-column prop="message" label="说明" min-width="180" />
                  <el-table-column label="操作" width="120" fixed="right">
                    <template #default="{ row }">
                      <el-button v-if="row.routeName" type="primary" link @click="goResult(row)">
                        {{ row.actionText || '查看' }}
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </div>

          <div class="input-area">
            <el-input
              v-model="question"
              type="textarea"
              :rows="3"
              maxlength="160"
              show-word-limit
              placeholder="例如：扫码入库 KBxxx；新建广州电装 PT005 数量100 的出库单给某客户；查询 PT005 未来会不会缺货"
              @keydown.ctrl.enter="send()"
            />
            <div class="ask-actions">
              <el-button @click="question = ''">清空</el-button>
              <el-button type="primary" :loading="asking" @click="send()">发送</el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="9">
        <el-card>
          <template #header>
            <div class="card-header">
              <strong>AI预警识别目标</strong>
              <el-button link type="primary" @click="loadAll">刷新</el-button>
            </div>
          </template>
          <el-empty v-if="!forecast.length" description="暂无缺货或呆滞风险" />
          <el-timeline v-else>
            <el-timeline-item v-for="item in forecast.slice(0, 10)" :key="`${item.risk}-${item.partCode}`" :type="forecastType(item.risk)">
              <div class="warning-title">{{ item.risk }}：{{ item.partCode }} {{ item.partName }}</div>
              <div class="muted">{{ item.message }}</div>
              <div class="muted">库存 {{ item.stockQty }}，近30天出库 {{ item.outbound30Days }}，日均 {{ item.dailyUsage }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <el-card class="shortcut-card">
          <template #header><strong>能力说明</strong></template>
          <el-alert
            type="success"
            show-icon
            :closable="false"
            title="可执行：新建入库/出库/转包单，扫码入库/出库/转包，新增/编辑/删除部分基础资料，查询库存和追溯。"
          />
          <el-alert
            class="hint"
            type="info"
            show-icon
            :closable="false"
            :title="modelHint"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="configVisible" title="AI模型配置" width="620px">
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        title="API Key 将保存到当前登录用户自己的配置中，不再写入项目文件。请不要和他人共用自己的 Key。"
      />
      <el-form class="config-form" label-width="100px">
        <el-form-item label="API地址">
          <el-input v-model="configForm.apiUrl" placeholder="https://api.deepseek.com/chat/completions" />
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="configForm.model" placeholder="deepseek-v4-flash" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="configForm.apiKey"
            type="password"
            show-password
            placeholder="留空表示保留已保存的 Key"
          />
          <div class="form-tip">
            当前状态：{{ configForm.apiKeyConfigured ? `已配置（${configForm.apiKeyMask}）` : '未配置' }}
          </div>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="configForm.clearApiKey">清除已保存的 API Key</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingConfig" @click="saveConfig">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  chatAiAdmin,
  executeAiPlan,
  getAiAdminSummary,
  getAiForecast,
  getAiModelConfig,
  getAiModelStatus,
  saveAiModelConfig
} from '@/api/aiAdmin'

const router = useRouter()
const summary = ref({})
const forecast = ref([])
const modelStatus = ref({})
const question = ref('')
const asking = ref(false)
const executingPlanId = ref('')
const configVisible = ref(false)
const savingConfig = ref(false)
const configForm = ref({
  apiUrl: 'https://api.deepseek.com/chat/completions',
  model: 'deepseek-v4-flash',
  apiKey: '',
  apiKeyConfigured: false,
  apiKeyMask: '',
  clearApiKey: false
})
const messages = ref([
  {
    id: Date.now(),
    role: 'ai',
    text: '我是AI仓库管理员。你可以直接说“扫码入库某看板”“新建某供应商某零件数量100的出库单”“查询PT005未来会不会缺货”。涉及写数据的操作，我会先给你执行计划，确认后再执行。'
  }
])

const quickQuestions = computed(() => [
  '总库存情况',
  '未来可能缺货的零件',
  '有哪些呆滞风险',
  '查询PT005库存',
  '异常数据检查'
])
const modelHint = computed(() => {
  if (modelStatus.value.enabled) return `当前使用 ${modelStatus.value.model}，配置来源：${modelStatus.value.source || '当前用户'}`
  return '当前用户未配置 API Key，系统会使用本地规则解析；点击“AI配置”填写 DeepSeek API Key 后启用大模型。'
})

onMounted(loadAll)

async function loadAll() {
  summary.value = (await getAiAdminSummary()).data || {}
  forecast.value = (await getAiForecast()).data || []
  modelStatus.value = (await getAiModelStatus()).data || {}
}

async function openConfig() {
  const data = (await getAiModelConfig()).data || {}
  configForm.value = {
    apiUrl: data.apiUrl || 'https://api.deepseek.com/chat/completions',
    model: data.model || 'deepseek-v4-flash',
    apiKey: '',
    apiKeyConfigured: Boolean(data.apiKeyConfigured),
    apiKeyMask: data.apiKeyMask || '',
    clearApiKey: false
  }
  configVisible.value = true
}

async function saveConfig() {
  savingConfig.value = true
  try {
    await saveAiModelConfig({
      apiUrl: configForm.value.apiUrl,
      model: configForm.value.model,
      apiKey: configForm.value.apiKey || '__KEEP__',
      clearApiKey: configForm.value.clearApiKey
    })
    ElMessage.success('AI配置已保存')
    configVisible.value = false
    await loadAll()
  } finally {
    savingConfig.value = false
  }
}

async function send(text) {
  const content = text || question.value.trim()
  if (!content) return ElMessage.warning('请输入指令')
  question.value = ''
  messages.value.push({ id: Date.now() + Math.random(), role: 'user', text: content })
  const pendingId = Date.now() + Math.random()
  messages.value.push({
    id: pendingId,
    role: 'ai',
    text: '思考中：正在理解你的指令，并匹配供应商、零件、仓库、库位和器具...',
    loading: true
  })
  asking.value = true
  try {
    const data = (await chatAiAdmin(content)).data || {}
    replaceMessage(pendingId, {
      id: Date.now() + Math.random(),
      role: 'ai',
      text: data.answer || '已处理',
      plan: data.plan,
      items: data.items || []
    })
    if (data.summary) summary.value = data.summary
    if (data.forecast) forecast.value = data.forecast
  } catch (error) {
    replaceMessage(pendingId, {
      id: Date.now() + Math.random(),
      role: 'ai',
      text: '处理失败：' + (error?.message || '请稍后再试')
    })
    throw error
  } finally {
    asking.value = false
  }
}

async function execute(planId) {
  executingPlanId.value = planId
  const pendingId = Date.now() + Math.random()
  messages.value.push({
    id: pendingId,
    role: 'ai',
    text: '执行中：正在调用系统业务接口并写入单据/看板数据...',
    loading: true
  })
  try {
    const data = (await executeAiPlan(planId)).data || {}
    replaceMessage(pendingId, {
      id: Date.now() + Math.random(),
      role: 'ai',
      text: data.answer || '操作已执行完成',
      items: data.result ? [normalizeResult(data.result)] : []
    })
    notifyResultChanged(data.result)
    if (data.forecast) forecast.value = data.forecast
    await loadAll()
  } catch (error) {
    replaceMessage(pendingId, {
      id: Date.now() + Math.random(),
      role: 'ai',
      text: '执行失败：' + (error?.message || '请检查计划参数后重试')
    })
    throw error
  } finally {
    executingPlanId.value = ''
  }
}

function replaceMessage(id, message) {
  const index = messages.value.findIndex(item => item.id === id)
  if (index >= 0) messages.value.splice(index, 1, message)
  else messages.value.push(message)
}

function normalizeResult(result) {
  if (Array.isArray(result)) return { kind: '执行结果', message: `返回 ${result.length} 条记录` }
  const row = { kind: result.type || '执行结果', ...result }
  const orderNo = String(row.orderNo || '')
  if (orderNo.startsWith('RK')) {
    row.kind = row.type || '入库单'
    row.message = row.message || `已生成入库单，状态 ${row.status || '-'}，已自动生成 ${row.kanbanCount ?? 0} 张关联看板`
    row.actionText = '查看入库单'
    row.routeName = 'InboundOrder'
    row.routeQuery = { inboundOrderNo: orderNo }
  } else if (orderNo.startsWith('OUT')) {
    row.kind = row.type || '出库单'
    row.message = row.message || `已生成出库单，状态 ${row.status || '-'}，已自动生成 ${row.kanbanCount ?? 0} 张关联看板`
    row.actionText = '查看出库单'
    row.routeName = 'OutboundOrder'
    row.routeQuery = { orderNo }
  } else if (orderNo.startsWith('ZB')) {
    row.kind = row.type || '转包单'
    row.message = row.message || `已生成转包单，状态 ${row.status || '-'}，已自动生成 ${row.kanbanCount ?? 0} 张关联看板`
    row.actionText = '查看转包单'
    row.routeName = 'RepackManage'
    row.routeQuery = { orderNo }
  }
  return row
}

function notifyResultChanged(result) {
  const orderNo = result && !Array.isArray(result) ? String(result.orderNo || '') : ''
  if (!orderNo) return
  localStorage.setItem('wms:last-ai-order', JSON.stringify({ orderNo, time: Date.now() }))
}

function goResult(row) {
  if (!row.routeName) return
  router.push({ name: row.routeName, query: row.routeQuery || {} })
}

function planParams(params = {}) {
  if (Array.isArray(params._displayRows)) {
    return params._displayRows
      .filter(item => item.value !== null && item.value !== undefined && item.value !== '')
      .map(item => ({ key: item.label, value: item.value }))
  }
  return Object.entries(params)
    .filter(([key, value]) => !key.startsWith('_') && value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({ key: paramLabel(key), value: typeof value === 'object' ? JSON.stringify(value) : value }))
}

function planDetailRows(params = {}) {
  return Array.isArray(params._detailRows) ? params._detailRows : []
}

function paramLabel(key) {
  const labels = {
    supplierId: '供应商',
    customerId: '客户',
    partId: '零件',
    qty: '数量',
    warehouseId: '仓库',
    locationId: '库位',
    containerId: '器具',
    outboundType: '出库类型',
    kanbanNo: '看板号',
    orderNo: '单据号',
    orderId: '单据ID',
    operator: '操作人',
    actualQty: '实际数量',
    entity: '基础资料类型',
    id: '记录ID',
    code: '编号',
    name: '名称',
    capacity: '容量'
  }
  return labels[key] || key
}

function riskType(risk) {
  if (risk === '高') return 'danger'
  if (risk === '中') return 'warning'
  return 'info'
}

function forecastType(risk) {
  if (risk === '缺货' || risk === '未来缺货') return 'danger'
  if (risk === '呆滞风险') return 'warning'
  return 'info'
}
</script>

<style scoped>
.ai-admin-page { padding: 4px; }
.metric-card { margin-bottom: 16px; }
.metric-label { color: #606266; font-size: 14px; }
.metric-value { margin-top: 8px; font-size: 30px; font-weight: 700; color: #303133; }
.metric-value.small { font-size: 24px; }
.metric-sub { margin-top: 6px; color: #909399; font-size: 13px; }
.metric-card.warning .metric-value { color: #e6a23c; }
.metric-card.danger .metric-value { color: #f56c6c; }
.main-row { margin-top: 4px; }
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.header-actions { display: flex; align-items: center; gap: 10px; }
.muted { color: #909399; font-size: 13px; line-height: 1.6; }
.quick-questions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.chat-card :deep(.el-card__body) { padding-bottom: 12px; }
.messages { min-height: 360px; max-height: 560px; overflow-y: auto; padding: 4px 2px 12px; background: #f7f8fa; border-radius: 8px; }
.message { display: flex; margin: 12px; }
.message.user { justify-content: flex-end; }
.message.ai { justify-content: flex-start; }
.message.loading .bubble { color: #606266; border: 1px dashed #a0cfff; }
.bubble { max-width: 88%; padding: 12px; border-radius: 10px; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.message.user .bubble { background: #ecf5ff; }
.message-text { white-space: pre-wrap; line-height: 1.7; color: #303133; }
.plan-box { margin-top: 12px; padding: 12px; border: 1px solid #dcdfe6; border-radius: 8px; background: #fff; }
.plan-title { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.plan-summary { color: #606266; margin-bottom: 10px; }
.plan-params { margin-bottom: 10px; }
.plan-detail-table { margin-bottom: 10px; }
.plan-actions { display: flex; gap: 8px; justify-content: flex-end; }
.result-table { margin-top: 12px; }
.input-area { margin-top: 12px; }
.ask-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
.warning-title { font-weight: 600; color: #303133; }
.shortcut-card { margin-top: 16px; }
.hint { margin-top: 12px; }
.config-form { margin-top: 16px; }
.form-tip { margin-top: 6px; color: #909399; font-size: 12px; }
@media (max-width: 768px) {
  .card-header { align-items: flex-start; flex-direction: column; }
  .header-actions { align-items: flex-start; flex-direction: column; }
  .bubble { max-width: 96%; }
}
</style>

