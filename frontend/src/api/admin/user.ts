import { adminRequest, hasAdminPermission, isAdminDemo } from './client'
import type { PageResult } from './client'
import type { UserProfile } from '../../admin/users'
import { userMemberships } from '../../types/admin/user'
import type {
  UserBanRequest, UserDetail, UserDistribution, UserGrowthPoint, UserId, UserLevelRule,
  UserQuery, UserRankingEntry, UserRecord, UserStats, UserStatus, UserTrendDays,
} from '../../types/admin/user'

const wireStatuses = { active: 'NORMAL', disabled: 'DISABLED', banned: 'BANNED' } as const
type WireStatus = typeof wireStatuses[keyof typeof wireStatuses]
const localStatuses: Record<WireStatus, UserStatus> = { NORMAL: 'active', DISABLED: 'disabled', BANNED: 'banned' }
type WireUser<T extends UserRecord> = Omit<T, 'status'> & { status: WireStatus }

function requireValidResponse(condition: unknown): asserts condition {
  if (!condition) throw new Error('用户管理接口响应格式异常。')
}

function validCount(value: number) {
  return Number.isSafeInteger(value) && value >= 0
}

function validId(value: UserId) {
  return typeof value === 'string' && value.length > 0 || Number.isSafeInteger(value)
}

function checkedArray<T>(rows: T[], validate: (row: T) => boolean) {
  requireValidResponse(Array.isArray(rows) && rows.every(row => row != null && validate(row)))
  return rows
}

function localUser<T extends UserRecord>(user: WireUser<T>): T {
  requireValidResponse(user && validId(user.id) && Object.hasOwn(localStatuses, user.status)
    && typeof user.name === 'string' && typeof user.username === 'string' && validCount(user.level)
    && Object.hasOwn(userMemberships, user.membership) && typeof user.avatar === 'string'
    && typeof user.registeredAt === 'string' && (user.lastLoginAt === null || typeof user.lastLoginAt === 'string'))
  return { ...user, status: localStatuses[user.status] } as T
}

function userPath(id: UserId) {
  return `/users/${encodeURIComponent(id)}`
}

function requirePermission(permission: string) {
  if (!hasAdminPermission(permission)) throw new Error('当前账号没有执行此操作的权限。')
}

async function demo() {
  if (!isAdminDemo) throw new Error('此操作仅在开发演示模式可用。')
  return (await import('../../admin/users')).userDemo
}

