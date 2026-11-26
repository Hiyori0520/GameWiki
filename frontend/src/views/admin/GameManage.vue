<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useTemplateRef, watch, type Component } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import {
  AndroidFilled,
  AppleFilled,
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ControlOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  PictureOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  RollbackOutlined,
  SearchOutlined,
  StarFilled,
  UnorderedListOutlined,
  UploadOutlined,
  WindowsFilled,
} from '@ant-design/icons-vue'
import AdminDialog from '../../components/admin/AdminDialog.vue'
import AdminGameIcon from '../../components/admin/AdminGameIcon.vue'
import AdminPager from '../../components/admin/AdminPager.vue'
import AdminStats from '../../components/admin/AdminStats.vue'
import { downloadAdminJson } from '../../admin/download'
import { adminPreferences } from '../../admin/preferences'
import { adminRequest, adminErrorMessage, hasAdminPermission, isAdminDemo } from '../../api/admin/client'
import {
  batchGames, canTransitionGame, createGame, deleteGame, demoExportGames, gameStatuses, getGame,
  getGameOptions, getGameStats, getGameTransfer, listGames, startGameExport, startGameImport,
  transitionGame, updateGame, uploadGameImage,
  type GameBatchCommand, type GameBatchResult, type GameCounts, type GameCreateRequest, type GameOptions, type GameQuery,
  type GameRecord, type GameSortField, type GameStats, type GameStatus, type GameTransferTask, type GameTransition,
} from '../../api/admin/game'

type GameForm = Omit<GameCreateRequest, 'tags' | 'aliases'> & {
  id?: number
  version?: number
  aliasesText: string
  tagsText: string
  submitReview: boolean
}
type BatchAction = GameBatchCommand['action']
type GameDialog = { kind: 'form' }
  | { kind: 'details'; game: GameRecord }
  | { kind: 'import' }
  | { kind: 'loading' }
  | { kind: 'delete'; ids: number[]; isBatch: boolean }
  | { kind: 'status'; ids: number[]; action: GameTransition; isBatch: boolean }
  | { kind: 'batch'; ids: number[]; action: 'add-tags' | 'set-categories' }

const route = useRoute()
const router = useRouter()
const number = new Intl.NumberFormat('zh-CN')
const keyword = ref('')
const appliedKeyword = ref('')
const category = ref('')
const platform = ref('')
const company = ref('')
const status = ref<GameStatus | ''>('')
const searchInput = useTemplateRef<HTMLInputElement>('game-search')
const categorySelect = useTemplateRef<HTMLSelectElement>('game-category')
const platformSelect = useTemplateRef<HTMLSelectElement>('game-platform')
const page = ref(1)
const size = ref<number>(adminPreferences.value.pageSize)
const sortBy = ref<GameSortField>()
const sortOrder = ref<'asc' | 'desc'>('asc')
const selected = ref<number[]>([])
const menu = ref<'batch' | 'transfer' | null>(null)
const dialog = ref<GameDialog | null>(null)
const form = ref<GameForm>(emptyForm())
const cleanForm = ref('')
const pendingCover = ref<File>()
const pendingBackground = ref<File>()
const pendingDuplicate = ref<number>()
const batchTags = ref('')
const batchCategories = ref<string[]>([])
const dialogError = ref('')
const notice = ref<{ text: string; error?: boolean } | null>(null)
const importFile = ref<File>()
const importTask = ref<GameTransferTask>()
const exportTask = ref<GameTransferTask>()
const isSaving = ref(false)
const isImporting = ref(false)
const isExporting = ref(false)
const exportPollFailed = ref(false)
const importPollFailed = ref('')
const brokenCovers = ref(new Set<string>())
const records = ref<GameRecord[]>([])
const total = ref(0)
const options = ref<GameOptions>()
const gameStats = ref<GameStats>()
const listLoading = ref(false)
const listError = ref('')
const summaryError = ref('')
const reloadKey = ref(0)
const summaryKey = ref(0)
let importsController: AbortController | undefined
let exportsController: AbortController | undefined
let disposed = false

const canWrite = computed(() => hasAdminPermission('games:write'))
const canReview = computed(() => hasAdminPermission('games:review'))
const canDelete = computed(() => hasAdminPermission('games:delete'))
const canImport = computed(() => hasAdminPermission('games:import'))
const canExport = computed(() => hasAdminPermission('games:export'))
const canBatch = computed(() => hasAdminPermission('games:batch'))
const isDirty = computed(() => dialog.value?.kind === 'form' && (
  Boolean(pendingCover.value || pendingBackground.value) || JSON.stringify(form.value) !== cleanForm.value
))
const platformIcons: Record<string, { icon: Component; color: string }> = {
  Windows: { icon: WindowsFilled, color: '#0869d9' },
  'Nintendo Switch': { icon: ControlOutlined, color: '#df1632' },
  PlayStation: { icon: PlayCircleOutlined, color: '#163eb5' },
  macOS: { icon: AppleFilled, color: '#505271' },
  Android: { icon: AndroidFilled, color: '#278647' },
  iOS: { icon: AppleFilled, color: '#505271' },
}
const stats = computed(() => [
  {
    label: '游戏总数', value: statValue('total'), tone: 'violet' as const, icon: AdminGameIcon,
    change: gameStats.value?.recent ? `↑ +${number.format(gameStats.value.recent.total)}` : '',
    note: gameStats.value?.recent ? '（近7天新增）' : '',
  },
  { label: '已发布游戏', value: statValue('published'), tone: 'blue' as const, icon: CheckCircleOutlined, note: recentStatusLabel('published') },
  { label: '待审核', value: statValue('pending'), tone: 'orange' as const, icon: ClockCircleOutlined, note: recentStatusLabel('pending') },
  { label: '已下架', value: statValue('offShelf'), tone: 'pink' as const, icon: EyeInvisibleOutlined, note: recentStatusLabel('offShelf') },
])
const queryFilters = computed<Omit<GameQuery, 'page' | 'pageSize'>>(() => ({
  keyword: appliedKeyword.value || undefined,
  category: category.value || undefined,
  platform: platform.value || undefined,
  company: company.value || undefined,
  status: status.value || undefined,
  sortBy: sortBy.value,
  sortOrder: sortBy.value ? sortOrder.value : undefined,
}))
const hasFilters = computed(() => Boolean(keyword.value || category.value || platform.value || company.value || status.value))
const allSelected = computed(() => records.value.length > 0 && records.value.every((game) => selected.value.includes(game.id)))
const categoriesForForm = computed(() => [...new Set([...(options.value?.categories ?? []), ...form.value.categories])])
const platformsForForm = computed(() => [...new Set([...(options.value?.platforms ?? []), ...form.value.platforms])])
const routeDialogs = ['admin-game-new', 'admin-game-edit', 'admin-game-detail', 'admin-game-import']
const transitionLabels: Record<GameTransition, string> = { 'submit-review': '提交审核', publish: '发布', 'off-shelf': '下架', reject: '退回草稿' }
const taskLabels: Record<GameTransferTask['status'], string> = { QUEUED: '排队中', RUNNING: '处理中', COMPLETED: '已完成', FAILED: '失败' }
const dialogTitle = computed(() => {
  switch (dialog.value?.kind) {
    case 'details': return dialog.value.game.name
    case 'form': return form.value.id ? '编辑游戏' : '添加游戏'
    case 'import': return '导入游戏'
    case 'loading': return '游戏资料'
    case 'delete': return '确认删除游戏'
    case 'status': return `确认${transitionLabels[dialog.value.action]}`
    case 'batch': return dialog.value.action === 'add-tags' ? '批量添加标签' : '批量修改分类'
    default: return ''
  }
})

