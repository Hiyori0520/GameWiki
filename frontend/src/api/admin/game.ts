import type { GameRecord as DemoGame, GameStatus as DemoStatus } from '../../admin/games'
import { gameCategories, gamePlatforms, games } from '../../admin/games'
import { guides } from '../../admin/guides'
import { adminRequest, isAdminDemo, type PageResult } from './client'

export const gameStatuses = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', PUBLISHED: '已发布', OFF_SHELF: '已下架',
} as const

export type GameStatus = keyof typeof gameStatuses
export type GameSortField = 'name' | 'releaseDate' | 'rating' | 'createdAt'
export type GameSortOrder = 'asc' | 'desc'

export interface GameQuery {
  page: number
  pageSize: number
  keyword?: string
  category?: string
  platform?: string
  company?: string
  status?: GameStatus
  sortBy?: GameSortField
  sortOrder?: GameSortOrder
}

export interface GameCreateRequest {
  name: string
  originalName: string
  englishName: string
  aliases: string[]
  company: string
  developer: string
  cover: string
  background: string
  categories: string[]
  tags: string[]
  platforms: string[]
  releaseDate: string
  description: string
}

export interface GameUpdateRequest extends GameCreateRequest {
  version?: number
}

export interface GameRecord extends GameCreateRequest {
  id: number
  rating: number | null
  votes: number
  status: GameStatus
  createdAt: string
  version?: number
}

export interface GameOptions {
  categories: string[]
  platforms: string[]
  companies: string[]
}

export interface GameCounts {
  total: number
  published: number
  pending: number
  offShelf: number
}

export interface GameStats extends GameCounts {
  recent?: GameCounts
}

export type GameTransition = 'submit-review' | 'publish' | 'off-shelf' | 'reject'
export type GameBatchCommand = { action: 'publish' | 'off-shelf' | 'delete' }
  | { action: 'add-tags' | 'set-categories'; values: string[] }
export interface GameBatchResult {
  succeeded: number
  failed: number
  errors: { id: number; reason: string }[]
}

export interface GameTransferTask {
  id: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  total?: number
  succeeded?: number
  failed?: number
  duplicates?: number
  progress?: number
  error?: string
  reportUrl?: string
  downloadUrl?: string
}

const demoStatusMap: Record<DemoStatus, GameStatus> = {
  draft: 'DRAFT', pending: 'PENDING_REVIEW', published: 'PUBLISHED', offline: 'OFF_SHELF',
}
const liveStatusMap: Record<GameStatus, DemoStatus> = {
  DRAFT: 'draft', PENDING_REVIEW: 'pending', PUBLISHED: 'published', OFF_SHELF: 'offline',
}
const statusTransitions: Record<GameTransition, { from: GameStatus[]; to: GameStatus }> = {
  'submit-review': { from: ['DRAFT'], to: 'PENDING_REVIEW' },
  reject: { from: ['PENDING_REVIEW'], to: 'DRAFT' },
  publish: { from: ['PENDING_REVIEW', 'OFF_SHELF'], to: 'PUBLISHED' },
  'off-shelf': { from: ['PUBLISHED'], to: 'OFF_SHELF' },
}

export function canTransitionGame(status: GameStatus, action: GameTransition) {
  return statusTransitions[action].from.includes(status)
}

function demoRecords() {
  return games.records.value.map(toGameRecord)
}

function toGameRecord(record: DemoGame): GameRecord {
  return {
    id: record.id,
    name: record.name,
    originalName: record.originalName ?? record.alias,
    englishName: record.englishName ?? '',
    aliases: record.aliases ?? (record.alias ? [record.alias] : []),
    company: record.company,
    developer: record.developer ?? '',
    cover: record.cover,
    background: record.background ?? '',
    categories: record.categories ?? (record.category ? [record.category] : []),
    tags: record.tags,
    platforms: record.platforms,
    releaseDate: record.releaseDate,
    description: record.description,
    rating: record.rating,
    votes: record.votes,
    status: demoStatusMap[record.status],
    createdAt: record.createdAt,
    version: record.version,
  }
}

function toDemoGame(record: GameRecord): DemoGame {
  return {
    ...record,
    alias: record.originalName || record.englishName || record.aliases[0] || '',
    category: record.categories[0] ?? '',
    status: liveStatusMap[record.status],
  }
}

function demoGame(id: number): GameRecord {
  const record = games.allRecords.value.find((item) => item.id === id && !item.deletedAt)
  if (!record) throw new Error('该游戏记录不存在。')
  return toGameRecord(record)
}

