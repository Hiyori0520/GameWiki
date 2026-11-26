import { avatars } from './art'
import { createDemoCollection } from './storage'
import { maskUserEmail, userMemberships, userRoles } from '../types/admin/user'
import type {
  UserBanRecord, UserBanRequest, UserDetail, UserId, UserLevelRule, UserQuery, UserRecord as UserListRecord, UserStatus, UserTrendDays,
} from '../types/admin/user'

export interface UserProfile {
  name: string
  username: string
  email: string
  level: number
  role: keyof typeof userRoles
  membership: keyof typeof userMemberships
}

export interface UserRecord extends UserProfile {
  id: number
  avatar: string
  status: UserStatus | 'pending'
  registeredAt: string
  lastLoginAt: string | null
  contribution: number
  banHistory?: UserBanRecord[]
}

const seedRows: [
  name: string,
  username: string,
  level: number,
  membership: UserRecord['membership'],
  status: UserRecord['status'],
  daysSinceRegistration: number,
  daysSinceLogin: number,
  contribution: number,
  role?: UserRecord['role'],
][] = import.meta.env.DEV ? [
  ['星海の旅人', 'star_traveler', 50, 'yearly', 'active', 1, 0, 12480, 'admin'],
  ['白昼梦', 'daydream', 46, 'quarterly', 'active', 2, 0, 10931],
  ['樱花与猫', 'sakura_cat', 38, 'standard', 'active', 3, 0, 9765],
  ['月下的夜空', 'moonlight', 35, 'standard', 'disabled', 4, 1, 845],
  ['雪见凛', 'yukimi', 32, 'monthly', 'active', 5, 1, 7124, 'moderator'],
  ['风之回声', 'echo_wind', 28, 'standard', 'banned', 6, 2, 1480],
  ['晴空日记', 'blue_diary', 14, 'standard', 'active', 8, 2, 501],
  ['鹿鸣', 'deer_song', 7, 'standard', 'pending', 9, 9, 35],
  ['浅水星光', 'still_water', 26, 'standard', 'active', 10, 1, 4155],
  ['春日来信', 'spring_letter', 22, 'standard', 'active', 12, 5, 740],
  ['砂糖玻璃', 'sugar_glass', 17, 'standard', 'active', 13, 1, 1210],
  ['梧桐夜雨', 'rain_at_night', 5, 'standard', 'pending', 15, 15, 10],
  ['北极星', 'pole_star', 41, 'quarterly', 'active', 17, 6, 3660],
  ['纸飞机', 'paper_plane', 16, 'standard', 'disabled', 19, 12, 550],
  ['远山绘卷', 'distant_hills', 23, 'standard', 'active', 21, 8, 1090],
  ['萤火街道', 'firefly_lane', 12, 'standard', 'active', 23, 1, 450],
  ['青瓷苏打', 'blue_soda', 30, 'standard', 'active', 26, 11, 1880],
  ['秋野', 'autumn_field', 9, 'standard', 'active', 28, 13, 550],
  ['窗边的云', 'window_cloud', 19, 'standard', 'active', 32, 3, 2300],
  ['橘灯', 'orange_lamp', 11, 'standard', 'banned', 39, 22, 60],
  ['云上漫游', 'cloud_roamer', 34, 'yearly', 'active', 45, 6, 2220],
  ['涟漪', 'ripple', 56, 'standard', 'active', 53, 3, 2720, 'admin'],
  ['拾穗者', 'grain_gatherer', 15, 'standard', 'active', 57, 15, 964],
  ['白桦', 'birchwood', 13, 'standard', 'active', 72, 16, 648],
  ['银色时雨', 'silver_rain', 21, 'monthly', 'active', 82, 9, 1902],
  ['鸢尾札记', 'iris_notes', 31, 'standard', 'active', 114, 16, 2711],
] : []

function demoTime(daysAgo: number, index: number) {
  const day = new Date()
  day.setDate(day.getDate() - daysAgo)
  day.setHours(10, Math.max(0, 32 - index), 0, 0)
  return day.toISOString()
}

const seed: UserRecord[] = seedRows.map(([
  name, username, level, membership, status, registration, login, contribution, role = 'member',
], index) => ({
  id: 100001 + index,
  name,
  username,
  email: `${username}@example.test`,
  level,
  role,
  membership,
  avatar: avatars[index % avatars.length] ?? '',
  status: status === 'pending' ? 'active' : status,
  registeredAt: demoTime(registration, index),
  lastLoginAt: status === 'pending' ? null : demoTime(login, index),
  contribution,
}))

export const users = createDemoCollection('users', seed)

