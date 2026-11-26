import { avatars } from './art'
import { games } from './games'
import { guideCategories, guides, guideTags } from './guides'
import type {
  GuideAction, GuideDraft, GuideGameOption, GuideQuery, GuideRecord,
  GuideStats, GuideTaxonomy, GuideTaxonomyItem,
} from '../types/admin/guide'

const localDay = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit' })

export function filteredGuides(query: GuideQuery, reviews: boolean) {
  const keyword = query.keyword.toLocaleLowerCase()
  const author = query.author.toLocaleLowerCase()
  const gameNames = new Map(games.records.value.map(game => [game.id, game.name]))
  const results = guides.records.value.map(guide => ({ ...guide, gameName: gameNames.get(guide.gameId) ?? guide.gameName })).filter(guide => {
    const publication = guide.publishedAt ? localDay.format(new Date(guide.publishedAt)) : ''
    return (!reviews || guide.status === 'pending')
      && (!query.status || guide.status === query.status)
      && (!keyword || [guide.title, guide.gameName, guide.author].some(text => text.toLocaleLowerCase().includes(keyword)))
      && (!author || guide.author.toLocaleLowerCase().includes(author))
      && (!query.gameId || guide.gameId === query.gameId)
      && (!query.category || guide.category === query.category)
      && (!query.tag || guide.tags.includes(query.tag))
      && (!query.publishedFrom || publication >= query.publishedFrom)
      && (!query.publishedTo || Boolean(publication) && publication <= query.publishedTo)
  })
  return results.sort((a, b) => {
    if (query.order === 'views') return b.views - a.views || b.id - a.id
    if (query.order === 'likes') return b.likes - a.likes || b.id - a.id
    const direction = query.order === 'oldest' ? 1 : -1
    const time = Date.parse(a.publishedAt ?? a.createdAt) - Date.parse(b.publishedAt ?? b.createdAt)
    return direction * (time || a.id - b.id)
  })
}

export function guideStats(): GuideStats {
  const now = new Date()
  const today = localDay.format(now)
  const weekStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6)
  const isThisWeek = (value: string) => {
    const time = new Date(value)
    return time >= weekStart && time <= now
  }
  const counts: GuideStats = {
    total: 0, published: 0, draft: 0, pending: 0, offline: 0, rejected: 0,
    addedToday: 0, addedThisWeek: 0, publishedThisWeek: 0, totalViews: 0,
  }
  for (const guide of guides.records.value) {
    counts[guide.status] += 1
    if (guide.revisionOf) continue
    counts.total += 1
    counts.totalViews += guide.views
    if (localDay.format(new Date(guide.createdAt)) === today) counts.addedToday += 1
    if (isThisWeek(guide.createdAt)) counts.addedThisWeek += 1
    if (guide.status === 'published' && guide.publishedAt && isThisWeek(guide.publishedAt)) counts.publishedThisWeek += 1
  }
  return counts
}

export function getGuide(id: number, forEdit = false): GuideRecord {
  const guide = guides.records.value.find(record => record.id === id)
  if (!guide) throw new Error('攻略不存在或已删除。')
  const record = forEdit ? guides.records.value.find(candidate => candidate.revisionOf === guide.id) ?? guide : guide
  return { ...record, gameName: getGame(record.gameId)?.name ?? record.gameName }
}

function newDraft(input: GuideDraft, game: GuideGameOption, id: number): GuideRecord {
  return {
    ...input, id, gameName: game.name, author: '管理员', avatar: avatars[0] ?? '',
    views: 0, likes: 0, createdAt: new Date().toISOString(),
    publishedAt: null, status: 'draft', version: 1,
  }
}

function nextId() {
  return guides.records.value.reduce((max, guide) => Math.max(max, guide.id + 1), Date.now())
}

export function createGuide(input: GuideDraft, game: GuideGameOption) {
  const guide = newDraft(input, game, nextId())
  guides.replace([...guides.records.value, guide])
  return guide
}

export function updateGuide(previous: GuideRecord, input: GuideDraft, game: GuideGameOption) {
  const current = getGuide(previous.id)
  if (current.version !== previous.version || current.status !== previous.status) {
    throw new Error('攻略已更新，请重新加载后编辑。')
  }
  if (current.status === 'pending') throw new Error('待审核攻略暂时无法编辑。')
  if (current.status === 'published') {
    if (guides.records.value.some(record => record.revisionOf === current.id)) {
      throw new Error('已有待处理的新版本，请重新加载后编辑。')
    }
    const revision = {
      ...newDraft(input, game, nextId()), author: current.author, avatar: current.avatar, revisionOf: current.id,
    }
    guides.replace([...guides.records.value, revision])
    return revision
  }
  const updated: GuideRecord = {
    ...current, ...input, gameName: game.name, status: 'draft', version: (current.version ?? 0) + 1,
  }
  guides.replace(guides.records.value.map(guide => guide.id === current.id ? updated : guide))
  return updated
}