function filteredDemoGames(query: Omit<GameQuery, 'page' | 'pageSize'>) {
  const needle = query.keyword?.trim().toLocaleLowerCase()
  const rows = demoRecords().filter((record) =>
    (!needle || [record.name, record.originalName, record.englishName, record.company, record.developer, ...record.aliases, ...record.tags]
      .some((term) => term.toLocaleLowerCase().includes(needle))) &&
    (!query.category || record.categories.includes(query.category)) &&
    (!query.platform || record.platforms.includes(query.platform)) &&
    (!query.company || [record.company, record.developer].includes(query.company)) &&
    (!query.status || record.status === query.status),
  )
  if (!query.sortBy) return rows
  const sortBy = query.sortBy
  const direction = query.sortOrder === 'asc' ? 1 : -1
  return rows.sort((a, b) => {
    if (sortBy === 'rating') {
      if (a.rating === null) return b.rating === null ? 0 : 1
      if (b.rating === null) return -1
      return (a.rating - b.rating) * direction
    }
    return a[sortBy].localeCompare(b[sortBy], 'zh-CN') * direction
  })
}

function validGameRecord(record: GameRecord | null | undefined): record is GameRecord {
  return Boolean(record && Number.isSafeInteger(record.id) && record.id > 0 &&
    Object.prototype.hasOwnProperty.call(gameStatuses, record.status) &&
    [record.name, record.originalName, record.englishName, record.company, record.developer, record.cover,
      record.background, record.releaseDate, record.description, record.createdAt].every((value) => typeof value === 'string') &&
    [record.aliases, record.categories, record.tags, record.platforms]
      .every((items) => Array.isArray(items) && items.every((item) => typeof item === 'string')) &&
    (record.rating === null || Number.isFinite(record.rating)) && Number.isFinite(record.votes))
}

export async function listGames(query: GameQuery, signal?: AbortSignal): Promise<PageResult<GameRecord>> {
  if (!isAdminDemo) {
    const result = await adminRequest<PageResult<GameRecord>>({ url: '/games', params: query, signal })
    if (!result || !Number.isSafeInteger(result.total) || result.total < 0 ||
      !Array.isArray(result.records) || !result.records.every(validGameRecord)) {
      throw new Error('游戏列表接口返回的数据格式不正确。')
    }
    return result
  }
  const { page, pageSize } = query
  const rows = filteredDemoGames(query)
  return { records: rows.slice((page - 1) * pageSize, page * pageSize), total: rows.length, page, pageSize }
}

export async function getGame(id: number, signal?: AbortSignal): Promise<GameRecord> {
  if (!isAdminDemo) {
    const game = await adminRequest<GameRecord>({ url: `/games/${id}`, signal })
    if (!validGameRecord(game)) throw new Error('游戏详情接口返回的数据格式不正确。')
    return game
  }
  return demoGame(id)
}

export async function getGameOptions(signal?: AbortSignal): Promise<GameOptions> {
  if (!isAdminDemo) return adminRequest({ url: '/games/options', signal })
  const records = demoRecords()
  return {
    categories: gameCategories,
    platforms: gamePlatforms,
    companies: [...new Set(records.flatMap(({ company, developer }) => [company, developer]).filter(Boolean))].sort(),
  }
}

export async function getGameStats(signal?: AbortSignal): Promise<GameStats> {
  if (!isAdminDemo) return adminRequest({ url: '/games/stats', signal })
  const rows = demoRecords()
  const recent = rows.filter(({ createdAt }) => Date.parse(createdAt) >= Date.now() - 7 * 86_400_000)
  const count = (items: GameRecord[]) => ({
    total: items.length,
    published: items.filter((game) => game.status === 'PUBLISHED').length,
    pending: items.filter((game) => game.status === 'PENDING_REVIEW').length,
    offShelf: items.filter((game) => game.status === 'OFF_SHELF').length,
  })
  return { ...count(rows), recent: count(recent) }
}

