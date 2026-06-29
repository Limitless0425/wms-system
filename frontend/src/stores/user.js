import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getUserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref('')
  const role = ref('')

  async function login(usernameVal, password) {
    const res = await loginApi(usernameVal, password)
    if (res.code === 200) {
      token.value = res.data.token
      username.value = res.data.username
      role.value = res.data.role
      localStorage.setItem('token', res.data.token)
      return true
    }
    return false
  }

  async function fetchUserInfo() {
    const res = await getUserInfo()
    if (res.code === 200) {
      username.value = res.data.username
      role.value = res.data.role
    }
  }

  function logout() {
    token.value = ''
    username.value = ''
    role.value = ''
    localStorage.removeItem('token')
  }

  return { token, username, role, login, fetchUserInfo, logout }
})
