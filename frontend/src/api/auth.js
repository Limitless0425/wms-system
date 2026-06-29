import request from '@/utils/request'

export function login(username, password) {
  return request.post('/login', { username, password })
}

export function getUserInfo() {
  return request.get('/userinfo')
}

export function getMenus() {
  return request.get('/menus')
}
