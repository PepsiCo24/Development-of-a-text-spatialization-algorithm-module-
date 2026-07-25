import axios from 'axios'

export const http = axios.create({ baseURL: '/api', timeout: 10_000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('geotext-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401 && !String(error.config?.url ?? '').startsWith('/auth/')) {
      localStorage.removeItem('geotext-token')
      localStorage.removeItem('geotext-profile')
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}`)
        window.location.replace(`/login?reason=session-expired&redirect=${redirect}`)
      }
      return Promise.reject(new Error('登录状态已失效，请重新登录'))
    }
    const data = error.response?.data
    if (typeof Blob !== 'undefined' && data instanceof Blob) {
      try {
        const payload = JSON.parse(await data.text()) as { message?: string }
        return Promise.reject(new Error(payload.message || '请求失败'))
      } catch {
        return Promise.reject(new Error(error.message || '网络请求失败'))
      }
    }
    return Promise.reject(new Error(data?.message ?? error.message ?? '网络请求失败'))
  },
)