watch(keyword, (value, _previous, onCleanup) => {
  const timer = window.setTimeout(() => { appliedKeyword.value = value.trim() }, 300)
  onCleanup(() => window.clearTimeout(timer))
})
watch([queryFilters, size], () => { page.value = 1 }, { flush: 'sync' })
watch([page, size, queryFilters, reloadKey], async (_values, _previous, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  records.value = []
  selected.value = []
  listLoading.value = true
  listError.value = ''
  try {
    const result = await listGames({ ...queryFilters.value, page: page.value, pageSize: size.value }, controller.signal)
    if (controller.signal.aborted) return
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > lastPage) {
      page.value = lastPage
      return
    }
    records.value = result.records
  } catch (error) {
    if (!controller.signal.aborted) {
      listError.value = adminErrorMessage(error)
      total.value = 0
    }
  } finally {
    if (!controller.signal.aborted) listLoading.value = false
  }
}, { immediate: true })
watch(summaryKey, async (_key, _previous, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  summaryError.value = ''
  const [statistics, filters] = await Promise.allSettled([getGameStats(controller.signal), getGameOptions(controller.signal)])
  if (controller.signal.aborted) return
  gameStats.value = statistics.status === 'fulfilled' ? statistics.value : undefined
  options.value = filters.status === 'fulfilled' ? filters.value : undefined
  for (const result of [statistics, filters]) {
    if (result.status === 'rejected') {
      summaryError.value = adminErrorMessage(result.reason)
      break
    }
  }
}, { immediate: true })
watch(() => route.query.q, (query) => {
  keyword.value = typeof query === 'string' ? query : ''
  appliedKeyword.value = keyword.value.trim()
}, { immediate: true })
watch(() => route.query.action, (action) => {
  if (route.name !== 'admin-games') return
  const query = { ...route.query }
  delete query.action
  if (action === 'new' && canWrite.value) void router.replace({ name: 'admin-game-new', query })
  else if (action === 'import' && canImport.value) void router.replace({ name: 'admin-game-import', query })
  else if (action === 'export' && canExport.value) {
    void exportGames()
    void router.replace({ query })
  }
}, { immediate: true })
watch(() => route.query.focus, async (focus) => {
  if (route.name !== 'admin-games') return
  await nextTick()
  if (focus === 'category') categorySelect.value?.focus()
  else if (focus === 'tag') searchInput.value?.focus()
  else if (focus === 'platform') platformSelect.value?.focus()
}, { immediate: true })
watch([() => route.name, () => route.params.id], async ([name, rawId], _previous, onCleanup) => {
  if (name === 'admin-games') {
    closeLocalDialog()
    return
  }
  if (name === 'admin-game-new' && canWrite.value) {
    const duplicateId = pendingDuplicate.value
    pendingDuplicate.value = undefined
    if (duplicateId === undefined) {
      openForm()
      return
    }
    await loadGameDialog(duplicateId, 'copy', onCleanup)
  } else if (name === 'admin-game-import' && canImport.value) {
    importFile.value = undefined
    showDialog({ kind: 'import' })
  } else if (name === 'admin-game-detail' || (name === 'admin-game-edit' && canWrite.value)) {
    await loadGameDialog(Number(rawId), name === 'admin-game-edit' ? 'edit' : 'detail', onCleanup)
  }
}, { immediate: true })

onBeforeRouteLeave(confirmExit)
onBeforeRouteUpdate((to, from) => to.path === from.path || confirmExit())
onMounted(() => {
  document.addEventListener('click', handleOutsideMenu)
  window.addEventListener('beforeunload', handleBeforeUnload)
})
onUnmounted(() => {
  disposed = true
  document.removeEventListener('click', handleOutsideMenu)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  importsController?.abort()
  exportsController?.abort()
})

function statValue(key: keyof GameCounts) {
  const count = gameStats.value?.[key]
  return typeof count === 'number' && Number.isFinite(count) ? number.format(count) : '—'
}

function recentStatusLabel(key: keyof GameCounts) {
  const count = gameStats.value?.recent?.[key]
  return typeof count === 'number' && Number.isFinite(count) ? `近7天新增 · ${number.format(count)} 条` : ''
}

