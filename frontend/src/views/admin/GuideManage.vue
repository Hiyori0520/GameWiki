<script setup lang="ts">
import {
  AppstoreOutlined, BookOutlined, CheckCircleOutlined, ClockCircleOutlined,
  CopyOutlined, DeleteOutlined, DownOutlined, EditOutlined, EyeOutlined,
  FileTextOutlined, FireOutlined, FolderOutlined, LikeOutlined, PictureOutlined,
  PlusCircleOutlined, PlusOutlined, RightOutlined, SearchOutlined, StarFilled, UploadOutlined,
} from '@ant-design/icons-vue'
import { computed, defineAsyncComponent, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { downloadAdminJson } from '../../admin/download'
import { adminPreferences } from '../../admin/preferences'
import { adminErrorMessage, hasAdminPermission } from '../../api/admin/client'
import {
  addGuideTaxonomy, changeGuide, copyGuide, deleteGuideTaxonomy, exportGuides, getGuide, getGuideStats,
  getGuideTaxonomy, importGuides, listGuides, safeGuideImage, validateGuideDraft,
} from '../../api/admin/guide'
import AdminDialog from '../../components/admin/AdminDialog.vue'
import AdminPager from '../../components/admin/AdminPager.vue'
import AdminStats from '../../components/admin/AdminStats.vue'
import GuideGameSelect from '../../components/admin/GuideGameSelect.vue'
import {
  formatGuideCount, guideStatusLabels, type GuideAction, type GuideDraft, type GuideQuery, type GuideRecord,
  type GuideSort, type GuideStats, type GuideStatus, type GuideTaxonomy, type GuideTaxonomyItem,
} from '../../types/admin/guide'

type ModalState =
  | { kind: 'detail'; id: number }
  | { kind: 'confirm'; records: GuideRecord[]; action: GuideAction; title: string; description: string }
  | { kind: 'taxonomy'; type: GuideTaxonomy }
  | { kind: 'deleteTaxonomy'; type: GuideTaxonomy; item: GuideTaxonomyItem }

const GuideMarkdown = defineAsyncComponent(() => import('../../components/admin/GuideMarkdown.vue'))
const route = useRoute()
const reviewQueue = computed(() => route.path === '/admin/guides/reviews')
const canRead = computed(() => hasAdminPermission('guides:read'))
const canWrite = computed(() => hasAdminPermission('guides:write'))
const canReview = computed(() => hasAdminPermission('guides:review'))
const canDelete = computed(() => hasAdminPermission('guides:delete'))
const canBatch = computed(() => hasAdminPermission('guides:batch'))
const canTaxonomy = computed(() => hasAdminPermission('guides:categories'))

const keyword = ref('')
const author = ref('')
const appliedSearch = ref({ keyword: '', author: '' })
const gameId = ref(0)
const category = ref('')
const tag = ref('')
const status = ref<GuideStatus | ''>('')
const order = ref<GuideSort>('newest')
const publishedFrom = ref('')
const publishedTo = ref('')
const page = ref(1)
const size = ref<number>(adminPreferences.value.pageSize)
const total = ref(0)
const visible = ref<GuideRecord[]>([])
const selected = ref<number[]>([])
const selectedLookups = new Map<number, GuideRecord>()
const statistics = ref<GuideStats>()
const categories = ref<GuideTaxonomyItem[]>([])
const tags = ref<GuideTaxonomyItem[]>([])
const modal = ref<ModalState>()
const activeGuide = ref<GuideRecord>()
const feedback = ref<{ message: string; error?: boolean }>()
const listError = ref('')
const metaError = ref('')
const modalError = ref('')
const reviewReason = ref('')
const taxonomyName = ref('')
const tagFilter = ref<HTMLSelectElement>()
const fileInput = ref<HTMLInputElement>()
const loading = ref(true)
const detailLoading = ref(false)
const busy = ref(false)
const refresh = ref(0)
const expandedFilters = ref(false)
const failedImages = ref(new Set<string>())

const statuses = Object.keys(guideStatusLabels) as GuideStatus[]
const tabOptions: { value: GuideStatus | ''; label: string }[] = [
  { value: '', label: '全部攻略' },
  ...(['published', 'draft', 'pending', 'offline'] as const).map(value => ({ value, label: guideStatusLabels[value] })),
]

const categoryNames = computed(() => categories.value.map(item => item.name))
const tagNames = computed(() => tags.value.map(item => item.name))
const categoryCounts = computed(() => new Map(categories.value.map(item => [item.name, item.count])))
const formatter = new Intl.NumberFormat('zh-CN')
const localTime = new Intl.DateTimeFormat('sv-SE', { dateStyle: 'short', timeStyle: 'short' })
const stats = computed(() => {
  const data = statistics.value
  const count = (value?: number) => value === undefined ? '—' : formatter.format(value)
  return [
    {
      label: '攻略总数', value: count(data?.total), icon: BookOutlined, tone: 'violet',
      change: data ? `↑ +${count(data.addedThisWeek)}` : undefined, note: data ? '（近7天）' : undefined,
    },
    {
      label: '已发布', value: count(data?.published), icon: CheckCircleOutlined, tone: 'blue',
      change: data ? `↑ +${count(data.publishedThisWeek)}` : undefined, note: data ? '（近7天）' : undefined,
    },
    { label: '草稿', value: count(data?.draft), icon: FileTextOutlined, tone: 'violet' },
    { label: '待审核', value: count(data?.pending), icon: ClockCircleOutlined, tone: 'orange' },
    { label: '今日新增', value: count(data?.addedToday), icon: PlusCircleOutlined, tone: 'green' },
    { label: '总浏览量', value: data ? formatGuideCount(data.totalViews) : '—', icon: FireOutlined, tone: 'pink' },
  ] as const
})

function displayTime(value: string) {
  const date = new Date(value)
  return Number.isFinite(date.valueOf()) ? localTime.format(date) : '—'
}

function imageSource(value: string) {
  const source = safeGuideImage(value)
  return failedImages.value.has(source) ? '' : source
}

const filters = computed(() => ({
  ...appliedSearch.value, gameId: gameId.value, category: category.value, tag: tag.value,
  status: reviewQueue.value ? 'pending' as const : status.value,
  publishedFrom: publishedFrom.value, publishedTo: publishedTo.value, order: order.value,
}))
const query = computed<GuideQuery>(() => ({ ...filters.value, page: page.value, pageSize: size.value }))
const selectedRecords = computed(() => selected.value.flatMap(id => {
  const guide = selectedLookups.get(id)
  return guide ? [guide] : []
}))
const pageSelected = computed(() => visible.value.filter(guide => selected.value.includes(guide.id)).length)
const allSelected = computed(() => visible.value.length > 0 && pageSelected.value === visible.value.length)
const submittable = computed(() => selectedRecords.value.filter(guide => canChange(guide, 'submit-review')))
const removable = computed(() => selectedRecords.value.filter(guide => canChange(guide, 'off-shelf')))
const deletable = computed(() => selectedRecords.value.filter(guide => canChange(guide, 'delete')))
const hasFilters = computed(() => Boolean(
  keyword.value || author.value || gameId.value || category.value || tag.value || (status.value && !reviewQueue.value)
  || order.value !== 'newest' || publishedFrom.value || publishedTo.value,
))

watch([keyword, author], ([q, name], _, onCleanup) => {
  const timer = window.setTimeout(() => { appliedSearch.value = { keyword: q.trim(), author: name.trim() } }, 250)
  onCleanup(() => window.clearTimeout(timer))
})
watch(filters, () => {
  page.value = 1
  selected.value = []
  selectedLookups.clear()
}, { flush: 'sync' })
watch(size, () => { page.value = 1 }, { flush: 'sync' })

watch([() => route.path, () => route.query], ([path, params]) => {
  resetFilters()
  keyword.value = typeof params.q === 'string' ? params.q : ''
  appliedSearch.value = { keyword: keyword.value.trim(), author: '' }
  const initial = params.status
  status.value = typeof initial === 'string' && statuses.includes(initial as GuideStatus) ? initial as GuideStatus : ''
  tag.value = typeof params.tag === 'string' ? params.tag : ''
  category.value = typeof params.category === 'string' ? params.category : ''
  const id = typeof params.id === 'string' ? Number(params.id) : 0
  if (Number.isSafeInteger(id) && id > 0) showDetails(id)
  else if (path === '/admin/guides/categories' && canTaxonomy.value) openModal({ kind: 'taxonomy', type: 'category' })
  else closeModal()
  if (params.focus === 'tag') void nextTick(() => tagFilter.value?.focus())
}, { immediate: true })

watch([query, reviewQueue, refresh, canRead], async ([request, reviews, , allowed], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  listError.value = ''
  loading.value = true
  try {
    if (!allowed) throw new Error('当前账号没有查看攻略列表的权限。')
    if (request.publishedFrom && request.publishedTo && request.publishedFrom > request.publishedTo) {
      throw new Error('发布时间的起始日期必须早于或等于结束日期。')
    }
    const result = await listGuides(request, reviews, controller.signal)
    if (controller.signal.aborted) return
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > lastPage) {
      page.value = lastPage
      return
    }
    visible.value = result.records
    for (const guide of result.records) selectedLookups.set(guide.id, guide)
    const retained = new Set([...selected.value, ...result.records.map(guide => guide.id)])
    for (const id of selectedLookups.keys()) if (!retained.has(id)) selectedLookups.delete(id)
  } catch (failure) {
    if (!controller.signal.aborted) {
      visible.value = []
      total.value = 0
      selected.value = []
      listError.value = adminErrorMessage(failure)
    }
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}, { immediate: true })

watch([refresh, canRead], async ([, allowed], _, onCleanup) => {
  if (!allowed) {
    statistics.value = undefined
    categories.value = []
    tags.value = []
    return
  }
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  metaError.value = ''
  const [summary, categoryList, tagList] = await Promise.allSettled([
    getGuideStats(controller.signal), getGuideTaxonomy('category', controller.signal), getGuideTaxonomy('tag', controller.signal),
  ])
  if (controller.signal.aborted) return
  statistics.value = summary.status === 'fulfilled' ? summary.value : undefined
  categories.value = categoryList.status === 'fulfilled' ? categoryList.value : []
  tags.value = tagList.status === 'fulfilled' ? tagList.value : []
  const errors = [summary, categoryList, tagList].filter(result => result.status === 'rejected')
  if (errors.length) {
    metaError.value = [...new Set(errors.map(result => adminErrorMessage(result.reason)))].join(' ')
  }
}, { immediate: true })

watch(() => modal.value?.kind === 'detail' ? modal.value.id : undefined, async (id, _, onCleanup) => {
  activeGuide.value = undefined
  if (!id) return
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  detailLoading.value = true
  try {
    if (!canRead.value) throw new Error('当前账号没有查看攻略详情的权限。')
    const record = await getGuide(id, controller.signal)
    if (!controller.signal.aborted) activeGuide.value = record
  } catch (failure) {
    if (!controller.signal.aborted) modalError.value = adminErrorMessage(failure)
  } finally {
    if (!controller.signal.aborted) detailLoading.value = false
  }
}, { immediate: true })

function resetFilters() {
  keyword.value = ''
  author.value = ''
  appliedSearch.value = { keyword: '', author: '' }
  gameId.value = 0
  category.value = ''
  tag.value = ''
  status.value = ''
  order.value = 'newest'
  publishedFrom.value = ''
  publishedTo.value = ''
  page.value = 1
}

function togglePage(checked: boolean) {
  const ids = visible.value.map(guide => guide.id)
  selected.value = checked
    ? [...new Set([...selected.value, ...ids])]
    : selected.value.filter(id => !ids.includes(id))
}

function openModal(next: ModalState) {
  if (busy.value) return
  feedback.value = undefined
  modalError.value = ''
  reviewReason.value = ''
  taxonomyName.value = ''
  modal.value = next
}

function closeModal() {
  if (!busy.value) modal.value = undefined
}

function showDetails(id: number) {
  openModal({ kind: 'detail', id })
}

function canChange(guide: GuideRecord, action: GuideAction) {
  switch (action) {
    case 'submit-review': return canWrite.value && ['draft', 'rejected', 'offline'].includes(guide.status)
    case 'off-shelf': return canWrite.value && guide.status === 'published'
    case 'delete': return canDelete.value && ['draft', 'rejected', 'offline'].includes(guide.status)
    case 'approve':
    case 'reject': return canReview.value && guide.status === 'pending'
  }
}

function confirmChange(records: GuideRecord[], action: GuideAction) {
  if (!records.length || !records.every(guide => canChange(guide, action))
    || (records.length > 1 && !canBatch.value)) return
  const titles: Record<GuideAction, string> = {
    'submit-review': '提交审核', approve: '审核通过并发布', reject: '驳回攻略',
    'off-shelf': '下架攻略', delete: '删除攻略',
  }
  const title = titles[action]
  const subject = records.length === 1 ? `《${records[0]?.title}》` : `${records.length} 篇攻略`
  openModal({
    kind: 'confirm', title, records, action,
    description: action === 'delete'
      ? `确定删除 ${subject}？删除后将无法在列表中访问。` : `确认对 ${subject} 执行「${title}」？`,
  })
}

function quickStatus(guide: GuideRecord) {
  if (guide.status === 'pending') return showDetails(guide.id)
  confirmChange([guide], guide.status === 'published' ? 'off-shelf' : 'submit-review')
}

async function submitChange() {
  const change = modal.value
  if (busy.value || change?.kind !== 'confirm' || !change.records.every(guide => canChange(guide, change.action))
    || (change.records.length > 1 && !canBatch.value)) return
  const reason = reviewReason.value.trim()
  if (change.action === 'reject' && !reason) {
    modalError.value = '请填写驳回原因。'
    return
  }
  modalError.value = ''
  busy.value = true
  const errors: string[] = []
  const successful: number[] = []
  try {
    for (const guide of change.records) {
      try {
        await changeGuide(guide, change.action, reason)
        successful.push(guide.id)
      } catch (failure) {
        errors.push(`${guide.title}：${adminErrorMessage(failure)}`)
      }
    }
    if (!successful.length) {
      modalError.value = errors.join('；')
      return
    }
    const ids = new Set(successful)
    selected.value = selected.value.filter(id => !ids.has(id))
    modal.value = undefined
    feedback.value = {
      message: `「${change.title}」已处理 ${successful.length} 篇。${errors.join('；')}`,
      error: errors.length > 0,
    }
  } finally {
    refresh.value += 1
    busy.value = false
  }
}

async function duplicate(guide: GuideRecord) {
  if (!canWrite.value || busy.value) return
  busy.value = true
  try {
    await copyGuide(guide)
    resetFilters()
    status.value = 'draft'
    feedback.value = { message: '已创建草稿副本。' }
    refresh.value += 1
  } catch (failure) {
    feedback.value = { message: adminErrorMessage(failure), error: true }
  } finally {
    busy.value = false
  }
}

function closeMenu(event: MouseEvent) {
  const button = event.currentTarget
  if (button instanceof HTMLElement) button.closest('details')?.removeAttribute('open')
}

function dismissMenu(event: Event) {
  const menu = event.currentTarget
  if (!(menu instanceof HTMLDetailsElement)) return
  if (event instanceof FocusEvent && event.relatedTarget instanceof Node && menu.contains(event.relatedTarget)) return
  menu.open = false
}

function batchChange(event: MouseEvent, targets: GuideRecord[], action: GuideAction) {
  closeMenu(event)
  confirmChange(targets, action)
}

async function download(event: MouseEvent) {
  closeMenu(event)
  if (!canBatch.value || busy.value) return
  busy.value = true
  try {
    const result = await exportGuides({ ...query.value, keyword: keyword.value.trim(), author: author.value.trim() }, reviewQueue.value)
    downloadAdminJson('gamewiki-guides.json', result)
    feedback.value = { message: `已导出当前筛选范围内的 ${result.length} 篇攻略。` }
  } catch (failure) {
    feedback.value = { message: adminErrorMessage(failure), error: true }
  } finally {
    busy.value = false
  }
}

function startImport(event: MouseEvent) {
  closeMenu(event)
  fileInput.value?.click()
}

function parseImport(item: unknown, index: number): GuideDraft {
  if (!item || typeof item !== 'object' || Array.isArray(item)) throw new Error(`第 ${index + 1} 项应为攻略对象。`)
  const input = item as Record<string, unknown>
  const description = typeof input.description === 'string' ? input.description : ''
  try {
    return validateGuideDraft({
      title: typeof input.title === 'string' ? input.title : '',
      description,
      body: typeof input.body === 'string' ? input.body : description,
      category: typeof input.category === 'string' ? input.category : '',
      tags: Array.isArray(input.tags) ? input.tags.filter((tag): tag is string => typeof tag === 'string') : [],
      cover: typeof input.cover === 'string' ? input.cover : '',
      gameId: typeof input.gameId === 'number' ? input.gameId : 0,
      spoiler: input.spoiler === true,
    })
  } catch (failure) {
    throw new Error(`第 ${index + 1} 项：${adminErrorMessage(failure)}`)
  }
}

async function importFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || busy.value || !canWrite.value || !canBatch.value) return
  busy.value = true
  try {
    if (file.size > 2 * 1024 * 1024) throw new Error('导入文件须小于等于 2 MB。')
    let rows: unknown
    try {
      rows = JSON.parse((await file.text()).replace(/^\uFEFF/, '')) as unknown
    } catch {
      throw new Error('文件格式无效。请使用 UTF-8 编码的 JSON 文件。')
    }
    if (!Array.isArray(rows) || !rows.length || rows.length > 500) {
      throw new Error('JSON 文件应包含 1 至 500 篇攻略。')
    }
    await importGuides(rows.map(parseImport))
    resetFilters()
    status.value = 'draft'
    feedback.value = { message: `已导入 ${rows.length} 篇草稿。` }
    refresh.value += 1
  } catch (failure) {
    feedback.value = { message: adminErrorMessage(failure), error: true }
  } finally {
    busy.value = false
  }
}