function checkDuplicateName(name: string, exceptId?: number) {
  if (demoRecords().some((record) => record.id !== exceptId && record.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
    throw new Error('该游戏名称已存在，请使用其他名称。')
  }
}

export async function createGame(data: GameCreateRequest): Promise<GameRecord> {
  if (!isAdminDemo) return adminRequest({ url: '/games', method: 'POST', data })
  checkDuplicateName(data.name)
  const id = games.allRecords.value.reduce((max, game) => Math.max(max, game.id), 0) + 1
  const record: GameRecord = { ...data, id, createdAt: new Date().toISOString(), status: 'DRAFT', rating: null, votes: 0 }
  games.replace([toDemoGame(record), ...games.allRecords.value])
  return record
}

export async function updateGame(id: number, data: GameUpdateRequest): Promise<GameRecord> {
  if (!isAdminDemo) return adminRequest({ url: `/games/${id}`, method: 'PUT', data })
  const existing = demoGame(id)
  checkDuplicateName(data.name, id)
  const record = { ...existing, ...data }
  games.replace(games.allRecords.value.map((game) => game.id === id ? toDemoGame(record) : game))
  return record
}

function deleteDemoGame(record: DemoGame): DemoGame {
  if (guides.records.value.some((guide) => guide.gameId === record.id)) {
    throw new Error('此游戏仍关联攻略，解除关联后才能删除。')
  }
  return { ...record, deletedAt: new Date().toISOString() }
}

export async function deleteGame(id: number) {
  if (!isAdminDemo) return adminRequest<void>({ url: `/games/${id}`, method: 'DELETE' })
  demoGame(id)
  games.replace(games.allRecords.value.map((game) => game.id === id ? deleteDemoGame(game) : game))
}

function transitionDemoGame(game: DemoGame, action: GameTransition): DemoGame {
  if (!canTransitionGame(demoStatusMap[game.status], action)) throw new Error('游戏当前状态不支持此操作。')
  return { ...game, status: liveStatusMap[statusTransitions[action].to] }
}

export async function transitionGame(id: number, action: GameTransition) {
  if (!isAdminDemo) return adminRequest<void>({ url: `/games/${id}/${action}`, method: 'POST' })
  demoGame(id)
  games.replace(games.allRecords.value.map((game) => game.id === id ? transitionDemoGame(game, action) : game))
}

export async function batchGames(ids: number[], command: GameBatchCommand): Promise<GameBatchResult> {
  if (!isAdminDemo) return adminRequest({ url: '/games/batch', method: 'POST', data: { ids, ...command } })
  const errors: GameBatchResult['errors'] = []
  const changes = new Map<number, DemoGame>()
  for (const id of new Set(ids)) {
    try {
      const game = toDemoGame(demoGame(id))
      switch (command.action) {
        case 'delete': changes.set(id, deleteDemoGame(game)); break
        case 'publish':
        case 'off-shelf': changes.set(id, transitionDemoGame(game, command.action)); break
        case 'add-tags': {
          const tags = [...new Set([...game.tags, ...command.values])]
          if (tags.length > 8) throw new Error('合并后超过 8 个标签。')
          changes.set(id, { ...game, tags })
          break
        }
        case 'set-categories': changes.set(id, { ...game, category: command.values[0] ?? '', categories: command.values }); break
      }
    } catch (error) {
      errors.push({ id, reason: error instanceof Error ? error.message : '操作失败' })
    }
  }
  if (changes.size) games.replace(games.allRecords.value.map((game) => changes.get(game.id) ?? game))
  return { succeeded: changes.size, failed: errors.length, errors }
}

export async function uploadGameImage(file: File): Promise<string> {
  if (!file.size || file.size > 1024 * 1024 || !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    throw new Error('请选择不超过 1 MB 的 JPG、PNG 或 WebP 图片。')
  }
  const image = await createImageBitmap(file).catch(() => null)
  if (!image) throw new Error('无法读取图片文件。')
  image.close()
  if (!isAdminDemo) {
    const data = new FormData()
    data.append('file', file)
    const result = await adminRequest<{ url: string }>({ url: '/uploads/images', method: 'POST', data })
    return result.url
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => typeof reader.result === 'string' ? resolve(reader.result) : reject(new Error('读取图片失败。'))
    reader.onerror = () => reject(new Error('读取图片失败。'))
    reader.readAsDataURL(file)
  })
}

export function startGameImport(file: File) {
  const data = new FormData()
  data.append('file', file)
  return adminRequest<GameTransferTask>({ url: '/imports/games', method: 'POST', data })
}

export function getGameTransfer(kind: 'imports' | 'exports', id: string, signal: AbortSignal) {
  return adminRequest<GameTransferTask>({ url: `/${kind}/games/${encodeURIComponent(id)}`, signal })
}

export function startGameExport(ids: number[], query: Omit<GameQuery, 'page' | 'pageSize'>) {
  return adminRequest<GameTransferTask>({ url: '/exports/games', method: 'POST', data: ids.length ? { ids } : { query } })
}

export function demoExportGames(ids: number[], query: Omit<GameQuery, 'page' | 'pageSize'>) {
  return filteredDemoGames(query).filter(({ id }) => !ids.length || ids.includes(id))
}
