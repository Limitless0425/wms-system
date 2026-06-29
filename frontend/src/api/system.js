import request from '@/utils/request'

export function getUsers(params = {}) { return request.get('/system/users', { params }) }
export function addUser(data) { return request.post('/system/user', data) }
export function updateUser(data) { return request.put('/system/user', data) }
export function deleteUser(id) { return request.delete(`/system/user/${id}`) }

export function getRoles() { return request.get('/system/roles') }
export function addRole(data) { return request.post('/system/role', data) }
export function updateRole(data) { return request.put('/system/role', data) }
export function deleteRole(id) { return request.delete(`/system/role/${id}`) }