function handleOutsideMenu(event: MouseEvent) {
  if (event.target instanceof Element && !event.target.closest('.game-menu-wrap')) menu.value = null
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

function confirmExit() {
  return !isSaving.value && (!isDirty.value || window.confirm('游戏资料尚未保存，确定离开？'))
}

function clearFilters(updateRoute = true) {
  keyword.value = ''
  appliedKeyword.value = ''
  category.value = ''
  platform.value = ''
  company.value = ''
  status.value = ''
  if (updateRoute && route.query.q !== undefined) {
    const query = { ...route.query }
    delete query.q
    void router.replace({ query })
  }
}

function sort(field: GameSortField) {
  sortOrder.value = sortBy.value === field && sortOrder.value === 'asc' ? 'desc' : 'asc'
  sortBy.value = field
}

function ariaSort(field: GameSortField) {
  return sortBy.value === field ? (sortOrder.value === 'asc' ? 'ascending' : 'descending') : 'none'
}

function selectPage(checked: boolean) {
  selected.value = checked ? records.value.map((game) => game.id) : []
}

function goToGame(name: string, id?: number, keepSearch = true) {
  menu.value = null
  return router.push({ name, params: id === undefined ? {} : { id }, query: keepSearch && route.query.q ? { q: route.query.q } : {} })
}

async function loadGameDialog(id: number, action: 'detail' | 'edit' | 'copy', onCleanup: (cleanup: () => void) => void) {
  showDialog({ kind: 'loading' })
  if (!Number.isSafeInteger(id) || id <= 0) {
    dialogError.value = '游戏编号无效。'
    return
  }
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  try {
    const game = await getGame(id, controller.signal)
    if (controller.signal.aborted) return
    if (action === 'detail') showDialog({ kind: 'details', game })
    else openForm(game, action === 'copy')
  } catch (error) {
    if (!controller.signal.aborted) dialogError.value = adminErrorMessage(error)
  }
}

function emptyForm(): GameForm {
  return {
    name: '', originalName: '', englishName: '', aliasesText: '', company: '', developer: '', cover: '',
    background: '', description: '', categories: [], tagsText: '', platforms: [], releaseDate: '', submitReview: false,
  }
}

function openForm(game?: GameRecord, duplicate = false) {
  pendingCover.value = undefined
  pendingBackground.value = undefined
  if (game) {
    const { id, name, aliases, categories, tags, platforms, createdAt: _createdAt, rating: _rating, votes: _votes, status: _status, ...fields } = game
    form.value = {
      ...fields,
      id: duplicate ? undefined : id,
      version: duplicate ? undefined : game.version,
      name: duplicate ? `${name.slice(0, 95)}（副本）` : name,
      categories: [...categories], tagsText: tags.join('、'), aliasesText: aliases.join('、'),
      platforms: [...platforms], submitReview: false,
    }
  } else form.value = emptyForm()
  cleanForm.value = JSON.stringify(form.value)
  showDialog({ kind: 'form' })
}

function showDialog(value: GameDialog) {
  dialogError.value = ''
  notice.value = null
  menu.value = null
  dialog.value = value
}

function closeLocalDialog() {
  dialog.value = null
  pendingCover.value = undefined
  pendingBackground.value = undefined
}

async function closeDialog() {
  if (isSaving.value || isImporting.value) return
  if (routeDialogs.includes(String(route.name))) await goToGame('admin-games')
  else if (confirmExit()) closeLocalDialog()
}

function values(text: string) {
  return [...new Set(text.split(/[,，、]/).map((value) => value.trim()).filter(Boolean))]
}

function validImage(value: string) {
  if (!value) return true
  if (/^\/(?!\/)[^\s\\]*$/i.test(value)) return true
  if (isAdminDemo && /^data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/i.test(value)) return true
  try { return new URL(value).protocol === 'https:' } catch { return false }
}

function validDate(value: string) {
  if (!value) return true
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(value)
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
}

function validateTags(tags: string[]) {
  if (tags.length > 8 || tags.some((tag) => tag.length > 16)) throw new Error('最多填写 8 个标签，每个标签最多 16 个字符。')
}

function pickImage(event: Event, field: 'cover' | 'background') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const pending = field === 'cover' ? pendingCover : pendingBackground
  pending.value = undefined
  dialogError.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size === 0 || file.size > 1024 * 1024) {
    dialogError.value = '请选择不超过 1 MB 的 JPG、PNG 或 WebP 图片。'
    input.value = ''
    return
  }
  pending.value = file
}

async function saveForm() {
  if (isSaving.value || !canWrite.value) return
  isSaving.value = true
  dialogError.value = ''
  const { id, version, submitReview, tagsText, aliasesText, ...fields } = form.value
  let saved: GameRecord | undefined
  try {
    const draft: GameCreateRequest = {
      ...fields,
      name: fields.name.trim(), originalName: fields.originalName.trim(), englishName: fields.englishName.trim(),
      company: fields.company.trim(), developer: fields.developer.trim(), cover: fields.cover.trim(),
      background: fields.background.trim(), description: fields.description.trim(), tags: values(tagsText), aliases: values(aliasesText),
    }
    if (!draft.name || draft.name.length > 100) throw new Error('游戏名称需为 1–100 个字符。')
    if (!draft.categories.length) throw new Error('至少选择一个游戏分类。')
    if (!draft.platforms.length) throw new Error('至少选择一个游戏平台。')
    if (!validDate(draft.releaseDate)) throw new Error('请输入有效的发行日期。')
    if (!validImage(draft.cover) || !validImage(draft.background)) throw new Error('请输入有效的 HTTPS 图片地址或上传图片。')
    validateTags(draft.tags)
    if (draft.aliases.length > 10 || draft.aliases.some((alias) => alias.length > 100)) throw new Error('最多填写 10 个别名，每个别名最多 100 个字符。')
    if (pendingCover.value) {
      draft.cover = await uploadGameImage(pendingCover.value)
      if (!draft.cover || !validImage(draft.cover)) throw new Error('接口未返回有效的封面地址。')
      form.value.cover = draft.cover
      pendingCover.value = undefined
    }
    if (pendingBackground.value) {
      draft.background = await uploadGameImage(pendingBackground.value)
      if (!draft.background || !validImage(draft.background)) throw new Error('接口未返回有效的背景图地址。')
      form.value.background = draft.background
      pendingBackground.value = undefined
    }
    saved = id === undefined ? await createGame(draft) : await updateGame(id, { ...draft, version })
    if (id === undefined && submitReview && canReview.value) await transitionGame(saved.id, 'submit-review')
    cleanForm.value = JSON.stringify(form.value)
    if (id === undefined) {
      clearFilters(false)
      page.value = 1
    }
    isSaving.value = false
    await goToGame('admin-games', undefined, id !== undefined)
    refresh()
    notice.value = { text: withMode(id === undefined ? '已添加游戏。' : '已保存游戏资料。') }
  } catch (error) {
    dialogError.value = adminErrorMessage(error)
    if (saved) {
      form.value.id = saved.id
      form.value.version = saved.version
      form.value.submitReview = false
      cleanForm.value = JSON.stringify(form.value)
      dialogError.value = `游戏已保存。提交审核失败：${dialogError.value}`
      refresh()
    }
  } finally {
    isSaving.value = false
  }
}

function duplicateGame(game: GameRecord) {
  pendingDuplicate.value = game.id
  void goToGame('admin-game-new')
}

function gameNames(ids: number[]) {
  return ids.map((id) => records.value.find((game) => game.id === id)?.name ?? `#${id}`).join('、')
}

function deleteGames(ids: number[], isBatch = false) {
  if (ids.length && canDelete.value && (!isBatch || canBatch.value)) showDialog({ kind: 'delete', ids: [...ids], isBatch })
}

