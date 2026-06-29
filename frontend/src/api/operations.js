import request from '@/utils/request'

export function repackKanban(data) {
  return request.post('/kanban/repack', data)
}
export function getRepackSource(no) {
  return request.get(`/kanban/repack-source/${no}`)
}
export function scanRepackKanban(no, data = {}) {
  return request.post(`/kanban/repack-scan/${no}`, data)
}
export function getRepackRecords() {
  return request.get('/kanban/repack-records')
}
export function getPendingHandRepackOrders() {
  return request.get('/kanban/repack-pending-orders')
}
export function cancelHandRepackOrder(id) {
  return request.delete(`/kanban/repack-order/${id}`)
}
export function getRepackOrders(params = {}) {
  return request.get('/repack/orders', { params })
}
export function createRepackOrder(data) {
  return request.post('/repack/order', data)
}
export function executeRepackOrder(id) {
  return request.put(`/repack/order/${id}/execute`)
}
export function voidRepackOrder(id) {
  return request.put(`/repack/order/${id}/void`)
}
export function deleteRepackOrder(id) {
  return request.delete(`/repack/order/${id}`)
}
export function getRepackOrderKanbans(id) {
  return request.get(`/repack/order/${id}/kanbans`)
}
export function scanRepackKanbanNew(data) {
  return request.post('/repack/scan', data)
}
export function getRepackBalanceKanbans(params = {}) {
  return request.get('/repack/kanbans', { params })
}
export function getRepackBalances(params = {}) {
  return request.get('/repack/balances', { params })
}
export function getRepackStockAvailability(params = {}) {
  return request.get('/repack/stock-availability', { params })
}
export function universalRepackScan(data) {
  return request.post('/repack/universal-scan', data)
}
export function getPendingRepackKanbans() {
  return request.get('/repack/pending-kanbans')
}
export function getRecentRepackRecords() {
  return request.get('/repack/recent-records')
}
export function lookupKanban(no) {
  return request.get(`/repack/lookup-kanban/${no}`)
}