const demoLevelRules: UserLevelRule[] = import.meta.env.DEV ? [
  { level: 1, name: '新手玩家', requiredExperience: 0, rule: '注册后启用' },
  { level: 20, name: '进阶玩家', requiredExperience: 2000, rule: '公开贡献获得经验' },
  { level: 40, name: '资深玩家', requiredExperience: 6000, rule: '公开贡献获得经验' },
  { level: 60, name: '攻略达人', requiredExperience: 12000, rule: '公开贡献获得经验' },
] : []

function dayOffset(days: number) {
  const result = new Date()
  result.setDate(result.getDate() + days)
  return result
}

function calendarDay(date: Date) {
  if (!Number.isFinite(date.valueOf())) return ''
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function asListRecord(user: UserRecord): UserListRecord {
  const { id, name, username, level, membership, avatar, status, registeredAt, lastLoginAt } = user
  const lastBan = user.banHistory?.at(-1)
  const expired = lastBan?.action === 'BAN' && lastBan.expiresAt && Date.parse(lastBan.expiresAt) <= Date.now()
  return { id, name, username, level, membership, avatar, status: status === 'pending' || status === 'banned' && expired ? 'active' : status, registeredAt, lastLoginAt }
}

function userById(id: UserId) {
  const user = users.records.value.find(item => String(item.id) === String(id))
  if (!user) throw new Error('未找到该用户。')
  return user
}

function activeUsers() {
  const start = calendarDay(dayOffset(-6))
  const end = calendarDay(dayOffset(0))
  return users.records.value.filter(user => {
    if (asListRecord(user).status !== 'active' || !user.lastLoginAt) return false
    const day = calendarDay(new Date(user.lastLoginAt))
    return day >= start && day <= end
  })
}

function queryUsers(query: UserQuery) {
  const search = query.keyword?.trim().toLocaleLowerCase()
  return users.records.value.filter(row => {
    const user = asListRecord(row)
    const registration = calendarDay(new Date(user.registeredAt))
    const login = user.lastLoginAt ? calendarDay(new Date(user.lastLoginAt)) : ''
    const matchesTab = !query.tab || query.tab === 'standard' && user.membership === 'standard'
      || query.tab === 'vip' && user.membership !== 'standard' || user.status === query.tab
    return matchesTab
      && (!search || [user.name, user.username, row.email, String(user.id)].some(value => value.toLocaleLowerCase().includes(search)))
      && (!query.status || query.status === user.status)
      && (!query.demoRole || query.demoRole === row.role)
      && (query.minLevel === undefined || user.level >= query.minLevel)
      && (query.maxLevel === undefined || user.level <= query.maxLevel)
      && (!query.registeredFrom || registration >= query.registeredFrom)
      && (!query.registeredTo || registration <= query.registeredTo)
      && (!query.lastLoginFrom || login && login >= query.lastLoginFrom)
      && (!query.lastLoginTo || login && login <= query.lastLoginTo)
  }).sort((left, right) => {
    switch (query.sort) {
      case 'oldest': return Date.parse(left.registeredAt) - Date.parse(right.registeredAt)
      case 'login': return Date.parse(right.lastLoginAt ?? '1970-01-01') - Date.parse(left.lastLoginAt ?? '1970-01-01')
      case 'level': return right.level - left.level
      default: return Date.parse(right.registeredAt) - Date.parse(left.registeredAt)
    }
  })
}

function requireLastAdmin(next: UserRecord[]) {
  const activeAdmin = (user: UserRecord) => user.role === 'admin' && asListRecord(user).status === 'active'
  if (users.records.value.some(activeAdmin) && !next.some(activeAdmin)) {
    throw new Error('请至少保留一名状态正常的演示管理员。')
  }
  users.replace(next)
}

function validateProfile(input: unknown, existing: UserRecord[], id?: number): UserProfile {
  if (!input || typeof input !== 'object') throw new Error('用户信息格式不正确。')
  const value = input as Partial<UserProfile>
  const name = typeof value.name === 'string' ? value.name.trim() : ''
  const username = typeof value.username === 'string' ? value.username.trim() : ''
  const email = typeof value.email === 'string' ? value.email.trim().toLowerCase() : ''
  const level = value.level ?? 1
  const role = value.role ?? 'member'
  const membership = value.membership ?? 'standard'
  if (!name || name.length > 30) throw new Error('请填写 1 至 30 个字符的昵称。')
  if (!/^[\p{L}\p{N}_.-]{2,24}$/u.test(username)) throw new Error('用户名须为 2 至 24 个字母、数字、下划线、点或连字符。')
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 120) throw new Error('请填写有效的邮箱地址。')
  if (!Number.isInteger(level) || level < 1 || level > 100) throw new Error('等级须为 1 至 100 的整数。')
  if (!Object.hasOwn(userRoles, role) || !Object.hasOwn(userMemberships, membership)) throw new Error('角色或会员身份无效。')
  if (existing.some(user => user.id !== id && (
    user.username.toLocaleLowerCase() === username.toLocaleLowerCase() || user.email.toLowerCase() === email
  ))) throw new Error('用户名或邮箱已存在。')
  return { name, username, email, level, role, membership }
}