function changeStatus(ids: number[], action: GameTransition, isBatch = false) {
  if (ids.length && canReview.value && (!isBatch || canBatch.value)) showDialog({ kind: 'status', ids: [...ids], action, isBatch })
}

function statusAction(status: GameStatus): GameTransition {
  if (status === 'DRAFT') return 'submit-review'
  return status === 'PUBLISHED' ? 'off-shelf' : 'publish'
}

function editBatch(action: 'add-tags' | 'set-categories') {
  if (!selected.value.length || !canBatch.value || !canWrite.value) return
  batchTags.value = ''
  batchCategories.value = []
  showDialog({ kind: 'batch', ids: [...selected.value], action })
}

async function applyChange() {
  const action = dialog.value
  if (!action || !['delete', 'status', 'batch'].includes(action.kind) || isSaving.value) return
  isSaving.value = true
  dialogError.value = ''
  let result: GameBatchResult | undefined
  let label = ''
  try {
    if (action.kind === 'delete' && canDelete.value) {
      if (action.isBatch && canBatch.value) result = await batchGames(action.ids, { action: 'delete' })
      else if (!action.isBatch && action.ids[0] !== undefined) await deleteGame(action.ids[0])
      else return
      label = '已删除游戏。'
    } else if (action.kind === 'status' && canReview.value) {
      const command: GameTransition = action.action
      if (action.isBatch && canBatch.value && (command === 'publish' || command === 'off-shelf')) result = await batchGames(action.ids, { action: command })
      else if (!action.isBatch && action.ids[0] !== undefined) await transitionGame(action.ids[0], command)
      else return
      label = `已${transitionLabels[command]}游戏。`
    } else if (action.kind === 'batch' && canBatch.value && canWrite.value) {
      const command: BatchAction = action.action
      const selectedValues = command === 'add-tags' ? values(batchTags.value) : batchCategories.value
      if (!selectedValues.length) throw new Error(command === 'add-tags' ? '请填写标签。' : '至少选择一个游戏分类。')
      if (command === 'add-tags') validateTags(selectedValues)
      result = await batchGames(action.ids, { action: command, values: selectedValues })
    } else return
    if (result) {
      const summary = `成功 ${result.succeeded} 条，失败 ${result.failed} 条。`
      const reasons = result.errors.map(({ id, reason }) => `${gameNames([id])}：${reason}`).join('；')
      notice.value = { text: withMode([summary, reasons].filter(Boolean).join(' ')), error: result.failed > 0 }
    } else notice.value = { text: withMode(label) }
    closeLocalDialog()
    refresh()
  } catch (error) {
    dialogError.value = adminErrorMessage(error)
  } finally {
    isSaving.value = false
  }
}

function withMode(message: string) {
  return isAdminDemo ? `${message}（开发演示）` : message
}

function refresh() {
  reloadKey.value++
  summaryKey.value++
}

function pickImport(event: Event) {
  importFile.value = (event.target as HTMLInputElement).files?.[0]
}

function taskLink(value?: string) {
  if (!value) return undefined
  try {
    const url = new URL(value, window.location.origin)
    if (url.protocol === 'https:' || (url.protocol === 'http:' && url.origin === window.location.origin)) return url.href
  } catch {
    return undefined
  }
}

async function downloadTransfer(url: string | undefined, filename: string) {
  if (!url?.startsWith('/api/admin/v1/')) return
  try {
    const payload = await adminRequest<unknown>({ url: url.slice('/api/admin/v1'.length) })
    downloadAdminJson(filename, payload)
  } catch (error) {
    notice.value = { text: adminErrorMessage(error), error: true }
  }
}

function schedulePoll(kind: 'imports' | 'exports', task: GameTransferTask, controller: AbortController) {
  const destination = kind === 'imports' ? importTask : exportTask
  destination.value = task
  if (controller.signal.aborted || ['COMPLETED', 'FAILED'].includes(task.status)) {
    if (kind === 'imports' && task.status === 'COMPLETED') refresh()
    return
  }
  const timer = window.setTimeout(async () => {
    controller.signal.removeEventListener('abort', clearPoll)
    try {
      const result = await getGameTransfer(kind, task.id, controller.signal)
      if (!controller.signal.aborted) schedulePoll(kind, result, controller)
    } catch (error) {
      if (!controller.signal.aborted) {
        const message = `无法查询任务 ${task.id}。${adminErrorMessage(error)}`
        if (kind === 'imports') importPollFailed.value = message
        else {
          exportPollFailed.value = true
          notice.value = { text: message, error: true }
        }
      }
    }
  }, 1500)
  const clearPoll = () => window.clearTimeout(timer)
  controller.signal.addEventListener('abort', clearPoll, { once: true })
}

async function importGames() {
  if (!importFile.value || isImporting.value || !canImport.value || isAdminDemo) return
  dialogError.value = ''
  isImporting.value = true
  importPollFailed.value = ''
  try {
    if (!/\.(csv|xlsx)$/i.test(importFile.value.name) || importFile.value.size === 0 || importFile.value.size > 10 * 1024 * 1024) {
      throw new Error('请选择非空、大小不超过 10 MB 的 CSV 或 XLSX 文件。')
    }
    const task = await startGameImport(importFile.value)
    if (disposed) return
    importsController?.abort()
    importsController = new AbortController()
    schedulePoll('imports', task, importsController)
  } catch (error) {
    dialogError.value = adminErrorMessage(error)
  } finally {
    isImporting.value = false
  }
}

async function exportGames() {
  if (!canExport.value || isExporting.value || (exportTask.value && ['QUEUED', 'RUNNING'].includes(exportTask.value.status))) return
  menu.value = null
  isExporting.value = true
  exportPollFailed.value = false
  try {
    const filters = { ...queryFilters.value, keyword: keyword.value.trim() || undefined }
    if (isAdminDemo) {
      const items = demoExportGames(selected.value, filters)
      if (!items.length) throw new Error('当前没有可导出的游戏。')
      downloadAdminJson('gamewiki-games-demo.json', items)
      return
    }
    const task = await startGameExport(selected.value, filters)
    if (disposed) return
    exportsController?.abort()
    exportsController = new AbortController()
    schedulePoll('exports', task, exportsController)
  } catch (error) {
    notice.value = { text: adminErrorMessage(error), error: true }
  } finally {
    isExporting.value = false
  }
}

