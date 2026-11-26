export const userStatuses = { active: '正常', disabled: '禁用', banned: '封禁' } as const
export const userRoles = { member: '普通用户', moderator: '内容审核员', admin: '管理员' } as const
export const userMemberships = { standard: '普通用户', monthly: 'VIP 月度', quarterly: 'VIP 季度', yearly: 'VIP 年度' } as const

export type UserId = number | string
export type UserStatus = keyof typeof userStatuses
export type UserMembership = keyof typeof userMemberships
export type UserTab = 'all' | 'standard' | 'vip' | 'pending' | 'banned'
export type UserSort = 'newest' | 'oldest' | 'login' | 'level'
export type UserTrendDays = 7 | 30 | 90

export interface UserRecord {
  id: UserId
  name: string
  username: string
  level: number
  membership: UserMembership
  avatar: string
  status: UserStatus
  registeredAt: string
  lastLoginAt: string | null
}

export interface UserBanRequest {
  reason: string
  durationDays: number | null
}

export interface UserBanRecord {
  action: 'BAN' | 'UNBAN'
  operator: string
  reason?: string
  operatedAt: string
  expiresAt?: string | null
}

export interface UserLevelRule {
  level: number
  name: string
  requiredExperience: number
  rule: string
}

export interface UserDetail extends UserRecord {
  maskedEmail?: string
  experience?: number
  guideContributions?: number
  communityContributions?: number
  banHistory?: UserBanRecord[]
}

export interface UserQuery {
  page: number
  pageSize: number
  keyword?: string
  tab?: Exclude<UserTab, 'all'>
  status?: UserStatus
  demoRole?: keyof typeof userRoles
  minLevel?: number
  maxLevel?: number
  registeredFrom?: string
  registeredTo?: string
  lastLoginFrom?: string
  lastLoginTo?: string
  sort: UserSort
}

export interface UserStats {
  total: number
  newLast7Days: number
  activeLast7Days: number
  vip: number
  pending: number | null
  banned: number
}

export interface UserGrowthPoint {
  date: string
  newUsers: number
}

export interface UserDistribution {
  membership: UserMembership
  count: number
}

export interface UserRankingEntry {
  id: UserId
  name: string
  avatar: string
  rank: number
  contribution: number
}

export function formatUserTime(value: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (!Number.isFinite(date.valueOf())) return '—'
  return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}

export function maskUserEmail(value?: string) {
  const at = value?.lastIndexOf('@') ?? -1
  if (!value || at < 1) return undefined
  return `${value[0]}***${value.slice(at)}`
}
