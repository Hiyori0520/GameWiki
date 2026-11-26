import { adminRequest } from './client'

export const dashboardMetrics = ['games', 'guides', 'users', 'content'] as const
export type DashboardMetric = (typeof dashboardMetrics)[number]
export type DashboardPeriod = 7 | 30 | 90
export type DashboardContent = 'games' | 'guides' | 'users'

export interface DashboardSummary {
  totals: Record<DashboardMetric, number>
  change7d?: Partial<Record<DashboardMetric, number>>
  pendingGuides?: number
}

export interface DashboardTrend {
  date: string
  games: number
  guides: number
}

export interface DashboardCategory {
  name: string
  value: number
}

export interface DashboardRecentItem {
  id: string | number
  name: string
  secondary?: string
  image?: string
  date: string
}

export interface DashboardLog {
  id: string | number
  actor: string
  module: string
  action: string
  target?: string
  time: string
  result: string
}

export interface DashboardHealth {
  api?: 'up' | 'down'
  database?: 'up' | 'down'
  storage?: number
  cpu?: number
  memory?: number
}

async function requestRows<T>(
  url: string, signal: AbortSignal, valid: (row: T) => boolean, params?: Record<string, string | number>,
) {
  const rows = await adminRequest<T[]>({ url, signal, params })
  if (!Array.isArray(rows) || rows.some((row) => !row || typeof row !== 'object' || !valid(row))) {
    throw new Error('后台接口返回的数据列表格式有误。')
  }
  return rows
}

export const dashboardApi = {
  async summary(signal: AbortSignal) {
    const summary = await adminRequest<DashboardSummary>({ url: '/dashboard/summary', signal })
    if (!summary?.totals || dashboardMetrics.some((key) => !Number.isFinite(summary.totals[key]))) {
      throw new Error('后台接口返回的统计格式有误。')
    }
    return summary
  },
  trends: (days: DashboardPeriod, signal: AbortSignal) =>
    requestRows<DashboardTrend>('/dashboard/trends', signal,
      (row) => typeof row.date === 'string' && Number.isFinite(row.games) && Number.isFinite(row.guides), { days }),
  distribution: (signal: AbortSignal) => requestRows<DashboardCategory>('/dashboard/game-distribution', signal,
    (row) => typeof row.name === 'string' && Number.isFinite(row.value)),
  recent: (type: DashboardContent, signal: AbortSignal) =>
    requestRows<DashboardRecentItem>('/dashboard/recent', signal,
      (row) => (typeof row.id === 'string' || typeof row.id === 'number') && typeof row.name === 'string' &&
        typeof row.date === 'string', { type, limit: 5 }),
  logs: (signal: AbortSignal) => requestRows<DashboardLog>('/dashboard/logs', signal,
    (row) => typeof row.time === 'string' && typeof row.action === 'string' && typeof row.actor === 'string', { limit: 30 }),
  health: (signal: AbortSignal) => adminRequest<DashboardHealth>({ url: '/dashboard/health', signal }),
}
