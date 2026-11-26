export const guideStatusLabels = {
  published: '已发布',
  draft: '草稿',
  pending: '待审核',
  offline: '已下架',
  rejected: '已驳回',
} as const

export type GuideStatus = keyof typeof guideStatusLabels
export type GuideSort = 'newest' | 'oldest' | 'views' | 'likes'
export type GuideTaxonomy = 'category' | 'tag'
export type GuideAction = 'submit-review' | 'approve' | 'reject' | 'off-shelf' | 'delete'

export interface GuideRecord {
  id: number
  title: string
  description: string
  gameId: number
  gameName: string
  category: string
  tags: string[]
  author: string
  avatar: string
  cover: string
  views: number
  likes: number
  createdAt: string
  publishedAt: string | null
  status: GuideStatus
  body?: string
  spoiler?: boolean
  featured?: boolean
  reviewedAt?: string
  reviewReason?: string
  revisionOf?: number
  version?: number
}

export interface GuideDraft {
  title: string
  description: string
  gameId: number
  category: string
  tags: string[]
  cover: string
  body: string
  spoiler: boolean
}

export interface GuideQuery {
  keyword: string
  author: string
  gameId: number
  category: string
  tag: string
  status: GuideStatus | ''
  publishedFrom: string
  publishedTo: string
  order: GuideSort
  page: number
  pageSize: number
}

export interface GuideStats {
  total: number
  published: number
  draft: number
  pending: number
  offline: number
  rejected: number
  addedToday: number
  addedThisWeek: number
  publishedThisWeek: number
  totalViews: number
}

export interface GuideTaxonomyItem {
  id: number
  name: string
  count?: number
}

export interface GuideGameOption {
  id: number
  name: string
  cover?: string
}

export function formatGuideCount(count: number) {
  if (count >= 100000000) return `${(count / 100000000).toFixed(2)} 亿`
  if (count >= 10000) return `${(count / 10000).toFixed(1)} 万`
  return count.toLocaleString('zh-CN')
}