export const userAdminApi = {
  async list(query: UserQuery, signal?: AbortSignal): Promise<PageResult<UserRecord>> {
    if (isAdminDemo) return (await demo()).list(query)
    const { status, ...filters } = query
    const result = await adminRequest<PageResult<WireUser<UserRecord>>>({
      url: '/users', signal, params: { ...filters, status: status ? wireStatuses[status] : undefined },
    })
    requireValidResponse(result && Array.isArray(result.records) && validCount(result.total)
      && validCount(result.page) && result.page > 0 && validCount(result.pageSize) && result.pageSize > 0)
    return { ...result, records: result.records.map(record => localUser<UserRecord>(record)) }
  },
  async detail(id: UserId, signal?: AbortSignal): Promise<UserDetail> {
    if (isAdminDemo) return (await demo()).detail(id)
    const result = localUser<UserDetail>(await adminRequest<WireUser<UserDetail>>({ url: userPath(id), signal }))
    if (result.banHistory != null) {
      checkedArray(result.banHistory, row => (row.action === 'BAN' || row.action === 'UNBAN')
        && typeof row.operator === 'string' && typeof row.operatedAt === 'string'
        && (row.reason == null || typeof row.reason === 'string') && (row.expiresAt == null || typeof row.expiresAt === 'string'))
    }
    requireValidResponse((result.maskedEmail == null || typeof result.maskedEmail === 'string')
      && (result.experience == null || validCount(result.experience))
      && (result.guideContributions == null || validCount(result.guideContributions))
      && (result.communityContributions == null || validCount(result.communityContributions)))
    return result
  },
  async stats(signal?: AbortSignal): Promise<UserStats> {
    if (isAdminDemo) return (await demo()).stats()
    const result = await adminRequest<UserStats>({ url: '/users/stats', signal })
    requireValidResponse(result && [result.total, result.newLast7Days, result.activeLast7Days, result.vip, result.banned].every(validCount)
      && result.vip <= result.total && (result.pending == null || validCount(result.pending)))
    return { ...result, pending: result.pending ?? null }
  },
  async growth(days: UserTrendDays, signal?: AbortSignal): Promise<UserGrowthPoint[]> {
    if (isAdminDemo) return (await demo()).growth(days)
    const result = await adminRequest<UserGrowthPoint[]>({ url: '/users/growth', params: { days }, signal })
    return checkedArray(result, row => typeof row.date === 'string' && validCount(row.newUsers))
  },
  async distribution(signal?: AbortSignal): Promise<UserDistribution[]> {
    if (isAdminDemo) return (await demo()).distribution()
    const result = await adminRequest<UserDistribution[]>({ url: '/users/distribution', signal })
    checkedArray(result, row => Object.hasOwn(userMemberships, row.membership) && validCount(row.count))
    requireValidResponse(new Set(result.map(row => row.membership)).size === result.length)
    return result
  },
  async ranking(signal?: AbortSignal): Promise<UserRankingEntry[]> {
    if (isAdminDemo) return (await demo()).ranking()
    const result = await adminRequest<UserRankingEntry[]>({ url: '/users/active-ranking', signal })
    return checkedArray(result, row => validId(row.id) && typeof row.name === 'string' && typeof row.avatar === 'string'
      && validCount(row.rank) && row.rank > 0 && validCount(row.contribution))
  },
  async levels(signal?: AbortSignal): Promise<UserLevelRule[]> {
    if (isAdminDemo) return (await demo()).levels()
    const result = await adminRequest<UserLevelRule[]>({ url: '/user-levels', signal })
    return checkedArray(result, row => validCount(row.level) && typeof row.name === 'string'
      && validCount(row.requiredExperience) && typeof row.rule === 'string')
  },
  async updateProfile(id: UserId, name: string): Promise<void> {
    requirePermission('users:write')
    await adminRequest({ url: `${userPath(id)}/profile`, method: 'PATCH', data: { name } })
  },
  async ban(id: UserId, body: UserBanRequest): Promise<void> {
    requirePermission('users:ban')
    if (isAdminDemo) {
      (await demo()).setStatus([id], 'banned', body)
      return
    }
    await adminRequest({ url: `${userPath(id)}/ban`, method: 'POST', data: body })
  },
  async unban(id: UserId): Promise<void> {
    requirePermission('users:ban')
    if (isAdminDemo) {
      (await demo()).setStatus([id], 'active')
      return
    }
    await adminRequest({ url: `${userPath(id)}/unban`, method: 'POST' })
  },
  /** 管理员重置密码：不传则后端生成随机密码，返回的明文仅此一次 */
  async resetPassword(id: UserId, newPassword?: string): Promise<string> {
    requirePermission('users:write')
    if (isAdminDemo) throw new Error('演示模式下不连接数据库，无法重置密码。')
    const result = await adminRequest<{ password: string }>({
      url: `${userPath(id)}/password`, method: 'POST', data: { newPassword: newPassword || undefined },
    })
    requireValidResponse(result && typeof result.password === 'string' && result.password.length > 0)
    return result.password
  },
  async demoProfile(id: UserId) { return (await demo()).profile(id) },
  async saveDemoProfile(profile: UserProfile, id?: UserId) { (await demo()).saveProfile(profile, id) },
  async setDemoStatus(ids: UserId[], status: UserStatus, ban?: UserBanRequest) { (await demo()).setStatus(ids, status, ban) },
  async removeDemoProfiles(ids: UserId[]) { (await demo()).remove(ids) },
  async importDemoProfiles(rows: unknown[]) { (await demo()).importProfiles(rows) },
  async exportDemoProfiles(query: UserQuery, ids: UserId[]) { return (await demo()).exportProfiles(query, ids) },
}
