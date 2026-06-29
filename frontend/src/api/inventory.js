import request from '@/utils/request'

export function traceByPart(partCode) {
  return request.get(`/inventory/trace/${partCode}`)
}
export function traceByKanban(no) {
  return request.get(`/inventory/kanban-trace/${no}`)
}
export function getWarehouseStockSummary() {
  return request.get('/inventory/warehouse-summary')
}
export function getStockSummary(params = {}) {
  return request.get('/inventory/stock-summary', { params })
}
export function getTotalStockReport() {
  return request.get('/inventory/total-stock-report')
}