function retryTransfer(kind: 'imports' | 'exports', task: GameTransferTask) {
  if (kind === 'imports') {
    importsController?.abort()
    importsController = new AbortController()
    importPollFailed.value = ''
    schedulePoll(kind, task, importsController)
  } else {
    exportsController?.abort()
    exportsController = new AbortController()
    notice.value = null
    exportPollFailed.value = false
    schedulePoll(kind, task, exportsController)
  }
}

function visibleGameLabels(game: GameRecord) {
  return [...new Set([...game.categories, ...game.tags])]
}

function markCoverBroken(cover: string) {
  brokenCovers.value.add(cover)
}
</script>

<template>
  <div class="game-management">
    <AdminStats :items="stats" />
    <p v-if="summaryError" class="admin-form-error" role="alert">{{ summaryError }} <button type="button" class="admin-button" @click="summaryKey++">重试</button></p>

    <section class="admin-panel game-list" aria-label="游戏列表" @keydown.esc="menu = null">
      <div class="admin-toolbar game-toolbar">
        <label class="game-search">
          <SearchOutlined aria-hidden="true" />
          <input ref="game-search" v-model="keyword" class="admin-input" type="search" aria-label="搜索游戏" placeholder="搜索游戏名称、别名、标签或公司…" />
        </label>
        <div class="game-actions">
          <button v-if="canWrite" type="button" class="admin-button is-primary" @click="goToGame('admin-game-new')"><PlusOutlined aria-hidden="true" /> 添加游戏</button>
          <div v-if="canBatch && (canReview || canDelete || canWrite)" class="game-menu-wrap">
            <button type="button" class="admin-button" :aria-expanded="menu === 'batch'" @click="menu = menu === 'batch' ? null : 'batch'">
              <UnorderedListOutlined aria-hidden="true" /> 批量操作 <DownOutlined aria-hidden="true" />
              <span v-if="selected.length" class="game-selection">{{ selected.length }}</span>
            </button>
            <div v-if="menu === 'batch'" class="admin-menu">
              <p v-if="!selected.length" class="game-menu-hint">请先勾选游戏</p>
              <button v-if="canReview" type="button" :disabled="!selected.length" @click="changeStatus(selected, 'publish', true)">批量发布</button>
              <button v-if="canReview" type="button" :disabled="!selected.length" @click="changeStatus(selected, 'off-shelf', true)">批量下架</button>
              <button v-if="canWrite" type="button" :disabled="!selected.length" @click="editBatch('add-tags')">批量添加标签</button>
              <button v-if="canWrite" type="button" :disabled="!selected.length || !options" @click="editBatch('set-categories')">批量修改分类</button>
              <button v-if="canDelete" type="button" :disabled="!selected.length" @click="deleteGames(selected, true)">批量删除</button>
            </div>
          </div>
          <div v-if="canImport || canExport" class="game-menu-wrap">
            <button type="button" class="admin-button" :aria-expanded="menu === 'transfer'" @click="menu = menu === 'transfer' ? null : 'transfer'">
              <UploadOutlined aria-hidden="true" /> 导入/导出 <DownOutlined aria-hidden="true" />
            </button>
            <div v-if="menu === 'transfer'" class="admin-menu">
              <button v-if="canImport" type="button" @click="goToGame('admin-game-import')"><UploadOutlined aria-hidden="true" /> 导入游戏</button>
              <button v-if="canExport" type="button" :disabled="isExporting || exportTask?.status === 'QUEUED' || exportTask?.status === 'RUNNING'" @click="exportGames()"><DownloadOutlined aria-hidden="true" /> {{ selected.length ? '导出选中记录' : '导出筛选结果' }}</button>
            </div>
          </div>
        </div>
      </div>

      <div class="admin-filters game-filters">
        <label>游戏分类
          <select ref="game-category" v-model="category" class="admin-select" aria-label="游戏分类">
            <option value="">全部分类</option>
            <option v-for="item in options?.categories" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>游戏平台
          <select ref="game-platform" v-model="platform" class="admin-select" aria-label="游戏平台">
            <option value="">全部平台</option>
            <option v-for="item in options?.platforms" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>制作/发行公司
          <select v-model="company" class="admin-select" aria-label="制作/发行公司">
            <option value="">全部公司</option>
            <option v-for="item in options?.companies" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>状态
          <select v-model="status" class="admin-select" aria-label="状态">
            <option value="">全部状态</option>
            <option v-for="(label, value) in gameStatuses" :key="value" :value="value">{{ label }}</option>
          </select>
        </label>
        <button v-if="hasFilters" type="button" class="admin-button game-reset" @click="clearFilters()">重置</button>
      </div>

      <p v-if="notice" class="game-notice" :class="{ 'admin-form-error': notice.error }" :role="notice.error ? 'alert' : 'status'">
        {{ notice.text }}
        <button type="button" class="admin-icon-button" aria-label="关闭提示" @click="notice = null">×</button>
      </p>
      <div v-if="canExport && exportTask" class="game-notice game-task" role="status">
        <span>导出任务 {{ exportTask.id }} · {{ taskLabels[exportTask.status] }}</span>
        <progress v-if="exportTask.progress !== undefined" :value="exportTask.progress" max="100" aria-label="导出进度" />
        <button v-if="exportTask.status === 'COMPLETED' && taskLink(exportTask.downloadUrl)" class="admin-button" type="button" @click="downloadTransfer(exportTask.downloadUrl, 'gamewiki-games.json')">下载导出结果</button>
        <span v-if="exportTask.error">{{ exportTask.error }}</span>
        <button v-if="exportPollFailed" type="button" class="admin-button" @click="retryTransfer('exports', exportTask)">重新查询</button>
      </div>

      <div class="admin-table-scroll" tabindex="0" aria-label="游戏记录表格" :aria-busy="listLoading">
        <table class="admin-table game-table">
          <thead>
            <tr>
              <th scope="col" class="game-check"><input :checked="allSelected" :indeterminate="selected.length > 0 && !allSelected" :disabled="!records.length || !(canExport || canBatch)" type="checkbox" aria-label="选择当前页所有游戏" @change="selectPage(($event.target as HTMLInputElement).checked)" /></th>
              <th scope="col">封面</th>
              <th scope="col" :aria-sort="ariaSort('name')"><button type="button" class="game-sort" @click="sort('name')">游戏名称<span v-if="sortBy === 'name'" aria-hidden="true">{{ sortOrder === 'asc' ? '↑' : '↓' }}</span></button></th>
              <th scope="col">分类</th>
              <th scope="col">制作/发行公司</th>
              <th scope="col" :aria-sort="ariaSort('releaseDate')"><button type="button" class="game-sort" @click="sort('releaseDate')">发行日期<span v-if="sortBy === 'releaseDate'" aria-hidden="true">{{ sortOrder === 'asc' ? '↑' : '↓' }}</span></button></th>
              <th scope="col">平台</th>
              <th scope="col" :aria-sort="ariaSort('rating')"><button type="button" class="game-sort" @click="sort('rating')">评分<span v-if="sortBy === 'rating'" aria-hidden="true">{{ sortOrder === 'asc' ? '↑' : '↓' }}</span></button></th>
              <th scope="col">状态</th>
              <th scope="col" class="game-operation-heading">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="game in records" :key="game.id">
              <td class="game-check"><input v-model="selected" :value="game.id" :disabled="!(canExport || canBatch)" type="checkbox" :aria-label="'选择 ' + game.name" /></td>
              <td>
                <img v-if="game.cover && validImage(game.cover) && !brokenCovers.has(game.cover)" class="admin-row-cover game-cover" :src="game.cover" alt="" loading="lazy" @error="markCoverBroken(game.cover)" />
                <span v-else class="admin-row-cover game-cover game-cover-empty" aria-label="无封面"><PictureOutlined /></span>
              </td>
              <th scope="row" class="game-name">
                <button type="button" class="admin-row-title game-name-link" @click="goToGame('admin-game-detail', game.id)">{{ game.name }}</button>
                <small v-if="game.originalName || game.englishName || game.aliases.length" class="admin-row-subtitle" :title="[game.originalName, game.englishName, ...game.aliases].filter(Boolean).join('、')">{{ game.originalName || game.englishName || game.aliases[0] }}</small>
              </th>
              <td>
                <div class="game-tags" :title="visibleGameLabels(game).join('、')">
                  <span v-for="(tag, index) in visibleGameLabels(game).slice(0, 3)" :key="tag" class="admin-tag" :class="{ 'is-pink': index === 0, 'is-blue': index === 1 }">{{ tag }}</span>
                  <span v-if="visibleGameLabels(game).length > 3" class="admin-row-subtitle">+{{ visibleGameLabels(game).length - 3 }}</span>
                  <span v-if="!visibleGameLabels(game).length">—</span>
                </div>
              </td>
              <td class="game-company" :title="[game.developer, game.company].filter(Boolean).join(' / ')">{{ game.company || game.developer || '—' }}</td>
              <td class="game-date"><CalendarOutlined v-if="game.releaseDate" /> {{ game.releaseDate || '—' }}</td>
              <td class="game-platforms">
                <template v-for="item in game.platforms.slice(0, 3)" :key="item">
                  <span v-if="platformIcons[item]" :title="item" :aria-label="item" role="img" :style="{ color: platformIcons[item]?.color }"><component :is="platformIcons[item]?.icon" /></span>
                  <small v-else :title="item">{{ item }}</small>
                </template>
                <small v-if="game.platforms.length > 3" :title="game.platforms.slice(3).join('、')">+{{ game.platforms.length - 3 }}</small>
                <span v-if="!game.platforms.length">—</span>
              </td>
              <td class="game-rating">
                <template v-if="game.rating !== null"><span><StarFilled /> {{ game.rating.toFixed(1) }}</span><small>（{{ number.format(game.votes) }}）</small></template>
                <span v-else>—</span>
              </td>
              <td>
                <button type="button" role="switch" :aria-checked="game.status === 'PUBLISHED'" :aria-label="`${transitionLabels[statusAction(game.status)]} ${game.name}`" :disabled="!canReview || !canTransitionGame(game.status, statusAction(game.status))" class="game-status" @click="changeStatus([game.id], statusAction(game.status))">
                  <span class="admin-switch" :class="{ 'is-on': game.status === 'PUBLISHED' }"></span>
                  <span>{{ gameStatuses[game.status] }}</span>
                </button>
              </td>
              <td>
                <div class="game-row-actions">
                  <button type="button" class="admin-icon-button" :aria-label="'查看 ' + game.name" title="查看" @click="goToGame('admin-game-detail', game.id)"><EyeOutlined /></button>
                  <button v-if="canWrite" type="button" class="admin-icon-button" :aria-label="'编辑 ' + game.name" title="编辑" @click="goToGame('admin-game-edit', game.id)"><EditOutlined /></button>
                  <button v-if="canWrite" type="button" class="admin-icon-button" :aria-label="'复制 ' + game.name" title="复制" @click="duplicateGame(game)"><CopyOutlined /></button>
                  <button v-if="canReview && canTransitionGame(game.status, 'reject')" type="button" class="admin-icon-button" :aria-label="'退回草稿 ' + game.name" title="退回草稿" @click="changeStatus([game.id], 'reject')"><RollbackOutlined /></button>
                  <button v-if="canDelete" type="button" class="admin-icon-button is-danger" :aria-label="'删除 ' + game.name" title="删除" @click="deleteGames([game.id])"><DeleteOutlined /></button>
                </div>
              </td>
            </tr>
            <tr v-if="listLoading"><td colspan="10" class="admin-empty" role="status">正在加载游戏…</td></tr>
            <tr v-else-if="listError"><td colspan="10" class="admin-empty"><p role="alert">{{ listError }}</p><button type="button" class="admin-button" @click="reloadKey++">重试</button></td></tr>
            <tr v-else-if="!records.length"><td colspan="10" class="admin-empty">没有符合条件的游戏</td></tr>
          </tbody>
        </table>
      </div>
      <AdminPager v-model:page="page" v-model:size="size" :total="total" />
    </section>

    <AdminDialog v-if="dialog" :title="dialogTitle" @close="closeDialog">
      <form v-if="dialog.kind === 'form'" id="game-edit-form" class="admin-modal-form game-form" @submit.prevent="saveForm">
        <label>游戏名称 <input v-model="form.name" class="admin-input" required maxlength="100" /></label>
        <label>原名 <input v-model="form.originalName" class="admin-input" maxlength="100" /></label>
        <label>英文名 <input v-model="form.englishName" class="admin-input" maxlength="100" /></label>
        <label>别名 <input v-model="form.aliasesText" class="admin-input" placeholder="使用逗号分隔" /></label>
        <label>制作公司 <input v-model="form.developer" class="admin-input" maxlength="100" /></label>
        <label>发行公司 <input v-model="form.company" class="admin-input" maxlength="100" /></label>
        <label>发行日期 <input v-model="form.releaseDate" class="admin-input" type="date" /></label>
        <label>标签 <input v-model="form.tagsText" class="admin-input" placeholder="使用逗号分隔" /></label>
        <fieldset class="game-platform-field game-form-wide">
          <legend>游戏分类</legend>
          <label v-for="item in categoriesForForm" :key="item"><input v-model="form.categories" type="checkbox" :value="item" /> {{ item }}</label>
          <small v-if="!categoriesForForm.length">分类选项暂无数据</small>
        </fieldset>
        <fieldset class="game-platform-field game-form-wide">
          <legend>游戏平台</legend>
          <label v-for="item in platformsForForm" :key="item"><input v-model="form.platforms" type="checkbox" :value="item" /> {{ item }}</label>
          <small v-if="!platformsForForm.length">平台选项暂无数据</small>
        </fieldset>
        <label>封面地址 <input v-model="form.cover" class="admin-input" type="text" placeholder="HTTPS 图片地址" /></label>
        <label>上传封面 <input class="admin-input" type="file" accept="image/jpeg,image/png,image/webp" @change="pickImage($event, 'cover')" /></label>
        <label>背景图地址 <input v-model="form.background" class="admin-input" type="text" placeholder="HTTPS 图片地址" /></label>
        <label>上传背景图 <input class="admin-input" type="file" accept="image/jpeg,image/png,image/webp" @change="pickImage($event, 'background')" /></label>
        <label class="game-form-wide">游戏说明 <textarea v-model="form.description" class="admin-input" rows="4" maxlength="500" /></label>
        <label v-if="!form.id && canReview" class="game-form-wide game-submit-review"><input v-model="form.submitReview" type="checkbox" /> 保存后提交审核</label>
      </form>

      <div v-else-if="dialog.kind === 'details'" class="game-detail">
        <img v-if="dialog.game.cover && validImage(dialog.game.cover) && !brokenCovers.has(dialog.game.cover)" :src="dialog.game.cover" alt="游戏封面" @error="markCoverBroken(dialog.game.cover)" />
        <dl>
          <div v-if="dialog.game.originalName"><dt>原名</dt><dd>{{ dialog.game.originalName }}</dd></div>
          <div v-if="dialog.game.englishName"><dt>英文名</dt><dd>{{ dialog.game.englishName }}</dd></div>
          <div v-if="dialog.game.aliases.length"><dt>别名</dt><dd>{{ dialog.game.aliases.join('、') }}</dd></div>
          <div><dt>状态</dt><dd>{{ gameStatuses[dialog.game.status] }}</dd></div>
          <div><dt>制作公司</dt><dd>{{ dialog.game.developer || '—' }}</dd></div>
          <div><dt>发行公司</dt><dd>{{ dialog.game.company || '—' }}</dd></div>
          <div><dt>发行日期</dt><dd>{{ dialog.game.releaseDate || '—' }}</dd></div>
          <div><dt>分类</dt><dd>{{ dialog.game.categories.join('、') || '—' }}</dd></div>
          <div><dt>平台</dt><dd>{{ dialog.game.platforms.join('、') || '—' }}</dd></div>
          <div><dt>标签</dt><dd>{{ dialog.game.tags.join('、') || '—' }}</dd></div>
          <div><dt>评分</dt><dd>{{ dialog.game.rating === null ? '—' : `${dialog.game.rating.toFixed(1)}（${number.format(dialog.game.votes)}）` }}</dd></div>
        </dl>
        <p v-if="dialog.game.description">{{ dialog.game.description }}</p>
        <img v-if="dialog.game.background && validImage(dialog.game.background) && !brokenCovers.has(dialog.game.background)" class="game-detail-background" :src="dialog.game.background" alt="游戏背景图" @error="markCoverBroken(dialog.game.background)" />
      </div>

      <form v-else-if="dialog.kind === 'import'" id="game-import-form" class="admin-modal-form" @submit.prevent="importGames">
        <label>CSV 或 XLSX 文件 <input class="admin-input" type="file" accept=".csv,.xlsx" :disabled="isAdminDemo || isImporting || importTask?.status === 'QUEUED' || importTask?.status === 'RUNNING'" @change="pickImport" /></label>
        <p v-if="isAdminDemo">导入任务需要接入后台接口。</p>
        <p v-else>文件最大 10 MB。导入完成后可查看结果和失败报告。</p>
        <div v-if="importTask" class="game-task" role="status">
          <span>导入任务 {{ importTask.id }} · {{ taskLabels[importTask.status] }}</span>
          <progress v-if="importTask.progress !== undefined" :value="importTask.progress" max="100" aria-label="导入进度" />
          <span v-if="importTask.total !== undefined">共 {{ importTask.total }} 条</span>
          <span v-if="importTask.succeeded !== undefined">成功 {{ importTask.succeeded }} 条</span>
          <span v-if="importTask.failed !== undefined">失败 {{ importTask.failed }} 条</span>
          <span v-if="importTask.duplicates !== undefined">重复 {{ importTask.duplicates }} 条</span>
          <span v-if="importTask.error">{{ importTask.error }}</span>
          <button v-if="taskLink(importTask.reportUrl)" class="admin-button" type="button" @click="downloadTransfer(importTask.reportUrl, 'gamewiki-game-import-report.json')">下载导入报告</button>
          <button v-if="importPollFailed && (importTask.status === 'QUEUED' || importTask.status === 'RUNNING')" type="button" class="admin-button" @click="retryTransfer('imports', importTask)">重新查询</button>
        </div>
      </form>

      <p v-else-if="dialog.kind === 'loading' && !dialogError" role="status">正在加载游戏资料…</p>
      <p v-else-if="dialog.kind === 'delete'">确定删除 {{ dialog.ids.length }} 条游戏记录（{{ gameNames(dialog.ids) }}）？</p>
      <p v-else-if="dialog.kind === 'status'">确定{{ transitionLabels[dialog.action] }} {{ dialog.ids.length }} 条游戏记录（{{ gameNames(dialog.ids) }}）？</p>
      <form v-else-if="dialog.kind === 'batch'" id="game-batch-form" class="admin-modal-form" @submit.prevent="applyChange">
        <p>已选择 {{ dialog.ids.length }} 条游戏记录。</p>
        <label v-if="dialog.action === 'add-tags'">添加标签 <input v-model="batchTags" class="admin-input" placeholder="使用逗号分隔" required /></label>
        <fieldset v-else class="game-platform-field">
          <legend>替换分类</legend>
          <label v-for="item in options?.categories" :key="item"><input v-model="batchCategories" type="checkbox" :value="item" /> {{ item }}</label>
        </fieldset>
      </form>
      <p v-if="dialogError" class="admin-form-error" role="alert">{{ dialogError }}</p>
      <p v-if="dialog.kind === 'import' && importPollFailed" class="admin-form-error" role="alert">{{ importPollFailed }}</p>

      <template #footer>
        <button type="button" class="admin-button" :disabled="isSaving || isImporting" @click="closeDialog">{{ dialog.kind === 'details' || dialog.kind === 'loading' ? '关闭' : '取消' }}</button>
        <button v-if="dialog.kind === 'details' && canWrite" type="button" class="admin-button is-primary" @click="goToGame('admin-game-edit', dialog.game.id)">编辑游戏</button>
        <button v-else-if="dialog.kind === 'form'" type="submit" form="game-edit-form" class="admin-button is-primary" :disabled="isSaving">{{ isSaving ? '正在保存…' : '保存游戏' }}</button>
        <button v-else-if="dialog.kind === 'import'" type="submit" form="game-import-form" class="admin-button is-primary" :disabled="!importFile || isAdminDemo || isImporting || importTask?.status === 'QUEUED' || importTask?.status === 'RUNNING'">{{ isImporting ? '正在提交…' : '确认导入' }}</button>
        <button v-else-if="dialog.kind === 'delete' || dialog.kind === 'status'" type="button" class="admin-button is-primary" :disabled="isSaving" @click="applyChange">{{ dialog.kind === 'delete' ? '确认删除' : '确认变更' }}</button>
        <button v-else-if="dialog.kind === 'batch'" type="submit" form="game-batch-form" class="admin-button is-primary" :disabled="isSaving">确认变更</button>
      </template>
    </AdminDialog>
  </div>
