import { adminRequest, isAdminDemo, type PageResult } from './client'
import type {
  GuideAction, GuideDraft, GuideGameOption, GuideQuery, GuideRecord, GuideStats,
  GuideStatus, GuideTaxonomy, GuideTaxonomyItem,
} from '../../types/admin/guide'

const apiStatuses = {
  draft: 'DRAFT', pending: 'PENDING_REVIEW', published: 'PUBLISHED', rejected: 'REJECTED', offline: 'OFF_SHELF',
} as const

type ApiGuide = Omit<GuideRecord, 'status'> & { status: GuideStatus | typeof apiStatuses[GuideStatus] }

function fromApi(guide: ApiGuide): GuideRecord {
  if (!guide || !Number.isSafeInteger(guide.id) || !Number.isSafeInteger(guide.gameId) ||
    typeof guide.title !== 'string' || typeof guide.description !== 'string' ||
    !Array.isArray(guide.tags) || guide.tags.some(tag => typeof tag !== 'string') ||
    !Number.isFinite(guide.views) || !Number.isFinite(guide.likes)) {
    throw new Error('后台接口返回的攻略记录格式有误。')
  }
  const status = (Object.keys(apiStatuses) as GuideStatus[])
    .find(value => value === guide.status || apiStatuses[value] === guide.status)
  if (!status) throw new Error('服务端返回了未知的攻略状态。')
  return { ...guide, status }
}

function queryParams({ status, ...query }: GuideQuery) {
  return Object.fromEntries(Object.entries({
    ...query, status: status ? apiStatuses[status] : undefined,
  }).filter(([, value]) => Boolean(value)))
}

export async function listGuides(query: GuideQuery, reviews: boolean, signal?: AbortSignal): Promise<PageResult<GuideRecord>> {
  if (isAdminDemo) {
    const { filteredGuides } = await import('../../admin/guideDemo')
    const all = filteredGuides(query, reviews)
    const start = (query.page - 1) * query.pageSize
    return { records: all.slice(start, start + query.pageSize), total: all.length, page: query.page, pageSize: query.pageSize }
  }
  const result = await adminRequest<PageResult<ApiGuide>>({
    url: reviews ? '/guide-reviews' : '/guides', params: queryParams(query), signal,
  })
  if (!result || !Array.isArray(result.records) || !Number.isSafeInteger(result.total) || result.total < 0) {
    throw new Error('后台接口返回的攻略分页格式有误。')
  }
  return { ...result, records: result.records.map(fromApi) }
}

export async function getGuide(id: number, signal?: AbortSignal, forEdit = false) {
  if (isAdminDemo) return (await import('../../admin/guideDemo')).getGuide(id, forEdit)
  const guide = await adminRequest<ApiGuide>({ url: `/guides/${id}`, params: forEdit ? { forEdit: true } : undefined, signal })
  return fromApi(guide)
}

export async function getGuideStats(signal?: AbortSignal) {
  if (isAdminDemo) return (await import('../../admin/guideDemo')).guideStats()
  const stats = await adminRequest<GuideStats>({ url: '/guides/stats', signal })
  const keys: (keyof GuideStats)[] = [
    'total', 'published', 'draft', 'pending', 'offline', 'rejected',
    'addedToday', 'addedThisWeek', 'publishedThisWeek', 'totalViews',
  ]
  if (!stats || keys.some(key => !Number.isFinite(stats[key]))) throw new Error('后台接口返回的攻略统计格式有误。')
  return stats
}

export async function saveGuide(input: GuideDraft, game: GuideGameOption, previous?: GuideRecord) {
  if (isAdminDemo) {
    const demo = await import('../../admin/guideDemo')
    return previous ? demo.updateGuide(previous, input, game) : demo.createGuide(input, game)
  }
  const guide = await adminRequest<ApiGuide>({
    url: previous ? `/guides/${previous.id}` : '/guides',
    method: previous ? 'PUT' : 'POST',
    data: { ...input, version: previous?.version },
  })
  return fromApi(guide)
}

export async function changeGuide(guide: GuideRecord, action: GuideAction, reason = '') {
  if (isAdminDemo) return (await import('../../admin/guideDemo')).changeGuide(guide, action, reason)
  if (action === 'delete') {
    await adminRequest<void>({ url: `/guides/${guide.id}`, method: 'DELETE', data: { version: guide.version } })
    return
  }
  const collection = action === 'approve' || action === 'reject' ? '/guide-reviews' : '/guides'
  await adminRequest<void>({
    url: `${collection}/${guide.id}/${action}`, method: 'POST', data: { reason, version: guide.version },
  })
}

function taxonomyPath(type: GuideTaxonomy) {
  return type === 'category' ? '/guide-categories' : '/guide-tags'
}

export async function getGuideTaxonomy(type: GuideTaxonomy, signal?: AbortSignal) {
  if (isAdminDemo) return (await import('../../admin/guideDemo')).listTaxonomy(type)
  const items = await adminRequest<GuideTaxonomyItem[]>({ url: taxonomyPath(type), signal })
  if (!Array.isArray(items) || items.some(item => !item || !Number.isSafeInteger(item.id) || typeof item.name !== 'string')) {
    throw new Error('后台接口返回的攻略分类或标签格式有误。')
  }
  return items
}

export async function addGuideTaxonomy(type: GuideTaxonomy, name: string) {
  if (isAdminDemo) return (await import('../../admin/guideDemo')).addTaxonomy(type, name)
  await adminRequest<void>({ url: taxonomyPath(type), method: 'POST', data: { name } })
}

export async function deleteGuideTaxonomy(type: GuideTaxonomy, item: GuideTaxonomyItem) {
  if (isAdminDemo) return (await import('../../admin/guideDemo')).removeTaxonomy(type, item)
  await adminRequest<void>({ url: `${taxonomyPath(type)}/${item.id}`, method: 'DELETE' })
}

