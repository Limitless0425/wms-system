import request from '@/utils/request'

export function getSuppliers(params = {}) {
  return request.get('/baseinfo/suppliers', { params })
}
export function addSupplier(data) { return request.post('/baseinfo/supplier', data) }
export function batchAddSuppliers(data) { return request.post('/baseinfo/suppliers/batch', data) }
export function updateSupplier(data) { return request.put('/baseinfo/supplier', data) }
export function deleteSupplier(id) { return request.delete(`/baseinfo/supplier/${id}`) }

export function getParts(params = {}) {
  if (typeof params === 'number') params = { supplierId: params }
  return request.get('/baseinfo/parts', { params })
}
export function addPart(data) { return request.post('/baseinfo/part', data) }
export function batchAddParts(data) { return request.post('/baseinfo/parts/batch', data) }
export function updatePart(data) { return request.put('/baseinfo/part', data) }
export function deletePart(id) { return request.delete(`/baseinfo/part/${id}`) }

export function getWarehouses() { return request.get('/baseinfo/warehouses') }
export function addWarehouse(data) { return request.post('/baseinfo/warehouse', data) }
export function batchAddWarehouses(data) { return request.post('/baseinfo/warehouses/batch', data) }
export function updateWarehouse(data) { return request.put('/baseinfo/warehouse', data) }
export function deleteWarehouse(id) { return request.delete(`/baseinfo/warehouse/${id}`) }

export function getLocations(warehouseId) {
  return request.get('/baseinfo/locations', { params: warehouseId ? { warehouseId } : {} })
}
export function addLocation(data) { return request.post('/baseinfo/location', data) }
export function addLocationsBatch(data) { return request.post('/baseinfo/locations/batch', data) }
export function updateLocation(data) { return request.put('/baseinfo/location', data) }
export function deleteLocation(id) { return request.delete(`/baseinfo/location/${id}`) }

export function getCustomers(params = {}) {
  return request.get('/baseinfo/customers', { params })
}
export function addCustomer(data) { return request.post('/baseinfo/customer', data) }
export function batchAddCustomers(data) { return request.post('/baseinfo/customers/batch', data) }
export function updateCustomer(data) { return request.put('/baseinfo/customer', data) }
export function deleteCustomer(id) { return request.delete(`/baseinfo/customer/${id}`) }

export function getContainers(params = {}) {
  if (typeof params === 'string') params = { type: params }
  return request.get('/baseinfo/containers', { params })
}
export function addContainer(data) { return request.post('/baseinfo/container', data) }
export function batchAddContainers(data) { return request.post('/baseinfo/containers/batch', data) }
export function updateContainer(data) { return request.put('/baseinfo/container', data) }
export function deleteContainer(id) { return request.delete(`/baseinfo/container/${id}`) }
