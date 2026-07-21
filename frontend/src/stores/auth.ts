import { defineStore } from 'pinia'
import { http } from '@/api/http'

const TOKEN_KEY = 'geotext-token'
const PROFILE_KEY = 'geotext-profile'

type SessionProfile = { displayName: string; role: string }

const tokenRole = (token: string) => {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, '='))).role ?? 'USER'
  } catch { return 'USER' }
}

const loadProfile = (token: string): SessionProfile => {
  try {
    const profile = JSON.parse(localStorage.getItem(PROFILE_KEY) ?? '') as SessionProfile
    if (profile.role) return profile
  } catch { /* Use the role embedded in an existing login token. */ }
  return { displayName: '', role: token ? tokenRole(token) : 'USER' }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const token = localStorage.getItem(TOKEN_KEY) ?? ''
    const profile = loadProfile(token)
    return { token, displayName: profile.displayName, role: profile.role }
  },
  getters: {
    authenticated: (state) => Boolean(state.token),
    isAdmin: (state) => state.role === 'ADMIN',
    roleLabel: (state) => state.role === 'ADMIN' ? '管理员' : '普通用户',
  },
  actions: {
    applySession(data: { accessToken: string; displayName: string; role: string }) {
      this.token = data.accessToken
      this.displayName = data.displayName
      this.role = data.role
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(PROFILE_KEY, JSON.stringify({ displayName: this.displayName, role: this.role }))
    },
    async login(username: string, password: string) {
      if (!username.trim() || !password) throw new Error('请输入用户名和密码')
      const response = await http.post('/auth/login', { username, password })
      this.applySession(response.data.data)
    },
    async register(username: string, displayName: string, password: string) {
      const response = await http.post('/auth/register', { username, displayName, password })
      this.applySession(response.data.data)
    },
    logout() {
      this.token = ''
      this.displayName = ''
      this.role = 'USER'
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(PROFILE_KEY)
    },
  },
})
