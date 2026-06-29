import request from '@/utils/request'

export function generateKanban(orderId) {
  return request.post(`/kanban/generate/${orderId}`)
}
export function getKanbans(params = {}) {
  return request.get('/kanban/list', { params })
}
export function getAllKanbans(params = {}) {
  return request.get('/kanban/all', { params })
}
export function getKanbanByNo(no) { return request.get(`/kanban/scan/${no}`) }
export function scanInbound(no, data) { return request.post(`/kanban/scan/${no}`, data) }
export function sealKanban(id, reason) { return request.put(`/kanban/${id}/seal`, { reason }) }
export function unsealKanban(id) { return request.put(`/kanban/${id}/unseal`) }
