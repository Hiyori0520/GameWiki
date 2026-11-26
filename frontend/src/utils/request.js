import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/',
  timeout: 30000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('kb_token')
  if (token) {
    config.headers['Authorization'] = token
  }
  return config
})

const toLogin = () => {
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  if (!location.pathname.startsWith('/login')) {
    location.href = '/login'
  }
}

request.interceptors.response.use(
  response => {
    const res = response.data

    if (res.code === 401) {
      ElMessage.error(res.message || '登录已过期，请重新登录')
      toLogin()
      return Promise.reject(new Error(res.message || 'Unauthorized'))
    }

    if (res.code === 403) {
      ElMessage.error(res.message || '无访问权限')
      return Promise.reject(new Error(res.message || 'Forbidden'))
    }

    if (res.code !== 200) {
      ElMessage.error(res.message || '操作失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }

    return res
  },
  error => {
    if (axios.isCancel(error)) return Promise.reject(error)
    if (error.response && error.response.status === 401) {
      toLogin()
      return Promise.reject(error)
    }
    ElMessage.error('后端服务连接异常，请确认 8081 端口已启动')
    return Promise.reject(error)
  }
)

export default request