export async function searchGuideGames(keyword: string, signal?: AbortSignal): Promise<GuideGameOption[]> {
  if (isAdminDemo) return (await import('../../admin/guideDemo')).findGames(keyword)
  const page = await adminRequest<PageResult<GuideGameOption>>({
    url: '/games', params: { page: 1, pageSize: 20, keyword: keyword.trim() || undefined }, signal,
  })
  if (!page || !Array.isArray(page.records) ||
    page.records.some(game => !game || !Number.isSafeInteger(game.id) || typeof game.name !== 'string')) {
    throw new Error('后台接口返回的所属游戏列表格式有误。')
  }
  return page.records
}

export async function copyGuide(guide: GuideRecord, signal?: AbortSignal) {
  const source = await getGuide(guide.id, signal)
  const body = source.body ?? source.description
  return saveGuide({
    title: `${source.title.slice(0, 146)}（副本）`, description: source.description,
    gameId: source.gameId, category: source.category, tags: [...source.tags], cover: source.cover,
    body, spoiler: source.spoiler ?? false,
  }, { id: source.gameId, name: source.gameName })
}

export async function exportGuides(query: GuideQuery, reviews: boolean) {
  if (isAdminDemo) return (await import('../../admin/guideDemo')).filteredGuides(query, reviews)
  const records = await adminRequest<ApiGuide[]>({
    url: '/guides/export', params: { ...queryParams(query), page: undefined, pageSize: undefined, reviewQueue: reviews },
  })
  if (!Array.isArray(records)) throw new Error('后台接口返回的攻略导出格式有误。')
  return records.map(fromApi)
}

export async function importGuides(inputs: GuideDraft[]) {
  if (isAdminDemo) {
    const demo = await import('../../admin/guideDemo')
    return demo.importGuides(inputs.map(draft => {
      const game = demo.getGame(draft.gameId)
      if (!game) throw new Error(`关联游戏 #${draft.gameId} 不存在。`)
      return { draft, game }
    }))
  }
  await adminRequest<void>({ url: '/guides/import', method: 'POST', data: { records: inputs } })
}

const imageTypes = new Set(['image/png', 'image/jpeg', 'image/webp'])

export function safeGuideImage(value?: string) {
  if (!value) return ''
  const url = value.trim()
  if (/^https?:\/\//i.test(url) || /^\/(?![\/\\])/.test(url)) return url
  if (isAdminDemo && /^data:image\/(?:png|jpeg|webp);base64,[a-z\d+/]+=*$/i.test(url)) return url
  return ''
}

export function validateGuideDraft(input: GuideDraft, forReview = false): GuideDraft {
  const draft = {
    ...input, title: input.title.trim(), description: input.description.trim(), category: input.category.trim(),
    tags: [...new Set(input.tags.map(tag => tag.trim()).filter(Boolean))], cover: input.cover.trim(), body: input.body.trim(),
  }
  if (!draft.title || draft.title.length > 150) throw new Error('请输入 1 至 150 个字符的攻略标题。')
  if (!Number.isSafeInteger(draft.gameId) || draft.gameId < 1) throw new Error('请选择所属游戏。')
  if (!draft.category || draft.category.length > 24) throw new Error('请选择有效的攻略分类。')
  if (draft.tags.length > 12 || draft.tags.some(tag => tag.length > 24)) throw new Error('最多选择 12 个有效的标签。')
  if (draft.description.length > 1000) throw new Error('简介不能超过 1000 个字符。')
  if (draft.cover && !safeGuideImage(draft.cover)) throw new Error('封面图片地址无效。')
  if (draft.body.length > 250000) throw new Error('正文不能超过 250000 个字符。')
  if (forReview && !draft.body) throw new Error('提交审核前需要填写攻略正文。')
  return draft
}

export async function uploadGuideImage(file: File, inline: boolean, signal?: AbortSignal) {
  const limit = isAdminDemo ? inline ? 96 * 1024 : 1024 * 1024 : 5 * 1024 * 1024
  const limitLabel = isAdminDemo ? inline ? '96 KB' : '1 MB' : '5 MB'
  if (!file.size || file.size > limit || !imageTypes.has(file.type)) {
    throw new Error(`请选择 ${limitLabel} 以内的 PNG、JPEG 或 WebP 图片。`)
  }
  const signature = new Uint8Array(await file.slice(0, 12).arrayBuffer())
  const matches = file.type === 'image/png'
    ? [137, 80, 78, 71, 13, 10, 26, 10].every((byte, index) => signature[index] === byte)
    : file.type === 'image/jpeg'
      ? signature[0] === 255 && signature[1] === 216 && signature[2] === 255
      : ['R', 'I', 'F', 'F', '', '', '', '', 'W', 'E', 'B', 'P']
        .every((char, index) => !char || signature[index] === char.charCodeAt(0))
  if (!matches) throw new Error('文件内容与图片格式不一致。')
  const bitmap = await createImageBitmap(file).catch(() => null)
  if (!bitmap) throw new Error('无法读取图片文件。')
  bitmap.close()
  signal?.throwIfAborted()
  if (isAdminDemo) {
    return new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = () => reject(new Error('无法读取图片文件。'))
      reader.readAsDataURL(file)
    })
  }
  const data = new FormData()
  data.append('file', file)
  data.append('purpose', inline ? 'guide-content' : 'guide-cover')
  const { url } = await adminRequest<{ url: string }>({ url: '/uploads/images', method: 'POST', data, signal })
  const safe = safeGuideImage(url)
  if (!safe) throw new Error('上传服务返回的图片地址无效。')
  return safe
}
