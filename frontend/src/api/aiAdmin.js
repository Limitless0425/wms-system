import request from '@/utils/request'

export function getAiAdminSummary() {
  return request.get('/ai-admin/summary')
}

export function getAiAdminAnomalies() {
  return request.get('/ai-admin/anomalies')
}

export function askAiAdmin(question) {
  return request.post('/ai-admin/ask', { question })
}

export function chatAiAdmin(question) {
  return request.post('/ai-admin/chat', { question })
}

export function executeAiPlan(planId) {
  return request.post('/ai-admin/execute', { planId })
}

export function getAiForecast() {
  return request.get('/ai-admin/forecast')
}

export function getAiModelStatus() {
  return request.get('/ai-admin/model-status')
}

export function getAiModelConfig() {
  return request.get('/ai-admin/model-config')
}

export function saveAiModelConfig(data) {
  return request.post('/ai-admin/model-config', data)
}