</template>

<style scoped>
.game-management { display: grid; gap: 15px; min-width: 0; }
.game-management :deep(.admin-stats) { margin-bottom: 0; }
.game-list { min-width: 0; }
.game-toolbar { gap: 12px; }
.game-search { position: relative; flex: 1 1 42%; max-width: 600px; min-width: 185px; }
.game-search > .anticon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--admin-muted, var(--gw-muted)); pointer-events: none; }
.game-search .admin-input { width: 100%; padding-inline-start: 37px; }
.game-actions { display: flex; gap: 9px; margin-left: auto; flex-wrap: wrap; }
.game-menu-wrap { position: relative; }
.game-menu-wrap .admin-menu { position: absolute; top: calc(100% + 6px); inset-inline-end: 0; min-width: 148px; z-index: 3; }
.game-menu-hint { margin: 4px 8px; color: var(--admin-muted, var(--gw-muted)); white-space: nowrap; font-size: 12px; }
.game-selection { min-width: 19px; padding-inline: 4px; border-radius: 12px; text-align: center; color: var(--gw-accent); background: var(--gw-accent-tint); font-size: 11px; }
.game-filters { align-items: center; }
.game-filters label { flex: 1 1 180px; max-width: 265px; }
.game-filters .admin-select { flex: 1; min-width: 0; }
.game-reset { margin-left: auto; }
.game-list .admin-table-scroll { width: calc(100% - 16px); margin-inline: 8px; }
.game-table { min-width: 1000px; }
.game-table tbody tr > * { height: 56px; }
.game-table th[scope="row"] { text-align: start; background: transparent; border-bottom: 1px solid var(--gw-border); }
.game-table tbody tr:hover th[scope="row"] { background: var(--gw-surface-hover); }
.game-check { width: 40px; text-align: center; }
.game-cover { width: 89px; height: 41px; display: block; object-fit: cover; }
.game-cover-empty { display: grid; place-items: center; color: var(--gw-subtle); background: var(--gw-accent-tint); }
.game-name { min-width: 134px; max-width: 168px; }
.game-name-link { display: block; width: 100%; text-align: start; border: 0; background: none; color: inherit; padding: 0; cursor: pointer; }
.game-name-link:hover { color: var(--admin-accent, var(--gw-accent)); }
.game-name .admin-row-subtitle { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.game-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; max-width: 180px; }
.game-tags .admin-tag { max-width: 125px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.game-company { min-width: 75px; max-width: 120px; overflow-wrap: anywhere; }
.game-date, .game-platforms, .game-rating { white-space: nowrap; }
.game-date .anticon { margin-right: 3px; color: var(--admin-muted, var(--gw-muted)); }
.game-platforms .anticon { font-size: 16px; }
.game-platforms > span + span { margin-inline-start: 6px; }
.game-platforms small { margin-inline-start: 5px; }
.game-rating span:has(.anticon) { color: var(--gw-warning); font-weight: 600; }
.game-rating small { color: var(--admin-muted, var(--gw-muted)); }
.game-status { display: inline-flex; align-items: center; gap: 6px; padding: 0; border: 0; background: none; color: inherit; white-space: nowrap; cursor: pointer; }
.game-operation-heading { text-align: center; }
.game-row-actions { display: flex; align-items: center; justify-content: center; gap: 1px; }
.game-notice { display: flex; align-items: center; justify-content: space-between; margin: 5px 15px 10px; padding: 3px 10px; border-radius: 8px; color: var(--gw-success); background: var(--gw-success-tint); }
.game-notice.admin-form-error { color: var(--gw-danger); background: var(--gw-danger-tint); }
.game-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; }
.game-form-wide { grid-column: 1 / -1; min-width: 0; }
.game-platform-field { display: flex; gap: 7px 14px; flex-wrap: wrap; border: 1px solid var(--gw-border); border-radius: 8px; padding: 6px 12px 12px; margin: 0; }
.game-platform-field legend { width: auto; padding: 0 5px; margin: 0; font-size: inherit; }
.game-platform-field label { display: flex; align-items: center; gap: 5px; white-space: nowrap; }
.game-upload { display: flex; align-items: end; flex-wrap: wrap; gap: 8px; }
.game-upload input { max-width: 100%; }
.game-detail > img { display: block; max-width: 100%; max-height: 150px; margin-bottom: 14px; border-radius: 8px; object-fit: cover; }
.game-detail dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 8px 14px; margin: 0; }
.game-detail dl > div { display: contents; }
.game-detail dt { font-weight: 600; }
.game-detail dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 900px) {
  .game-search { max-width: none; }
  .game-actions { margin-left: 0; }
}
.game-sort { border: 0; color: inherit; background: transparent; padding: 0; font: inherit; cursor: pointer; }
.game-sort span { margin-inline-start: 4px; }
.game-task { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; }
.game-detail .game-detail-background { width: min(100%, 440px); max-height: 150px; object-fit: cover; }
.game-form .game-submit-review { display: flex; align-items: center; flex-direction: row; }
</style>