export function changeGuide(previous: GuideRecord, action: GuideAction, reason: string) {
  const current = getGuide(previous.id)
  if (current.version !== previous.version || current.status !== previous.status) {
    throw new Error('攻略状态已变化，请刷新后重试。')
  }
  const allowed = {
    'submit-review': ['draft', 'rejected', 'offline'],
    approve: ['pending'],
    reject: ['pending'],
    'off-shelf': ['published'],
    delete: ['draft', 'rejected', 'offline'],
  }
  if (!allowed[action].includes(current.status)) throw new Error('当前状态不允许此操作。')
  if (action === 'submit-review' && !(current.body ?? current.description).trim()) {
    throw new Error('提交审核前需要填写攻略正文。')
  }
  if (action === 'reject' && !reason.trim()) throw new Error('请填写驳回原因。')
  const records = guides.records.value
  if (action === 'delete') {
    if (records.some(guide => guide.revisionOf === current.id)) throw new Error('请先处理该攻略的新版本。')
    guides.replace(records.filter(guide => guide.id !== current.id))
    return
  }
  const now = new Date().toISOString()
  const status = { 'submit-review': 'pending', approve: 'published', reject: 'rejected', 'off-shelf': 'offline' } as const
  const next: GuideRecord = {
    ...current,
    status: status[action],
    publishedAt: action === 'approve' ? now : current.publishedAt,
    reviewedAt: action === 'approve' || action === 'reject' ? now : current.reviewedAt,
    reviewReason: action === 'approve' || action === 'reject' ? reason || undefined : undefined,
    version: (current.version ?? 0) + 1,
  }
  if (action === 'approve' && current.revisionOf) {
    const original = getGuide(current.revisionOf)
    if (original.status !== 'published') throw new Error('原攻略已下架，请先处理原攻略状态。')
    const published: GuideRecord = {
      ...next, id: original.id, views: original.views, likes: original.likes, featured: original.featured,
      createdAt: original.createdAt, revisionOf: undefined, version: (original.version ?? 0) + 1,
    }
    guides.replace(records.filter(guide => guide.id !== current.id).map(guide => guide.id === original.id ? published : guide))
    return
  }
  guides.replace(records.map(guide => guide.id === next.id ? next : guide))
}

function taxonomyCollection(type: GuideTaxonomy) {
  return type === 'category' ? guideCategories : guideTags
}

function taxonomyNames(guide: GuideRecord, type: GuideTaxonomy) {
  return type === 'category' ? [guide.category] : guide.tags
}

export function listTaxonomy(type: GuideTaxonomy): GuideTaxonomyItem[] {
  const counts = new Map<string, number>()
  for (const guide of guides.records.value) {
    for (const name of taxonomyNames(guide, type)) counts.set(name, (counts.get(name) ?? 0) + 1)
  }
  const items = taxonomyCollection(type).records.value.map(item => ({ ...item, count: counts.get(item.name) ?? 0 }))
  const known = new Set(items.map(item => item.name))
  const fromRecords = [...counts].filter(([name]) => !known.has(name))
  return [...items, ...fromRecords.map(([name, count], index) => ({ id: -(index + 1), name, count }))]
}

export function addTaxonomy(type: GuideTaxonomy, name: string) {
  const collection = taxonomyCollection(type)
  const items = collection.records.value
  if (listTaxonomy(type).some(item => item.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
    throw new Error('该名称已存在。')
  }
  const id = items.reduce((max, item) => Math.max(max, item.id), 0) + 1
  collection.replace([...items, { id, name }])
}

export function removeTaxonomy(type: GuideTaxonomy, item: GuideTaxonomyItem) {
  if (guides.records.value.some(guide => taxonomyNames(guide, type).includes(item.name))) {
    throw new Error('该名称已被攻略使用。')
  }
  const collection = taxonomyCollection(type)
  collection.replace(collection.records.value.filter(current => current.id !== item.id))
}

export function findGames(keyword: string): GuideGameOption[] {
  const normalized = keyword.trim().toLocaleLowerCase()
  return games.records.value
    .filter(game => [game.name, game.alias].some(name => name.toLocaleLowerCase().includes(normalized)))
    .slice(0, 20)
    .map(({ id, name, cover }) => ({ id, name, cover }))
}

export function importGuides(inputs: { draft: GuideDraft; game: GuideGameOption }[]) {
  const id = nextId()
  guides.replace([...guides.records.value, ...inputs.map(({ draft, game }, index) => newDraft(draft, game, id + index))])
}

export function getGame(id: number) {
  return games.records.value.find(game => game.id === id)
}
