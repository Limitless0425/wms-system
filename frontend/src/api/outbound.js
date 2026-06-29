import request from '@/utils/request'

export function getOutboundOrders(params = {}) {
  return request.get('/outbound/orders', { params })
}
export function createOutboundOrder(data) { return request.post('/outbound/order', data) }
export function voidOutboundOrder(id) { return request.put(`/outbound/order/${id}/void`) }
export function deleteOutboundOrder(id) { return request.delete(`/outbound/order/${id}`) }
export function getOutboundOrderKanbans(id) { return request.get(`/outbound/order/${id}/kanbans`) }
export function getOutboundKanbanByNo(no) { return request.get(`/outbound/kanban/scan/${no}`) }
export function getOutboundStockAvailability(params = {}) { return request.get('/outbound/stock-availability', { params }) }
export function scanOutbound(data) { return request.post('/outbound/scan', data) }
export function directOutbound(data) { return request.post('/outbound/direct', data) }
export function returnOutbound(data) { return request.post('/outbound/return', data) }
export function getRecentOutbound() { return request.get('/outbound/recent') }
export function getOutboundHistory(params = {}) { return request.get('/outbound/history', { params }) }
export function getPendingOutbound() { return request.get('/outbound/pending') }
