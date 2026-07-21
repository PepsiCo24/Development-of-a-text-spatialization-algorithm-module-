import { defineStore } from 'pinia'
import { http } from '@/api/http'

const TOKEN_KEY = 'geotext-token'

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: localStorage.getItem(TOKEN_KEY) ?? '' }),
  getters: { authenticated: (state) => Boolean(state.token) },
  actions: {
    async login(username: string, password: string) {
      if (!username.trim() || !password) throw new Error('请输入用户名和密码')
      const response = await http.post('/auth/login', { username, password })
      this.token = response.data.data.accessToken
      localStorage.setItem(TOKEN_KEY, this.token)
    },
    async register(username: string, displayName: string, password: string) {
      const response = await http.post('/auth/register', { username, displayName, password })
      this.token = response.data.data.accessToken
      localStorage.setItem(TOKEN_KEY, this.token)
    },
    logout() {
      this.token = ''
      localStorage.removeItem(TOKEN_KEY)
    },
  },
})
