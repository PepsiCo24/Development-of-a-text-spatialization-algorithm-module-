import axios from 'axios'

export const http = axios.create({ baseURL: '/api', timeout: 10_000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('geotext-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !String(error.config?.url ?? '').startsWith('/auth/')) {
      localStorage.removeItem('geotext-token')
      localStorage.removeItem('geotext-profile')
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}`)
        window.location.replace(`/login?reason=session-expired&redirect=${redirect}`)
      }
      return Promise.reject(new Error('登录状态已失效，请重新登录'))
    }
    return Promise.reject(new Error(error.response?.data?.message ?? error.message ?? '网络请求失败'))
  },
)