function buildUser(profile: UserProfile, id: number): UserRecord {
  return { ...profile, id, avatar: avatars[(id - 100001) % avatars.length] ?? '', status: 'active', registeredAt: new Date().toISOString(), lastLoginAt: null, contribution: 0 }
}

export const userDemo = {
  list(query: UserQuery) {
    const matching = queryUsers(query)
    const offset = (query.page - 1) * query.pageSize
    return { records: matching.slice(offset, offset + query.pageSize).map(asListRecord), page: query.page, pageSize: query.pageSize, total: matching.length }
  },
  detail(id: UserId): UserDetail {
    const user = userById(id)
    return { ...asListRecord(user), maskedEmail: maskUserEmail(user.email), experience: user.contribution, banHistory: user.banHistory ?? [] }
  },
  stats() {
    const rows = users.records.value
    return {
      total: rows.length, newLast7Days: rows.filter(user => calendarDay(new Date(user.registeredAt)) >= calendarDay(dayOffset(-6))).length,
      activeLast7Days: activeUsers().length, vip: rows.filter(user => user.membership !== 'standard').length,
      pending: null, banned: rows.filter(user => asListRecord(user).status === 'banned').length,
    }
  },
  growth(days: UserTrendDays) {
    const registered = users.records.value.map(user => calendarDay(new Date(user.registeredAt)))
    return Array.from({ length: days }, (_, index) => {
      const date = calendarDay(dayOffset(index + 1 - days))
      return { date, newUsers: registered.filter(day => day === date).length }
    })
  },
  distribution() {
    return (Object.keys(userMemberships) as UserRecord['membership'][]).map(membership => ({
      membership, count: users.records.value.filter(user => user.membership === membership).length,
    }))
  },
  ranking() {
    return activeUsers().sort((left, right) => right.contribution - left.contribution).map((user, index) => ({
      id: user.id, name: user.name, avatar: user.avatar, contribution: user.contribution, rank: index + 1,
    }))
  },
  levels: () => demoLevelRules,
  profile(id: UserId): UserProfile {
    const { name, username, email, level, role, membership } = userById(id)
    return { name, username, email, level, role, membership }
  },
  saveProfile(input: UserProfile, id?: UserId) {
    const existing = users.records.value
    const target = id === undefined ? undefined : userById(id)
    const profile = validateProfile(input, existing, target?.id)
    const nextId = Math.max(100000, ...existing.map(user => user.id)) + 1
    requireLastAdmin(target
      ? existing.map(user => user.id === target.id ? { ...user, ...profile } : user)
      : [buildUser(profile, nextId), ...existing])
  },
  setStatus(ids: UserId[], status: UserStatus, ban?: UserBanRequest) {
    const targets = new Set(ids.map(id => userById(id).id))
    const operatedAt = new Date().toISOString()
    const expiresAt = ban?.durationDays ? dayOffset(ban.durationDays).toISOString() : null
    requireLastAdmin(users.records.value.map(user => {
      if (!targets.has(user.id)) return user
      const action = status === 'banned' ? 'BAN' : asListRecord(user).status === 'banned' ? 'UNBAN' : undefined
      const banHistory = action ? [...(user.banHistory ?? []), {
        action, operator: '演示管理员', reason: ban?.reason, operatedAt, expiresAt,
      } satisfies UserBanRecord] : user.banHistory
      return { ...user, status, banHistory }
    }))
  },
  remove(ids: UserId[]) {
    const targets = new Set(ids.map(id => userById(id).id))
    requireLastAdmin(users.records.value.filter(user => !targets.has(user.id)))
  },
  exportProfiles(query: UserQuery, ids: UserId[]) {
    const rows = ids.length ? ids.map(userById) : queryUsers(query)
    return rows.map(({ name, username, email, level, role, membership }) => ({ name, username, email, level, role, membership }))
  },
  importProfiles(rows: unknown[]) {
    const next = [...users.records.value]
    let id = Math.max(100000, ...next.map(user => user.id)) + 1
    rows.forEach((row, index) => {
      try {
        next.push(buildUser(validateProfile(row, next), id++))
      } catch (error) {
        throw new Error(`第 ${index + 1} 条记录：${error instanceof Error ? error.message : '格式错误'}`)
      }
    })
    requireLastAdmin(next)
  },
}
