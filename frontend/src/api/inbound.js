import request from '@/utils/request'

export function getInboundOrders(params = {}) {
  return request.get('/inbound/orders', { params })
}
export function getInboundOrder(id) { return request.get(`/inbound/order/${id}`) }
export function getInboundSupplierStorage(supplierId) { return request.get(`/inbound/supplier-storage/${supplierId}`) }
export function createInboundOrder(data) { return request.post('/inbound/order', data) }
export function updateInboundOrder(data) { return request.put('/inbound/order', data) }
export function deleteInboundOrder(id) { return request.delete(`/inbound/order/${id}`) }
export function voidInboundOrder(id) { return request.put(`/inbound/order/${id}/void`) }
export function manualInboundOrder(id) { return request.put(`/inbound/order/${id}/manual-inbound`) }
export function getOrderKanbans(id) { return request.get(`/inbound/order/${id}/kanbans`) }
