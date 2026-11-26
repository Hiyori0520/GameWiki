import { marked } from 'marked'
import request from '../utils/request'
import type { GameRecord } from './admin/game'
import type { GuideRecord } from '../types/admin/guide'
import type { PageResult } from './admin/client'

export interface CatalogGame extends GameRecord {
  guideCount: number
  collected?: boolean
  collectedAt?: string
}

export interface CatalogGuide extends GuideRecord {
  authorId: number
  gameCover: string
  gameCategory: string
  collected?: boolean
  liked?: boolean
  following?: boolean
}

export interface CatalogOptions {
  categories: string[]
  platforms: string[]
  tags: string[]
}

export interface PlayerProfile {
  id: number
  nickname: string
  username: string
  level: number
  title: string
  email: string
  phone: string
  location: string
  bio: string
  joinDate: string
  avatar: string
  contributions: number
  receivedLikes: number
}

export interface HistoryEntry {
  id: number
  type: 'game' | 'guide'
  targetId: number
  time: string
  title: string
  subtitle: string
}

const prefix = '/api/catalog'

export const catalogApi = {
  async games(params: Record<string, string | number> = {}, signal?: AbortSignal): Promise<PageResult<CatalogGame>> {
    return (await request.get(prefix + '/games', { params, signal })).data
  },
  async game(id: number, signal?: AbortSignal): Promise<CatalogGame> {
    return (await request.get(prefix + '/games/' + id, { signal })).data
  },
  async options(): Promise<CatalogOptions> {
    return (await request.get(prefix + '/options')).data
  },
  async guides(params: Record<string, string | number> = {}, signal?: AbortSignal): Promise<PageResult<CatalogGuide>> {
    return (await request.get(prefix + '/guides', { params, signal })).data
  },
  async guide(id: number, signal?: AbortSignal): Promise<CatalogGuide> {
    return (await request.get(prefix + '/guides/' + id, { signal })).data
  },
  async submit(input: { gameId: number; title: string; category: string; description: string; body: string }) {
    const { data } = await request.post(prefix + '/guides', input)
    return data as CatalogGuide
  },
  async favorite(type: 'game' | 'guide', id: number, active: boolean): Promise<{ active: boolean }> {
    return (await request.put(`${prefix}/favorites/${type}/${id}`, { active })).data
  },
  async like(id: number, active: boolean): Promise<{ active: boolean; likes: number }> {
    return (await request.put(`${prefix}/guides/${id}/like`, { active })).data
  },
  async follow(id: number, active: boolean): Promise<{ active: boolean }> {
    return (await request.put(`${prefix}/authors/${id}/follow`, { active })).data
  },
  async visit(type: 'game' | 'guide', id: number) {
    await request.post(`${prefix}/history/${type}/${id}`)
  },
  async profile(): Promise<PlayerProfile> {
    return (await request.get(prefix + '/profile')).data
  },
  async saveProfile(profile: Pick<PlayerProfile, 'nickname' | 'email' | 'phone' | 'location' | 'bio'>): Promise<PlayerProfile> {
    return (await request.patch(prefix + '/profile', profile)).data
  },
  async favorites(): Promise<{ games: CatalogGame[]; guides: CatalogGuide[] }> {
    return (await request.get(prefix + '/favorites')).data
  },
  async history(): Promise<HistoryEntry[]> {
    return (await request.get(prefix + '/history')).data
  },
  async clearHistory() {
    await request.delete(prefix + '/history')
  },
}

const styles = ['blue', 'purple', 'green', 'orange', 'pink', 'cyan']
function theme(id: number) { return styles[id % styles.length] ?? 'blue' }

export function displayDate(value?: string | null) { return value?.slice(0, 10) || '—' }

export function gameCard(record: CatalogGame) {
  return {
    ...record,
    category: record.categories[0] ?? '其他',
    image: record.cover,
    cover: theme(record.id),
    symbol: '✦',
    date: displayDate(record.releaseDate || record.createdAt),
    collectedAt: displayDate(record.collectedAt),
  }
}

export function guideArticle(record: CatalogGuide) {
  const body = record.body ?? ''
  const sections: { id: string; heading: string; body: string }[] = []
  let heading = '攻略正文'
  let content = ''
  function commit() {
    if (content.trim()) sections.push({ id: `guide-section-${sections.length + 1}`, heading, body: content })
    content = ''
  }
  for (const token of marked.lexer(body)) {
    if (token.type === 'heading' && token.depth <= 3) {
      commit()
      heading = token.text
    } else content += token.raw
  }
  commit()
  return {
    ...record, sections,
    summary: record.description,
    date: displayDate(record.publishedAt || record.createdAt),
    updated: displayDate(record.reviewedAt || record.createdAt),
    // 优先用攻略自己的封面（catalog_guide.cover），后端已做兜底：为空时回退到游戏封面
    gameImage: record.cover || record.gameCover,
    gameCover: theme(record.gameId),
    gameSymbol: '✦',
    readMin: Math.max(1, Math.ceil(body.length / 500)),
  }
}