const taxonomyItems = computed(() => modal.value?.kind === 'taxonomy' && modal.value.type === 'tag' ? tags.value : categories.value)

async function saveTaxonomy() {
  const state = modal.value
  if (busy.value || !canTaxonomy.value || state?.kind !== 'taxonomy') return
  const name = taxonomyName.value.trim()
  if (!name || name.length > 24 || taxonomyItems.value.some(item => item.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
    modalError.value = !name || name.length > 24 ? '请输入 1 至 24 个字符的名称。' : '该名称已存在。'
    return
  }
  busy.value = true
  modalError.value = ''
  try {
    await addGuideTaxonomy(state.type, name)
    taxonomyName.value = ''
    refresh.value += 1
  } catch (failure) {
    modalError.value = adminErrorMessage(failure)
  } finally {
    busy.value = false
  }
}

async function removeTaxonomy() {
  const state = modal.value
  if (busy.value || !canTaxonomy.value || state?.kind !== 'deleteTaxonomy') return
  busy.value = true
  try {
    await deleteGuideTaxonomy(state.type, state.item)
    if (state.type === 'category' && category.value === state.item.name) category.value = ''
    if (state.type === 'tag' && tag.value === state.item.name) tag.value = ''
    modal.value = { kind: 'taxonomy', type: state.type }
    refresh.value += 1
  } catch (failure) {
    modalError.value = adminErrorMessage(failure)
  } finally {
    busy.value = false
  }
}

const tagTones = ['is-blue', '', 'is-pink', 'is-green', 'is-orange']
</script>

<template>
  <div class="guide-page">
    <AdminStats :items="[...stats]" />

    <div class="guide-content">
      <section class="admin-panel guide-list" aria-label="攻略列表">
        <nav class="guide-tabs" aria-label="快捷状态筛选">
          <RouterLink v-if="reviewQueue" class="admin-button" to="/admin/guides">返回攻略列表</RouterLink>
          <button v-for="tab in reviewQueue ? tabOptions.filter(option => option.value === 'pending') : tabOptions"
            :key="tab.value" type="button"
            :class="{ 'is-active': status === tab.value }"
            :aria-pressed="status === tab.value" @click="status = tab.value">
            {{ tab.label }} <span v-if="statistics">({{ formatter.format(tab.value ? statistics[tab.value] : statistics.total) }})</span>
          </button>
        </nav>

        <div class="admin-toolbar guide-toolbar">
          <label class="guide-search">
            <SearchOutlined aria-hidden="true" />
            <input v-model="keyword" class="admin-input" type="search" aria-label="搜索攻略"
              placeholder="搜索攻略标题、游戏名称、作者…" />
          </label>

          <div class="guide-actions">
            <RouterLink v-if="canWrite && !reviewQueue" class="admin-button is-primary" to="/admin/guides/new">
              <PlusOutlined aria-hidden="true" /> 新建攻略
            </RouterLink>
            <details v-if="canBatch && !reviewQueue" class="guide-menu" name="guide-actions-menu" @focusout="dismissMenu" @keydown.esc="dismissMenu">
              <summary class="admin-button">
                <AppstoreOutlined aria-hidden="true" /> 批量操作
                <span v-if="selected.length">({{ selected.length }})</span>
                <DownOutlined aria-hidden="true" />
              </summary>
              <div class="admin-menu guide-menu-items">
                <button type="button" :disabled="busy || loading || !submittable.length"
                  @click="batchChange($event, submittable, 'submit-review')">提交所选攻略审核</button>
                <button type="button" :disabled="busy || loading || !removable.length"
                  @click="batchChange($event, removable, 'off-shelf')">下架所选已发布攻略</button>
                <button type="button" :disabled="busy || loading || !deletable.length"
                  @click="batchChange($event, deletable, 'delete')">删除所选可删除攻略</button>
              </div>
            </details>
            <details v-if="canBatch && !reviewQueue" class="guide-menu" name="guide-actions-menu" @focusout="dismissMenu" @keydown.esc="dismissMenu">
              <summary class="admin-button">
                <UploadOutlined aria-hidden="true" /> 导入/导出 <DownOutlined aria-hidden="true" />
              </summary>
              <div class="admin-menu guide-menu-items">
                <button v-if="canWrite" type="button" :disabled="busy" @click="startImport">从 JSON 导入草稿</button>
                <button type="button" :disabled="busy || loading" @click="download">导出筛选结果为 JSON</button>
              </div>
            </details>
            <input ref="fileInput" type="file" accept=".json,application/json" hidden @change="importFile" />
          </div>
        </div>

        <div class="admin-filters guide-filters">
          <label>所属游戏
            <GuideGameSelect v-model="gameId" class="guide-game-filter" placeholder="全部游戏" :disabled="!canRead" />
          </label>
          <label>攻略分类
            <select v-model="category" class="admin-select" aria-label="攻略分类">
              <option value="">全部分类</option>
              <option v-for="name in categoryNames" :key="name" :value="name">{{ name }}</option>
            </select>
          </label>
          <label>标签
            <select ref="tagFilter" v-model="tag" class="admin-select" aria-label="标签">
              <option value="">全部标签</option>
              <option v-for="name in tagNames" :key="name" :value="name">{{ name }}</option>
            </select>
          </label>
          <label v-if="!reviewQueue">发布状态
            <select v-model="status" class="admin-select" aria-label="发布状态">
              <option value="">全部状态</option>
              <option v-for="value in statuses" :key="value" :value="value">{{ guideStatusLabels[value] }}</option>
            </select>
          </label>
          <label>排序
            <select v-model="order" class="admin-select" aria-label="排序">
              <option value="newest">发布时间（最新）</option>
              <option value="oldest">发布时间（最早）</option>
              <option value="views">浏览量（最高）</option>
              <option value="likes">点赞数（最高）</option>
            </select>
          </label>
          <button type="button" class="guide-text-button" :aria-expanded="expandedFilters"
            @click="expandedFilters = !expandedFilters">{{ expandedFilters ? '收起筛选' : '更多筛选' }}</button>
          <button v-if="hasFilters" type="button" class="guide-text-button" @click="resetFilters">重置</button>
        </div>
        <div v-if="expandedFilters" class="admin-filters guide-extra-filters">
          <label>作者 <input v-model="author" type="search" class="admin-input" aria-label="筛选攻略作者" /></label>
          <label>发布起始 <input v-model="publishedFrom" class="admin-input" type="date" :max="publishedTo || undefined" /></label>
          <label>发布截止 <input v-model="publishedTo" class="admin-input" type="date" :min="publishedFrom || undefined" /></label>
        </div>

        <p v-if="feedback" class="guide-feedback" :class="{ 'is-error': feedback.error }"
          :role="feedback.error ? 'alert' : 'status'">{{ feedback.message }}</p>
        <p v-if="metaError" class="guide-feedback is-error" role="alert">
          无法完整加载攻略统计和分类标签。{{ metaError }}
          <button type="button" class="guide-text-button" @click="refresh++">重试</button>
        </p>

        <div class="admin-table-scroll guide-table-scroll" tabindex="0" role="region" aria-label="攻略表格" :aria-busy="loading">
          <table class="admin-table guide-table">
            <thead>
              <tr>
                <th scope="col">
                  <input type="checkbox" aria-label="选择当前页全部攻略"
                    :checked="allSelected" :indeterminate.prop="pageSelected > 0 && !allSelected"
                    :disabled="!canBatch || reviewQueue || loading || busy || !visible.length"
                    @change="togglePage(($event.target as HTMLInputElement).checked)" />
                </th>
                <th scope="col">封面</th><th scope="col">攻略标题</th><th scope="col">所属游戏</th>
                <th scope="col">分类</th><th scope="col">标签</th><th scope="col">作者</th>
                <th scope="col">浏览量</th><th scope="col">点赞</th><th scope="col">发布时间</th>
                <th scope="col">状态</th><th scope="col" class="guide-table-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="guide in loading ? [] : visible" :key="guide.id">
                <td>
                  <input v-model="selected" type="checkbox" :value="guide.id" :aria-label="`选择 ${guide.title}`"
                    :disabled="!canBatch || reviewQueue || busy" />
                </td>
                <td>
                  <img v-if="imageSource(guide.cover)" class="admin-row-cover guide-cover" :src="imageSource(guide.cover)" alt=""
                    loading="lazy" @error="failedImages.add(safeGuideImage(guide.cover))" />
                  <span v-else class="guide-cover guide-placeholder"><PictureOutlined aria-hidden="true" /></span>
                </td>
                <td class="guide-title-cell">
                  <button type="button" class="admin-row-title guide-title-text" :title="guide.title"
                    @click="showDetails(guide.id)">
                    {{ guide.title }} <StarFilled v-if="guide.featured" class="guide-featured" aria-label="精选" />
                  </button>
                  <span class="admin-row-subtitle guide-description" :title="guide.description">{{ guide.description }}</span>
                </td>
                <td class="guide-game-cell">{{ guide.gameName }}</td>
                <td><span class="admin-tag" :class="guide.id % 2 ? 'is-pink' : ''">{{ guide.category }}</span></td>
                <td class="guide-tags-cell">
                  <div class="guide-row-tags">
                    <span v-for="(name, index) in guide.tags" :key="name" class="admin-tag"
                      :class="tagTones[index % tagTones.length]">{{ name }}</span>
                  </div>
                </td>
                <td><div class="guide-author">
                  <img v-if="imageSource(guide.avatar)" :src="imageSource(guide.avatar)" alt="" loading="lazy"
                    @error="failedImages.add(safeGuideImage(guide.avatar))" />
                  <span>{{ guide.author }}</span>
                </div></td>
                <td class="guide-nowrap">{{ formatGuideCount(guide.views) }}</td>
                <td class="guide-nowrap"><LikeOutlined aria-hidden="true" /> {{ formatGuideCount(guide.likes) }}</td>
                <td class="guide-time">
                  <template v-if="guide.publishedAt">
                    {{ displayTime(guide.publishedAt).slice(0, 10) }}<br />{{ displayTime(guide.publishedAt).slice(11) }}
                  </template>
                  <span v-else>—</span>
                </td>
                <td>
                  <div class="guide-status">
                    <button v-if="guide.status !== 'pending' && guide.status !== 'rejected'" type="button"
                      class="admin-switch" :class="{ 'is-on': guide.status === 'published' }"
                      role="switch" :aria-checked="guide.status === 'published'" :aria-label="`发布状态 ${guide.title}`"
                      :title="guide.status === 'published' ? '下架攻略' : '提交审核'"
                      :disabled="!canWrite || busy" @click="quickStatus(guide)">
                      <span></span>
                    </button>
                    <button v-else-if="guide.status === 'pending' || canWrite" type="button"
                      class="guide-text-button" :disabled="busy" @click="quickStatus(guide)"
                      :title="guide.status === 'pending' ? '打开审核' : '重新提交审核'">
                      {{ guideStatusLabels[guide.status] }}
                    </button>
                    <span v-else>{{ guideStatusLabels[guide.status] }}</span>
                    <span v-if="guide.status !== 'pending' && guide.status !== 'rejected'">
                      {{ guideStatusLabels[guide.status] }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="guide-row-actions">
                    <button type="button" class="admin-icon-button" :title="guide.status === 'pending' && canReview ? '审核' : '查看'"
                      :aria-label="`${guide.status === 'pending' && canReview ? '审核' : '查看'} ${guide.title}`" :disabled="busy"
                      @click="showDetails(guide.id)"><EyeOutlined /></button>
                    <RouterLink v-if="canWrite && guide.status !== 'pending'" class="admin-icon-button" :to="`/admin/guides/${guide.id}/edit`"
                      title="编辑攻略" :aria-label="`编辑 ${guide.title}`"><EditOutlined /></RouterLink>
                    <button v-if="canWrite" type="button" class="admin-icon-button" title="复制为草稿" :disabled="busy"
                      :aria-label="`复制 ${guide.title} 为草稿`" @click="duplicate(guide)"><CopyOutlined /></button>
                    <button v-if="canDelete" type="button" class="admin-icon-button is-danger" title="删除"
                      :disabled="busy || !canChange(guide, 'delete')" :aria-label="`删除 ${guide.title}`"
                      @click="confirmChange([guide], 'delete')"><DeleteOutlined /></button>
                  </div>
                </td>
              </tr>
              <tr v-if="loading || listError || !visible.length">
                <td colspan="12">
                  <div v-if="loading" class="admin-empty" role="status">正在加载攻略…</div>
                  <div v-else-if="listError" class="admin-empty" role="alert">
                    <p>{{ listError }}</p>
                    <button v-if="canRead" type="button" class="admin-button" @click="refresh++">重试</button>
                  </div>
                  <div v-else class="admin-empty">没有符合当前条件的攻略。</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AdminPager v-model:page="page" v-model:size="size" :total="total" />
      </section>

      <aside class="guide-sidebar" aria-label="攻略分类和标签">
        <section class="admin-panel guide-taxonomy">
          <div class="guide-panel-heading">
            <h2>攻略分类</h2>
            <button v-if="canTaxonomy" type="button" class="guide-text-button" :disabled="busy"
              @click="openModal({ kind: 'taxonomy', type: 'category' })"><PlusOutlined aria-hidden="true" /> 管理</button>
          </div>
          <ul class="guide-category-list">
            <li v-for="name in categoryNames" :key="name">
              <button type="button" :aria-pressed="category === name"
                @click="category = category === name ? '' : name">
                <DownOutlined v-if="category === name" class="guide-category-chevron" aria-hidden="true" />
                <RightOutlined v-else class="guide-category-chevron" aria-hidden="true" />
                <FolderOutlined class="guide-category-icon" aria-hidden="true" />
                <span>{{ name }}</span>
                <span v-if="categoryCounts.get(name) !== undefined" class="guide-count">({{ formatter.format(categoryCounts.get(name) ?? 0) }})</span>
              </button>
            </li>
          </ul>
        </section>
        <section class="admin-panel guide-taxonomy">
          <div class="guide-panel-heading">
            <h2>热门标签</h2>
            <button v-if="canTaxonomy" type="button" class="guide-text-button" :disabled="busy"
              @click="openModal({ kind: 'taxonomy', type: 'tag' })"><PlusOutlined aria-hidden="true" /> 管理</button>
          </div>
          <div class="guide-hot-tags">
            <button v-for="(name, index) in tagNames" :key="name" type="button" class="admin-tag"
              :class="tagTones[index % tagTones.length]" :aria-pressed="tag === name"
              @click="tag = tag === name ? '' : name">{{ name }}</button>
          </div>
        </section>
      </aside>
    </div>

    <AdminDialog v-if="modal?.kind === 'detail'" :key="`guide-${modal.id}`"
      :title="activeGuide?.status === 'pending' && canReview ? '审核攻略' : '攻略详情'" @close="closeModal">
      <div v-if="detailLoading" class="admin-empty" role="status">正在读取攻略…</div>
      <p v-else-if="modalError" class="admin-form-error" role="alert">{{ modalError }}</p>
      <div v-else-if="activeGuide" class="guide-detail">
        <img v-if="imageSource(activeGuide.cover)" :src="imageSource(activeGuide.cover)" alt="" class="guide-detail-cover"
          loading="lazy" @error="failedImages.add(safeGuideImage(activeGuide.cover))" />
        <h3>{{ activeGuide.title }}</h3>
        <p v-if="activeGuide.description">{{ activeGuide.description }}</p>
        <dl>
          <dt>所属游戏</dt><dd>{{ activeGuide.gameName }}</dd>
          <dt>作者</dt><dd>{{ activeGuide.author }}</dd>
          <dt>分类</dt><dd>{{ activeGuide.category }}</dd>
          <dt>标签</dt><dd>{{ activeGuide.tags.join('、') || '—' }}</dd>
          <dt>状态</dt><dd>{{ guideStatusLabels[activeGuide.status] }}</dd>
          <dt>创建时间</dt><dd>{{ displayTime(activeGuide.createdAt) }}</dd>
          <dt>发布时间</dt><dd>{{ activeGuide.publishedAt ? displayTime(activeGuide.publishedAt) : '—' }}</dd>
          <template v-if="activeGuide.revisionOf">
            <dt>关联版本</dt><dd>攻略 #{{ activeGuide.revisionOf }} 的修订</dd>
          </template>
          <template v-if="activeGuide.reviewedAt">
            <dt>审核时间</dt><dd>{{ displayTime(activeGuide.reviewedAt) }}</dd>
          </template>
          <template v-if="activeGuide.reviewReason">
            <dt>审核意见</dt><dd>{{ activeGuide.reviewReason }}</dd>
          </template>
        </dl>
        <span v-if="activeGuide.spoiler" class="admin-tag is-orange">含剧透</span>
        <GuideMarkdown :source="activeGuide.body ?? activeGuide.description" with-contents />
      </div>
      <template #footer>
        <button type="button" class="admin-button" @click="closeModal">关闭</button>
        <template v-if="activeGuide && canChange(activeGuide, 'approve')">
          <button type="button" class="admin-button" @click="confirmChange([activeGuide], 'reject')">驳回</button>
          <button type="button" class="admin-button is-primary"
            @click="confirmChange([activeGuide], 'approve')">审核通过并发布</button>
        </template>
        <button v-else-if="activeGuide && canChange(activeGuide, 'submit-review')" type="button" class="admin-button is-primary"
          @click="confirmChange([activeGuide], 'submit-review')">提交审核</button>
      </template>
    </AdminDialog>

    <AdminDialog v-if="modal?.kind === 'confirm'" key="guide-confirm" :title="modal.title" @close="closeModal">
      <p>{{ modal.description }}</p>
      <form id="guide-confirm-form" class="admin-modal-form guide-dialog-form" @submit.prevent="submitChange">
        <label v-if="modal.action === 'reject' || modal.action === 'approve'">
          {{ modal.action === 'reject' ? '驳回原因' : '审核意见（选填）' }}
          <textarea v-model="reviewReason" class="admin-input" maxlength="300" rows="3" :required="modal.action === 'reject'" :disabled="busy" />
        </label>
        <p v-if="modalError" class="admin-form-error" role="alert">{{ modalError }}</p>
      </form>
      <template #footer>
        <button type="button" class="admin-button" :disabled="busy" @click="closeModal">取消</button>
        <button type="submit" form="guide-confirm-form" class="admin-button is-primary" :disabled="busy">{{ busy ? '处理中…' : '确认' }}</button>
      </template>
    </AdminDialog>

    <AdminDialog v-if="modal?.kind === 'taxonomy'" key="guide-taxonomy"
      :title="modal.type === 'category' ? '管理攻略分类' : '管理攻略标签'" @close="closeModal">
      <form id="guide-taxonomy-form" class="admin-modal-form guide-dialog-form" @submit.prevent="saveTaxonomy">
        <div class="guide-hot-tags">
          <span v-for="item in taxonomyItems" :key="item.id" class="admin-tag">
            {{ item.name }}
            <button v-if="item.id > 0 && !item.count" type="button" class="guide-text-button" :disabled="busy"
              :aria-label="`删除 ${item.name}`" @click="openModal({ kind: 'deleteTaxonomy', type: modal.type, item })">×</button>
          </span>
        </div>
        <label>{{ modal.type === 'category' ? '新增分类' : '新增标签' }}
          <input v-model="taxonomyName" class="admin-input" required maxlength="24" :disabled="busy" />
        </label>
        <p v-if="modalError" class="admin-form-error" role="alert">{{ modalError }}</p>
      </form>
      <template #footer>
        <button type="button" class="admin-button" :disabled="busy" @click="closeModal">关闭</button>
        <button type="submit" form="guide-taxonomy-form" class="admin-button is-primary" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </template>
    </AdminDialog>

    <AdminDialog v-if="modal?.kind === 'deleteTaxonomy'" key="guide-taxonomy-delete" title="删除攻略分类或标签" @close="closeModal">
      <p>确定删除「{{ modal.item.name }}」？已关联攻略的分类或标签无法删除。</p>
      <p v-if="modalError" class="admin-form-error" role="alert">{{ modalError }}</p>
      <template #footer>
        <button type="button" class="admin-button" :disabled="busy" @click="openModal({ kind: 'taxonomy', type: modal.type })">取消</button>
        <button type="button" class="admin-button is-primary" :disabled="busy" @click="removeTaxonomy">{{ busy ? '处理中…' : '确认删除' }}</button>
      </template>
    </AdminDialog>
  </div>
</template>

<style scoped>
.guide-page { display: grid; gap: 3px; min-width: 0; }
.guide-content { display: grid; grid-template-columns: minmax(0, 1fr) clamp(200px, 17vw, 274px); gap: 10px; align-items: start; }
.guide-list, .guide-sidebar { min-width: 0; }
.guide-sidebar { display: grid; gap: 9px; }
.guide-tabs { display: flex; gap: clamp(12px, 1.7vw, 29px); padding: 0 10px; border-bottom: 1px solid var(--admin-line); overflow-x: auto; }
.guide-tabs button { border: 0; background: transparent; color: inherit; border-bottom: 3px solid transparent; padding: 10px 6px 9px; font-weight: 700; white-space: nowrap; cursor: pointer; }
.guide-tabs button span { color: var(--admin-muted); font-weight: 500; }
.guide-tabs button.is-active { color: var(--admin-accent); border-bottom-color: var(--admin-accent); }
.guide-tabs button.is-active span { color: inherit; }
.guide-toolbar { padding: 16px 12px 0; gap: 10px; }
.guide-search { position: relative; flex: 1 1 330px; max-width: 540px; min-width: 160px; }
.guide-search > .anticon { position: absolute; inset-inline-start: 12px; top: 50%; transform: translateY(-50%); color: var(--admin-muted); pointer-events: none; }
.guide-search .admin-input { padding-inline-start: 34px; width: 100%; }
.guide-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.guide-actions .admin-button { white-space: nowrap; }
.guide-filters { padding: 12px 14px 16px; justify-content: space-between; gap: 8px 12px; }
.guide-filters label { display: flex; gap: 7px; align-items: center; white-space: nowrap; font-weight: 650; font-size: 0.88rem; }
.guide-filters .admin-select { min-width: 104px; max-width: 160px; }
.guide-game-filter { width: 118px; }
.guide-extra-filters { padding-top: 0; }
.guide-menu { position: relative; }
.guide-menu summary { list-style: none; }
.guide-menu summary::-webkit-details-marker { display: none; }
.guide-menu-items { position: absolute; z-index: 5; inset-inline-end: 0; margin-top: 5px; }
.guide-table { width: 100%; min-width: 904px; font-size: 0.83rem; }
.guide-table th, .guide-table td { padding-inline: clamp(4px, 0.35vw, 8px); }
.guide-table th { white-space: nowrap; }
.guide-table input { accent-color: var(--gw-accent); }
.guide-title-cell { max-width: clamp(135px, 10.8vw, 218px); }
.guide-title-text { display: block; max-width: 100%; padding: 0; border: 0; background: none; color: inherit; text-align: start; cursor: pointer; }
.guide-title-text:hover { color: var(--admin-accent); }
.guide-title-text, .guide-description { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.guide-description { display: block; margin-top: 2px; }
.guide-featured { color: var(--gw-warning); }
.guide-cover { width: clamp(45px, 5vw, 84px); height: 42px; border-radius: 7px; object-fit: cover; }
.guide-placeholder { display: grid; place-items: center; background: var(--gw-accent-tint); color: var(--gw-accent); }
.guide-game-cell { max-width: 112px; overflow-wrap: anywhere; }
.guide-tags-cell { max-width: 150px; }
.guide-row-tags { display: flex; gap: 3px; flex-wrap: wrap; }
.guide-row-tags .admin-tag { padding-inline: 5px; }
.guide-author { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.guide-author img { width: 25px; height: 25px; border-radius: 50%; object-fit: cover; }
.guide-nowrap, .guide-status { white-space: nowrap; }
.guide-time { white-space: nowrap; font-variant-numeric: tabular-nums; line-height: 1.35; }
.guide-status, .guide-row-actions { display: flex; align-items: center; gap: 5px; }
.guide-status .admin-switch { flex: none; width: 35px; }
.guide-status .admin-switch.is-on::after { transform: translateX(15px); }
.guide-table-actions { text-align: center; }
.guide-row-actions { justify-content: center; gap: 3px; }
.guide-row-actions .admin-icon-button { width: 21px; min-width: 21px; height: 26px; font-size: 15px; }
.guide-row-actions .admin-icon-button:not(.is-danger) { color: var(--admin-accent); }
.guide-taxonomy { padding: 13px 15px 14px; }
.guide-panel-heading { display: flex; justify-content: space-between; align-items: center; gap: 5px; padding-bottom: 9px; border-bottom: 1px solid var(--admin-line); }
.guide-panel-heading h2 { margin: 0; font-size: 1.05rem; }
.guide-text-button { padding: 2px; border: 0; background: none; color: var(--admin-accent); font-weight: 650; cursor: pointer; white-space: nowrap; }
.guide-text-button:hover { text-decoration: underline; text-underline-offset: 3px; }
.guide-text-button:disabled, .guide-row-actions button:disabled, .guide-status button:disabled { opacity: .55; cursor: not-allowed; }
.guide-category-list { display: grid; gap: 1px; margin: 7px 0 0; padding: 0; list-style: none; }
.guide-category-list button { display: flex; align-items: center; gap: 7px; width: 100%; border: 0; border-radius: 6px; padding: 5px 3px; background: none; color: inherit; cursor: pointer; text-align: start; }
.guide-category-list button:hover, .guide-category-list button[aria-pressed='true'] { background: var(--gw-surface-hover); }
.guide-category-chevron { font-size: 10px; color: var(--admin-muted); }
.guide-category-icon { font-size: 15px; color: var(--admin-accent); }
.guide-count { color: var(--admin-muted); }
.guide-hot-tags { display: flex; flex-wrap: wrap; gap: 6px 8px; padding-top: 10px; }
.guide-hot-tags .admin-tag { border-radius: 999px; padding: 3px 10px; }
.guide-hot-tags button { cursor: pointer; }
.guide-hot-tags button.admin-tag { border: 1px solid transparent; }
.guide-hot-tags button[aria-pressed='true'] { box-shadow: inset 0 0 0 1.5px currentColor; }
.guide-feedback { margin: 0 12px 10px; padding: 7px 10px; border-radius: 6px; background: var(--gw-success-tint); color: var(--gw-success); }
.guide-feedback.is-error { color: var(--gw-danger); background: var(--gw-danger-tint); }
.guide-detail { display: grid; gap: 10px; overflow-wrap: anywhere; }
.guide-detail h3, .guide-detail p { margin: 0; }
.guide-detail-cover { width: min(100%, 340px); height: 155px; border-radius: 9px; object-fit: cover; }
.guide-detail dl { display: grid; grid-template-columns: max-content 1fr; gap: 7px 16px; margin: 0; }
.guide-detail dt { font-weight: 650; }
.guide-detail dd { margin: 0; }
.guide-dialog-form { margin-top: 10px; }
@media (max-width: 1350px) { .guide-content { grid-template-columns: minmax(0, 1fr) 200px; } }
@media (max-width: 1150px) {
  .guide-content { grid-template-columns: minmax(0, 1fr); }
  .guide-sidebar { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
