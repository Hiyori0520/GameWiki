import axios, { type AxiosRequestConfig } from 'axios'
import { readonly, shallowRef } from 'vue'

export const isAdminDemo = import.meta.env.DEV && import.meta.env.VITE_ADMIN_DEMO === 'true'

export interface PageResult<T> {
  records: T[]
  page: number
  pageSize: number
  total: number
}

export interface AdminSession {
  isAdmin: true
  name: string
  permissions: string[]
}

const session = shallowRef<AdminSession | null>(null)
export const adminSession = readonly(session)
const sessionExpired = shallowRef(false)
export const adminSessionExpired = readonly(sessionExpired)

const client = axios.create({ baseURL: '/api/admin/v1', timeout: 30000 })
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('kb_token')
  if (token) config.headers['Authorization'] = token
  return config
})

class AdminApiError extends Error {
  readonly status: number

  constructor(status: number, message = '') {
    super(message)
    this.status = status
  }
}

export function adminErrorStatus(error: unknown) {
  if (error instanceof AdminApiError) return error.status
  if (axios.isAxiosError(error)) return error.response?.status
}

export function adminErrorMessage(error: unknown) {
  const status = adminErrorStatus(error)
  if (status === 401) return '管理员登录已过期，请登录后重试。'
  if (status === 403) return '当前账号没有执行此操作的权限。'
  if (status === 404) return '请求的记录或接口不存在。'
  if (status === 409) return '数据已更新，请刷新后重试。'
  if (status === 502) return '后台接口响应格式异常或服务不可用。'
  if (status && status >= 500) return '后台服务暂时无法处理请求，请稍后重试。'
  if (axios.isAxiosError<{ message?: unknown }>(error)) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') return '后台请求超时，请重试。'
    if (!error.response) return '无法连接后台服务，请检查网络后重试。'
    const message = error.response.data?.message
    if (typeof message === 'string' && message) return message
    return '后台请求失败，请重试。'
  }
  return error instanceof Error && error.message ? error.message : '后台请求失败，请重试。'
}

export async function adminRequest<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await client.request<unknown>(config)
    if (response.status === 204) return undefined as T
    if (!response.data || typeof response.data !== 'object' || !('code' in response.data)) {
      throw new AdminApiError(502)
    }
    const body = response.data as { code: unknown; success?: unknown; message?: unknown; result?: T; data?: T }
    if (typeof body.code !== 'number') throw new AdminApiError(502)
    if (body.code !== 200 || body.success === false) {
      throw new AdminApiError(body.code === 200 ? 502 : body.code, typeof body.message === 'string' ? body.message : '')
    }
    if ('result' in body) return body.result as T
    if ('data' in body) return body.data as T
    if (!config.method || config.method.toUpperCase() === 'GET') throw new AdminApiError(502)
    return undefined as T
  } catch (error) {
    if (adminErrorStatus(error) === 401 && config.url !== '/session') {
      session.value = null
      sessionExpired.value = true
    }
    throw error
  }
}

let pendingSession: Promise<void> | undefined

export function loadAdminSession() {
  if (isAdminDemo) return Promise.resolve()
  pendingSession ??= adminRequest<AdminSession>({ method: 'GET', url: '/session' })
    .then((account) => {
      if (account?.isAdmin !== true || typeof account.name !== 'string' || !Array.isArray(account.permissions) ||
        !account.permissions.every((permission) => typeof permission === 'string')) {
        throw new AdminApiError(403)
      }
      session.value = account
      sessionExpired.value = false
    })
    .catch((error: unknown) => {
      session.value = null
      throw error
    })
    .finally(() => { pendingSession = undefined })
  return pendingSession
}

export function hasAdminPermission(permission: string) {
  return isAdminDemo || session.value?.permissions.includes(permission) === true
}
