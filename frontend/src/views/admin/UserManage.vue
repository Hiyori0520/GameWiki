<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined, AuditOutlined, CalendarOutlined, CloseCircleOutlined, CrownOutlined,
  DeleteOutlined, DownOutlined, EditOutlined, EyeOutlined, KeyOutlined, LockOutlined, PlusOutlined,
  SafetyCertificateOutlined, SearchOutlined, TeamOutlined, UnlockOutlined, UploadOutlined, UserOutlined,
} from '@ant-design/icons-vue'
import { graphic } from 'echarts/core'
import AdminChart from '../../components/admin/AdminChart.vue'
import AdminDialog from '../../components/admin/AdminDialog.vue'
import AdminPager from '../../components/admin/AdminPager.vue'
import AdminStats from '../../components/admin/AdminStats.vue'
import UserDetailPanel from './users/UserDetail.vue'
import UserLevelPanel from './users/UserLevel.vue'
import { downloadAdminJson } from '../../admin/download'
import { adminPreferences } from '../../admin/preferences'
import type { ECOption } from '../../admin/chartOptions'
import type { UserProfile } from '../../admin/users'
import { adminErrorMessage, hasAdminPermission, isAdminDemo } from '../../api/admin/client'
import { userAdminApi } from '../../api/admin/user'
import { formatUserTime, userMemberships, userRoles, userStatuses } from '../../types/admin/user'
import type {
  UserBanRequest, UserDetail, UserDistribution, UserGrowthPoint, UserId, UserLevelRule,
  UserQuery, UserRankingEntry, UserRecord, UserSort, UserStats, UserStatus, UserTab, UserTrendDays,
} from '../../types/admin/user'

type UserDialog =
  | { kind: 'form'; user?: UserRecord; roleOnly?: boolean }
  | { kind: 'detail'; id: string }
  | { kind: 'levels'; id?: string }
  | { kind: 'state'; ids: UserId[]; status: UserStatus }
  | { kind: 'delete'; ids: UserId[] }
  | { kind: 'ranking' }
  | { kind: 'import' }

const route = useRoute()
const router = useRouter()
const formatNumber = new Intl.NumberFormat('zh-CN').format
const levelRanges = [
  { value: '1-19', label: 'Lv.1–19', first: 1, last: 19 },
  { value: '20-39', label: 'Lv.20–39', first: 20, last: 39 },
  { value: '40-100', label: 'Lv.40 以上', first: 40, last: undefined },
]
const tabChoices: { key: UserTab; label: string }[] = [
  { key: 'all', label: '全部用户' },
  { key: 'standard', label: '普通用户' },
  { key: 'vip', label: 'VIP 用户' },
  { key: 'pending', label: '待审核' },
  { key: 'banned', label: '已封禁' },
]

const tab = ref<UserTab>('all')
const keyword = ref('')
const effectiveKeyword = ref('')
const level = ref('')
const role = ref<keyof typeof userRoles | ''>('')
const status = ref<UserStatus | ''>('')
const startDate = ref('')
const endDate = ref('')
const loginStartDate = ref('')
const loginEndDate = ref('')
const sort = ref<UserSort>('newest')
const page = ref(1)
const pageSize = ref<number>(adminPreferences.value.pageSize)
const visibleUsers = ref<UserRecord[]>([])
const total = ref(0)
const loading = ref(false)
const listError = ref('')
const selected = ref<UserId[]>([])
const feedback = ref('')
const feedbackError = ref(false)
const refresh = ref(0)
const dialog = ref<UserDialog | null>(null)
const dialogError = ref('')
const dialogLoading = ref(false)
const submitting = ref(false)
const banReason = ref('')
const banDays = ref<number | null>(7)
const banConfirm = ref(false)
const form = ref<UserProfile>(blankProfile())
const detailUser = ref<UserDetail>()

// 重置密码弹窗（独立于 AdminDialog，避免干扰其状态机）
const resetUser = ref<UserRecord | null>(null)
const resetInput = ref('')
const resetResult = ref('')
const resetting = ref(false)
const resetError = ref('')
const levelRules = ref<UserLevelRule[]>([])
const levelControl = ref<HTMLSelectElement>()
const roleControl = ref<HTMLSelectElement>()
const batchMenu = ref<HTMLDetailsElement>()
const transferMenu = ref<HTMLDetailsElement>()
const trendDays = ref<UserTrendDays>(30)
const userStats = ref<UserStats>()
const statsError = ref('')
const growth = ref<UserGrowthPoint[]>([])
const growthError = ref('')
const growthLoading = ref(false)
const rankings = ref<UserRankingEntry[]>([])
const rankingError = ref('')
const rankingLoading = ref(false)
const distribution = ref<UserDistribution[]>([])
const distributionError = ref('')
const distributionLoading = ref(false)
const failedAvatars = ref(new Set<string>())

const canSearchEmail = computed(() => hasAdminPermission('users:email:search'))
const canViewEmail = computed(() => hasAdminPermission('users:email:read'))
const canEdit = computed(() => hasAdminPermission('users:write'))
const canBan = computed(() => hasAdminPermission('users:ban'))
const canBatch = computed(() => isAdminDemo && hasAdminPermission('users:batch'))
const searchPlaceholder = computed(() => canSearchEmail.value ? '搜索 UID、昵称、用户名、邮箱…' : '搜索 UID、昵称、用户名…')

const tabCounts = computed<Record<UserTab, number | undefined>>(() => {
  const stats = userStats.value
  return {
    all: stats?.total,
    standard: stats ? stats.total - stats.vip : undefined,
    vip: stats?.vip,
    pending: stats?.pending ?? undefined,
    banned: stats?.banned,
  }
})
const stats = computed(() => {
  const values = userStats.value
  return [
    { label: '注册用户数', value: formatCount(values?.total), change: values ? '+' + formatNumber(values.newLast7Days) : undefined, note: '（近7天）', tone: 'violet' as const, icon: TeamOutlined },
    { label: '活跃用户数', value: formatCount(values?.activeLast7Days), note: '近7天', tone: 'blue' as const, icon: UserOutlined },
    { label: 'VIP 用户数', value: formatCount(values?.vip), tone: 'indigo' as const, icon: CrownOutlined },
    { label: '待审核用户', value: values?.pending === null ? '暂未启用' : formatCount(values?.pending), tone: 'orange' as const, icon: SafetyCertificateOutlined },
    { label: '封禁用户数', value: formatCount(values?.banned), tone: 'pink' as const, icon: CloseCircleOutlined },
  ]
})

function formatCount(value?: number | null) {
  return typeof value === 'number' ? formatNumber(value) : '—'
}

const membershipColors = ['#9a82e4', '#f17cb0', '#ff943c', '#ffc04c']
const distributionTotal = computed(() => distribution.value.reduce((sum, item) => sum + item.count, 0))
const distributionLegend = computed(() => Object.entries(userMemberships).map(([key, label], index) => {
  const count = distribution.value.find(item => item.membership === key)?.count ?? 0
  return { key, label, count, color: membershipColors[index]!, share: distributionTotal.value ? count / distributionTotal.value * 100 : 0 }
}))
const distributionOption = computed<ECOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）' },
  series: [{
    type: 'pie', radius: ['66%', '94%'], label: { show: false },
    data: distributionLegend.value.map(item => ({ name: item.label, value: item.count, itemStyle: { color: item.color } })),
  }],
}))

const invalidDates = computed(() => Boolean(
  startDate.value && endDate.value && startDate.value > endDate.value
  || loginStartDate.value && loginEndDate.value && loginStartDate.value > loginEndDate.value,
))
const allChecked = computed(() => visibleUsers.value.length > 0
  && visibleUsers.value.every(user => selected.value.includes(user.id)))
const partlyChecked = computed(() => !allChecked.value && visibleUsers.value.some(user => selected.value.includes(user.id)))
const hasFilters = computed(() => Boolean(tab.value !== 'all' || keyword.value || level.value || role.value
  || status.value || startDate.value || endDate.value || loginStartDate.value || loginEndDate.value || sort.value !== 'newest'))

const query = computed<UserQuery>(() => {
  const range = levelRanges.find(item => item.value === level.value)
  return {
    page: page.value, pageSize: pageSize.value, keyword: effectiveKeyword.value.trim() || undefined,
    tab: tab.value === 'all' ? undefined : tab.value, status: status.value || undefined,
    demoRole: role.value || undefined, minLevel: range?.first, maxLevel: range?.last,
    registeredFrom: startDate.value || undefined, registeredTo: endDate.value || undefined,
    lastLoginFrom: loginStartDate.value || undefined, lastLoginTo: loginEndDate.value || undefined, sort: sort.value,
  }
})

watch(keyword, (value, _, onCleanup) => {
  const timer = window.setTimeout(() => { effectiveKeyword.value = value }, 250)
  onCleanup(() => window.clearTimeout(timer))
})
watch([tab, effectiveKeyword, level, role, status, startDate, endDate, loginStartDate, loginEndDate, sort, pageSize], () => {
  page.value = 1
  selected.value = []
})
watch(page, () => { selected.value = [] })

watch([query, invalidDates, refresh], async ([filters, invalid], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  listError.value = ''
  visibleUsers.value = []
  if (invalid) {
    loading.value = false
    total.value = 0
    return
  }

  loading.value = true
  try {
    const result = await userAdminApi.list(filters, controller.signal)
    if (controller.signal.aborted) return
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(result.total / filters.pageSize))
    if (filters.page > lastPage) {
      page.value = lastPage
      return
    }
    visibleUsers.value = result.records
  } catch (error) {
    if (controller.signal.aborted) return
    total.value = 0
    listError.value = adminErrorMessage(error)
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}, { immediate: true })

watch(refresh, async (_, __, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  statsError.value = ''
  rankingError.value = ''
  distributionError.value = ''
  rankingLoading.value = true
  distributionLoading.value = true
  const [statsResult, distributionResult, rankingResult] = await Promise.allSettled([
    userAdminApi.stats(controller.signal), userAdminApi.distribution(controller.signal), userAdminApi.ranking(controller.signal),
  ])
  if (controller.signal.aborted) return
  userStats.value = statsResult.status === 'fulfilled' ? statsResult.value : undefined
  if (statsResult.status === 'rejected') statsError.value = adminErrorMessage(statsResult.reason)
  if (tab.value === 'pending' && userStats.value?.pending === null) tab.value = 'all'
  distribution.value = distributionResult.status === 'fulfilled' ? distributionResult.value : []
  if (distributionResult.status === 'rejected') distributionError.value = adminErrorMessage(distributionResult.reason)
  rankings.value = rankingResult.status === 'fulfilled' ? rankingResult.value : []
  if (rankingResult.status === 'rejected') rankingError.value = adminErrorMessage(rankingResult.reason)
  rankingLoading.value = false
  distributionLoading.value = false
}, { immediate: true })

watch([trendDays, refresh], async ([days], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  growthError.value = ''
  growthLoading.value = true
  try {
    const result = await userAdminApi.growth(days, controller.signal)
    if (!controller.signal.aborted) growth.value = result
  } catch (error) {
    if (controller.signal.aborted) return
    growth.value = []
    growthError.value = adminErrorMessage(error)
  } finally {
    if (!controller.signal.aborted) growthLoading.value = false
  }
}, { immediate: true })

function selectTab(key: UserTab) {
  if (key === 'pending' && userStats.value?.pending === null) return
  tab.value = key
  status.value = ''
}

function resetFilters() {
  selectTab('all')
  keyword.value = ''
  effectiveKeyword.value = ''
  role.value = ''
  level.value = ''
  startDate.value = ''
  endDate.value = ''
  loginStartDate.value = ''
  loginEndDate.value = ''
  sort.value = 'newest'
  if (route.query.q || route.query.status) void router.replace({ query: { ...route.query, q: undefined, status: undefined } })
}

function selectPage(checked: boolean) {
  selected.value = checked ? visibleUsers.value.map(user => user.id) : []
}

function blankProfile(): UserProfile {
  return { name: '', username: '', email: '', level: 1, role: 'member', membership: 'standard' }
}

function openDialog(next: UserDialog) {
  if (submitting.value) return
  dialogError.value = ''
  banReason.value = ''
  banDays.value = 7
  banConfirm.value = false
  if (next.kind === 'form') form.value = { ...blankProfile(), ...next.user }
  dialog.value = next
}

function closeDialog() {
  if (submitting.value) return
  if (route.name === 'admin-user-detail' || route.name === 'admin-user-levels') {
    void router.replace({ name: 'admin-users' })
  } else {
    dialog.value = null
  }
}

function openReset(user: UserRecord) {
  resetUser.value = user
  resetInput.value = ''
  resetResult.value = ''
  resetError.value = ''
}

function closeReset() {
  if (resetting.value) return
  resetUser.value = null
}

async function submitReset() {
  if (!resetUser.value || resetting.value) return
  const value = resetInput.value.trim()
  if (value && (value.length < 6 || value.length > 32)) {
    resetError.value = '自定义密码需为 6-32 位'
    return
  }
  resetError.value = ''
  resetting.value = true
  try {
    resetResult.value = await userAdminApi.resetPassword(resetUser.value.id, value || undefined)
  } catch (e) {
    resetError.value = adminErrorMessage(e)
  } finally {
    resetting.value = false
  }
}

watch(dialog, async (current, _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  detailUser.value = undefined
  levelRules.value = []
  dialogLoading.value = false
  if (!current) return
  if (current.kind === 'form' && (!isAdminDemo || !current.user)) return
  if (current.kind !== 'form' && current.kind !== 'detail' && current.kind !== 'levels') return

  dialogLoading.value = true
  try {
    if (current.kind === 'detail') {
      const detail = await userAdminApi.detail(current.id, controller.signal)
      if (!controller.signal.aborted) detailUser.value = detail
    } else if (current.kind === 'levels') {
      const [levels, detail] = await Promise.all([
        userAdminApi.levels(controller.signal), current.id ? userAdminApi.detail(current.id, controller.signal) : undefined,
      ])
      if (controller.signal.aborted) return
      levelRules.value = levels
      detailUser.value = detail
    } else if (current.user) {
      const profile = await userAdminApi.demoProfile(current.user.id)
      if (!controller.signal.aborted) form.value = profile
    }
  } catch (error) {
    if (!controller.signal.aborted) dialogError.value = adminErrorMessage(error)
  } finally {
    if (!controller.signal.aborted) dialogLoading.value = false
  }
})

function runBatch(kind: 'state' | 'delete', nextStatus: UserStatus = 'active') {
  batchMenu.value?.removeAttribute('open')
  const ids = [...selected.value]
  if (canBatch.value && ids.length) openDialog(kind === 'delete' ? { kind, ids } : { kind, ids, status: nextStatus })
}

function closeMenuOnFocusOut(event: FocusEvent) {
  const menu = event.currentTarget as HTMLDetailsElement
  if (!menu.contains(event.relatedTarget as Node | null)) menu.open = false
}

function openImport() {
  transferMenu.value?.removeAttribute('open')
  if (isAdminDemo) openDialog({ kind: 'import' })
}

function notify(message: string, error = false) {
  feedback.value = message
  feedbackError.value = error
}

async function submitChange(action: () => Promise<void>, message: string) {
  if (submitting.value || dialogLoading.value) return
  submitting.value = true
  dialogError.value = ''
  try {
    await action()
    selected.value = []
    dialog.value = null
    refresh.value++
    notify(message)
  } catch (error) {
    dialogError.value = adminErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

async function saveForm() {
  const current = dialog.value
  if (current?.kind !== 'form' || !canEdit.value) return
  const name = form.value.name.trim()
  if (!name || name.length > 30) {
    dialogError.value = '请填写 1 至 30 个字符的昵称。'
    return
  }
  if (isAdminDemo) {
    await submitChange(async () => {
      await userAdminApi.saveDemoProfile(form.value, current.user?.id)
      if (!current.user) resetFilters()
    }, current.user ? '演示用户信息已更新。' : '演示用户已新增。')
  } else if (current.user) {
    const id = current.user.id
    await submitChange(() => userAdminApi.updateProfile(id, name), '用户信息已更新。')
  }
}

async function confirmChange() {
  const current = dialog.value
  if (current?.kind === 'delete' && isAdminDemo && hasAdminPermission('users:delete')) {
    await submitChange(() => userAdminApi.removeDemoProfiles(current.ids), '已删除 ' + current.ids.length + ' 条演示用户记录。')
    return
  }
  if (current?.kind !== 'state' || !canBan.value) return

  let ban: UserBanRequest | undefined
  if (current.status === 'banned') {
    const reason = banReason.value.trim()
    if (!reason || reason.length > 300) {
      dialogError.value = '请输入 1 至 300 个字符的封禁原因。'
      return
    }
    if (!banConfirm.value) {
      dialogError.value = ''
      banConfirm.value = true
      return
    }
    ban = { reason, durationDays: banDays.value }
  }
  const id = current.ids[0]
  await submitChange(async () => {
    if (isAdminDemo) {
      await userAdminApi.setDemoStatus(current.ids, current.status, ban)
    } else if (id !== undefined) {
      if (ban) await userAdminApi.ban(id, ban)
      else await userAdminApi.unban(id)
    }
  }, current.status === 'banned' ? '账号已封禁。' : '账号状态已更新。')
}

async function exportUsers() {
  if (!isAdminDemo || invalidDates.value) return
  transferMenu.value?.removeAttribute('open')
  try {
    const data = await userAdminApi.exportDemoProfiles({ ...query.value, keyword: keyword.value.trim() }, selected.value)
    if (!data.length) {
      notify('当前没有可导出的演示用户。', true)
      return
    }
    downloadAdminJson('gamewiki-demo-users.json', data)
    notify('已导出 ' + data.length + ' 条演示用户资料。')
  } catch (error) {
    notify(adminErrorMessage(error), true)
  }
}

async function importUsers(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!isAdminDemo || !file || submitting.value) return

  await submitChange(async () => {
    if (file.size > 2_000_000) throw new Error('请选择不超过 2 MB 的 JSON 文件。')
    let rows: unknown
    try {
      rows = JSON.parse(await file.text())
    } catch {
      throw new Error('文件不是有效的 JSON。')
    }
    if (!Array.isArray(rows) || !rows.length || rows.length > 500) throw new Error('请选择包含 1 至 500 条记录的 JSON 数组。')
    await userAdminApi.importDemoProfiles(rows)
    resetFilters()
  }, '已导入演示用户资料。')
}

const dialogTitle = computed(() => {
  switch (dialog.value?.kind) {
    case 'form': return dialog.value.roleOnly ? '调整演示用户角色' : dialog.value.user ? '编辑用户' : '新增演示用户'
    case 'detail': return '用户详情'
    case 'levels': return '用户等级'
    case 'state':
      if (dialog.value.status === 'banned') return banConfirm.value ? '确认封禁' : '封禁用户'
      return dialog.value.status === 'active' && !isAdminDemo ? '确认解封' : '确认账号状态变更'
    case 'delete': return '删除演示用户'
    case 'ranking': return '活跃用户榜 · 近7天'
    case 'import': return '导入演示用户'
    default: return ''
  }
})

watch(() => route.query.q, value => {
  keyword.value = typeof value === 'string' ? value : ''
  effectiveKeyword.value = keyword.value
}, { immediate: true })
watch(() => route.query.status, value => {
  if (value === 'pending') {
    selectTab('pending')
  } else if (typeof value === 'string' && Object.hasOwn(userStatuses, value)) {
    selectTab('all')
    status.value = value as UserStatus
  } else {
    selectTab('all')
  }
}, { immediate: true })
watch(() => route.fullPath, () => {
  if (route.name === 'admin-user-detail' && typeof route.params.id === 'string') {
    openDialog({ kind: 'detail', id: route.params.id })
  } else if (route.name === 'admin-user-levels') {
    openDialog({ kind: 'levels', id: typeof route.query.user === 'string' ? route.query.user : undefined })
  } else {
    if (dialog.value?.kind === 'detail' || dialog.value?.kind === 'levels') dialog.value = null
    const action = route.query.action
    if (isAdminDemo && action === 'new') openDialog({ kind: 'form' })
    else if (isAdminDemo && action === 'import') openImport()
    else if (isAdminDemo && action === 'export') void exportUsers()
    if (action) void router.replace({ query: { ...route.query, action: undefined } })
  }
}, { immediate: true })
watch(() => route.query.focus, async value => {
  if (value !== 'level' && value !== 'role') return
  await nextTick()
  const control = value === 'level' ? levelControl.value : roleControl.value
  control?.focus()
}, { immediate: true, flush: 'post' })

const trendOption = computed<ECOption>(() => ({
  tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => String(value) + ' 位新用户' },
  grid: { top: 16, bottom: 27, left: 29, right: 9 },
  xAxis: {
    type: 'category', data: growth.value.map(point => point.date.slice(5)), boundaryGap: false,
    axisTick: { show: false }, axisLine: { lineStyle: { color: '#2a3e63' } },
    axisLabel: { color: '#bac5de', fontSize: 10, hideOverlap: true },
  },
  yAxis: {
    type: 'value', min: 0, minInterval: 1, splitNumber: 4,
    axisLabel: { color: '#bac5de', fontSize: 10 }, splitLine: { lineStyle: { color: '#2a3e63' } },
  },
  series: [{
    name: '新增用户', type: 'line', data: growth.value.map(point => point.newUsers), smooth: true, showSymbol: false, symbol: 'circle',
    itemStyle: { color: '#b4aaff' }, lineStyle: { width: 2 },
    areaStyle: { color: new graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: '#a572e9b8' }, { offset: 1, color: '#a572e908' },
    ]) },
  }],
}))
</script>

<template>
  <div class="user-management">
    <div class="user-main">
      <AdminStats :items="stats" />
      <p v-if="statsError" class="user-feedback is-error" role="alert">无法加载用户统计。{{ statsError }}</p>

      <section class="admin-panel users-list" aria-label="用户列表">
        <div class="user-tabs" aria-label="按用户身份或状态筛选">
          <button v-for="item in tabChoices" :key="item.key" type="button"
            :class="{ 'is-current': tab === item.key }" :aria-pressed="tab === item.key"
            :disabled="item.key === 'pending' && (isAdminDemo || userStats?.pending == null)" @click="selectTab(item.key)">
            {{ item.label }} <span>({{ item.key === 'pending' && (isAdminDemo || userStats?.pending === null) ? '暂未启用' : formatCount(tabCounts[item.key]) }})</span>
          </button>
        </div>

        <div class="admin-toolbar users-toolbar">
          <label class="user-search">
            <SearchOutlined />
            <input v-model="keyword" class="admin-input" type="search" :placeholder="searchPlaceholder" :aria-label="searchPlaceholder" />
          </label>
          <div class="user-table-controls">
            <button v-if="isAdminDemo && canEdit" type="button" class="admin-button is-primary" @click="openDialog({ kind: 'form' })"><PlusOutlined /> 新增用户</button>
            <details v-if="canBatch" ref="batchMenu" class="user-menu" @focusout="closeMenuOnFocusOut" @keydown.esc.stop="batchMenu?.removeAttribute('open')">
              <summary class="admin-button"><AppstoreOutlined /> 批量操作 <DownOutlined /></summary>
              <div class="admin-menu user-menu-options">
                <small v-if="!selected.length">请先勾选用户</small>
                <button type="button" :disabled="!selected.length" @click="runBatch('state', 'active')">批量启用（{{ selected.length }}）</button>
                <button type="button" :disabled="!selected.length" @click="runBatch('state', 'disabled')">批量禁用（{{ selected.length }}）</button>
                <button type="button" :disabled="!selected.length" @click="runBatch('state', 'banned')">批量封禁（{{ selected.length }}）</button>
                <button type="button" :disabled="!selected.length" @click="runBatch('delete')">批量删除（{{ selected.length }}）</button>
              </div>
            </details>
            <details v-if="isAdminDemo" ref="transferMenu" class="user-menu" @focusout="closeMenuOnFocusOut" @keydown.esc.stop="transferMenu?.removeAttribute('open')">
              <summary class="admin-button"><UploadOutlined /> 导入/导出 <DownOutlined /></summary>
              <div class="admin-menu user-menu-options">
                <button type="button" @click="openImport">导入用户 JSON</button>
                <button type="button" @click="exportUsers">{{ selected.length ? '导出已选用户' : '导出查询结果' }}</button>
              </div>
            </details>
          </div>
        </div>

        <div class="admin-filters users-filters">
          <label>用户等级 <select ref="levelControl" v-model="level" class="admin-select"><option value="">全部等级</option><option v-for="option in levelRanges" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
          <label>角色 <select ref="roleControl" v-model="role" class="admin-select" aria-label="用户角色"><option value="">全部角色</option><option v-for="(label, key) in userRoles" :key="key" :value="key">{{ label }}</option></select></label>
          <label>用户状态 <select v-model="status" class="admin-select"><option value="">全部状态</option><option v-for="(label, key) in userStatuses" :key="key" :value="key">{{ label }}</option></select></label>
          <div class="user-date-filter">
            <span>注册时间</span>
            <div class="user-date-controls" role="group" aria-label="注册时间范围">
              <CalendarOutlined />
              <input v-model="startDate" type="date" aria-label="开始日期" /><span>～</span><input v-model="endDate" type="date" aria-label="结束日期" />
            </div>
          </div>
          <div class="user-date-filter">
            <span>最近登录</span>
            <div class="user-date-controls" role="group" aria-label="最近登录时间范围">
              <CalendarOutlined />
              <input v-model="loginStartDate" type="date" aria-label="最近登录开始日期" /><span>～</span><input v-model="loginEndDate" type="date" aria-label="最近登录结束日期" />
            </div>
          </div>
          <label>排序 <select v-model="sort" class="admin-select"><option value="newest">最新注册</option><option value="oldest">最早注册</option><option value="login">最近登录</option><option value="level">最高等级</option></select></label>
          <button v-if="hasFilters" type="button" class="user-link" @click="resetFilters">重置</button>
        </div>
        <p v-if="invalidDates" class="user-feedback is-error" role="alert">开始日期须早于或等于结束日期。</p>
        <p v-if="listError" class="user-feedback is-error" role="alert">{{ listError }} <button class="user-link" type="button" @click="refresh++">重试</button></p>
        <p v-if="feedback" class="user-feedback" :class="{ 'is-error': feedbackError }" :role="feedbackError ? 'alert' : 'status'">{{ feedback }}</p>

        <div class="admin-table-scroll users-table-scroll" tabindex="0" aria-label="可滚动的用户数据表">
          <table class="admin-table users-table" :aria-busy="loading">
            <thead>
              <tr>
                <th scope="col"><input type="checkbox" aria-label="选择当前页全部用户" :checked="allChecked" :indeterminate.prop="partlyChecked" :disabled="!visibleUsers.length" @change="selectPage(($event.target as HTMLInputElement).checked)" /></th>
                <th scope="col">UID</th><th scope="col">用户信息</th><th scope="col">等级</th><th scope="col">身份</th>
                <th scope="col">注册时间</th><th scope="col">最后登录</th><th scope="col">状态</th><th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in visibleUsers" :key="user.id">
                <td><input v-model="selected" type="checkbox" :value="user.id" :aria-label="`选择 ${user.name}`" /></td>
                <td>{{ user.id }}</td>
                <td>
                  <div class="user-identity">
                    <img v-if="user.avatar && !failedAvatars.has(user.avatar)" class="user-avatar" :src="user.avatar" alt="" loading="lazy" @error="failedAvatars.add(user.avatar)" />
                    <span v-else class="user-avatar user-avatar-fallback"><UserOutlined /></span>
                    <div><span class="admin-row-title">{{ user.name }}</span><span class="admin-row-subtitle">{{ user.username }}</span></div>
                  </div>
                </td>
                <td><span class="admin-tag" :class="{ 'is-blue': user.level < 30 }">Lv.{{ user.level }}</span></td>
                <td><span class="admin-tag" :class="user.membership === 'standard' ? 'user-standard-tag' : 'is-orange'"><CrownOutlined v-if="user.membership !== 'standard'" /> {{ userMemberships[user.membership] }}</span></td>
                <td>{{ formatUserTime(user.registeredAt) }}</td><td>{{ formatUserTime(user.lastLoginAt) }}</td>
                <td>
                  <div class="user-state" :class="`status-${user.status}`">
                    <button v-if="isAdminDemo" class="admin-switch" :class="{ 'is-on': user.status === 'active' }" type="button" role="switch" :disabled="!canBan" :aria-checked="user.status === 'active'" :aria-label="`${user.name} 的账号启用状态`"
                      @click="openDialog({ kind: 'state', ids: [user.id], status: user.status === 'active' ? 'disabled' : 'active' })"></button>
                    <span>{{ userStatuses[user.status] }}</span>
                  </div>
                </td>
                <td>
                  <div class="user-row-actions">
                    <button class="admin-icon-button" type="button" :title="`查看 ${user.name}`" :aria-label="`查看 ${user.name}`" @click="router.push({ name: 'admin-user-detail', params: { id: user.id } })"><EyeOutlined /></button>
                    <button v-if="canEdit" class="admin-icon-button" type="button" :title="`编辑 ${user.name}`" :aria-label="`编辑 ${user.name}`" @click="openDialog({ kind: 'form', user })"><EditOutlined /></button>
                    <button v-if="canEdit && !isAdminDemo" class="admin-icon-button" type="button" :title="`重置 ${user.name} 的密码`" :aria-label="`重置 ${user.name} 的密码`" @click="openReset(user)"><KeyOutlined /></button>
                    <button v-if="isAdminDemo && canEdit" class="admin-icon-button" type="button" :title="`调整 ${user.name} 的角色`" :aria-label="`调整 ${user.name} 的角色`" @click="openDialog({ kind: 'form', user, roleOnly: true })"><AuditOutlined /></button>
                    <button v-if="canBan" class="admin-icon-button" :class="{ 'is-danger': user.status !== 'banned' }" type="button"
                      :title="(user.status === 'banned' ? '解封 ' : '封禁 ') + user.name" :aria-label="(user.status === 'banned' ? '解封 ' : '封禁 ') + user.name"
                      @click="openDialog({ kind: 'state', ids: [user.id], status: user.status === 'banned' ? 'active' : 'banned' })">
                      <UnlockOutlined v-if="user.status === 'banned'" /><LockOutlined v-else />
                    </button>
                    <button v-if="isAdminDemo && hasAdminPermission('users:delete')" class="admin-icon-button is-danger" type="button" :title="`删除 ${user.name}`" :aria-label="`删除 ${user.name}`" @click="openDialog({ kind: 'delete', ids: [user.id] })"><DeleteOutlined /></button>
                  </div>
                </td>
              </tr>
              <tr v-if="!visibleUsers.length"><td colspan="9"><div class="admin-empty" role="status">{{ loading ? '正在加载用户…' : listError ? '用户列表无法加载' : invalidDates ? '请先调整日期范围' : '没有符合条件的用户' }}</div></td></tr>
            </tbody>
          </table>
        </div>
        <AdminPager v-model:page="page" v-model:size="pageSize" :total="total" />
      </section>
    </div>

    <aside class="user-insights" aria-label="用户统计图表">
      <section class="admin-panel user-insight">
        <header><h2>用户增长趋势</h2><select v-model.number="trendDays" class="admin-select" aria-label="用户增长统计周期"><option :value="7">近7天</option><option :value="30">近30天</option><option :value="90">近90天</option></select></header>
        <p v-if="growthError" class="admin-empty" role="alert">{{ growthError }}</p>
        <p v-else-if="growthLoading || !growth.length" class="admin-empty" role="status">{{ growthLoading ? '正在加载增长趋势…' : '暂无增长数据' }}</p>
        <AdminChart v-else class="user-trend" :option="trendOption" :label="`近 ${trendDays} 天新增用户趋势`" />
      </section>
      <section class="admin-panel user-insight">
        <header><h2>用户分布</h2></header>
        <p v-if="distributionError" class="admin-empty" role="alert">{{ distributionError }}</p>
        <p v-else-if="distributionLoading || !distributionTotal" class="admin-empty" role="status">{{ distributionLoading ? '正在加载用户分布…' : '暂无用户分布' }}</p>
        <div v-else class="user-distribution">
          <div class="user-donut">
            <AdminChart :option="distributionOption" label="用户会员身份分布" />
            <span aria-hidden="true"><strong>{{ formatCount(distributionTotal) }}</strong>总用户</span>
          </div>
          <ul>
            <li v-for="segment in distributionLegend" :key="segment.key"><span class="user-legend-dot" :style="{ background: segment.color }"></span><span>{{ segment.label }}</span><span>{{ segment.share.toFixed(1) }}%</span></li>
          </ul>
        </div>
      </section>
      <section class="admin-panel user-insight">
        <header><h2>活跃用户榜 <small>（近7天）</small></h2><button v-if="rankings.length" class="user-link" type="button" @click="openDialog({ kind: 'ranking' })">查看全部 ›</button></header>
        <p v-if="rankingError" class="admin-empty" role="alert">{{ rankingError }}</p>
        <p v-else-if="rankingLoading" class="admin-empty" role="status">正在加载活跃用户榜…</p>
        <ol v-else-if="rankings.length" class="user-ranking">
          <li v-for="user in rankings.slice(0, 3)" :key="user.id"><span class="user-rank" :data-rank="user.rank">{{ user.rank }}</span><img v-if="user.avatar && !failedAvatars.has(user.avatar)" class="user-avatar" :src="user.avatar" alt="" loading="lazy" @error="failedAvatars.add(user.avatar)" /><span v-else class="user-avatar user-avatar-fallback"><UserOutlined /></span><strong>{{ user.name }}</strong><small>贡献值 {{ formatNumber(user.contribution) }}</small></li>
        </ol>
        <p v-else class="admin-empty">暂无活跃用户</p>
      </section>
    </aside>

    <AdminDialog v-if="dialog" :title="dialogTitle" @close="closeDialog">
      <p v-if="dialogLoading" class="admin-empty" role="status">正在加载…</p>
      <form v-else-if="dialog.kind === 'form'" id="admin-user-form" class="admin-modal-form user-form" novalidate @submit.prevent="saveForm">
        <template v-if="!dialog.roleOnly">
          <label>昵称 <input v-model="form.name" class="admin-input" maxlength="30" :disabled="submitting" required /></label>
          <label>用户名 <input v-model="form.username" class="admin-input" maxlength="24" :readonly="!isAdminDemo" :disabled="submitting" required /></label>
          <template v-if="isAdminDemo">
            <label>邮箱 <input v-model="form.email" class="admin-input" type="email" maxlength="120" :disabled="submitting" required /></label>
            <label>等级 <input v-model.number="form.level" class="admin-input" type="number" min="1" max="100" :disabled="submitting" required /></label>
            <label>会员身份 <select v-model="form.membership" class="admin-select" :disabled="submitting"><option v-for="(label, key) in userMemberships" :key="key" :value="key">{{ label }}</option></select></label>
          </template>
        </template>
        <label v-if="isAdminDemo">角色 <select v-model="form.role" class="admin-select" :disabled="submitting"><option v-for="(label, key) in userRoles" :key="key" :value="key">{{ label }}</option></select></label>
      </form>
      <UserDetailPanel v-else-if="dialog.kind === 'detail' && detailUser" :user="detailUser" :show-email="canViewEmail" />
      <UserLevelPanel v-else-if="dialog.kind === 'levels' && !dialogError" :rules="levelRules" :user="detailUser" />
      <template v-else-if="dialog.kind === 'state'">
        <template v-if="dialog.status === 'banned'">
          <div v-if="!banConfirm" class="admin-modal-form user-form">
            <label class="user-ban-reason">封禁原因 <textarea v-model="banReason" class="admin-input" rows="3" maxlength="300" required></textarea></label>
            <label>封禁期限 <select v-model="banDays" class="admin-select"><option :value="1">1 天</option><option :value="7">7 天</option><option :value="30">30 天</option><option :value="null">永久</option></select></label>
          </div>
          <dl v-else class="user-details"><dt>用户数量</dt><dd>{{ dialog.ids.length }}</dd><dt>封禁原因</dt><dd>{{ banReason.trim() }}</dd><dt>封禁期限</dt><dd>{{ banDays ? `${banDays} 天` : '永久' }}</dd></dl>
        </template>
        <p v-else>{{ isAdminDemo ? `将 ${dialog.ids.length} 个演示账号的状态改为「${userStatuses[dialog.status]}」？` : '确认解除该账号的封禁？' }}</p>
      </template>
      <p v-else-if="dialog.kind === 'delete'">确定删除所选 {{ dialog.ids.length }} 条演示用户记录？删除后无法恢复。</p>
      <ol v-else-if="dialog.kind === 'ranking'" class="user-ranking">
        <li v-for="user in rankings" :key="user.id"><span class="user-rank" :data-rank="user.rank">{{ user.rank }}</span><strong>{{ user.name }}</strong><small>贡献值 {{ formatNumber(user.contribution) }}</small></li>
      </ol>
      <div v-else-if="dialog.kind === 'import'" class="user-import">
        <p>请选择 JSON 数组，或先导出一份样例。每条记录都需要昵称 <code>name</code>、用户名 <code>username</code> 和邮箱 <code>email</code>。</p>
        <input type="file" accept=".json,application/json" aria-label="选择用户 JSON 文件" :disabled="submitting" @change="importUsers" />
      </div>
      <p v-if="dialogError" class="admin-form-error" role="alert">{{ dialogError }}</p>
      <template #footer>
        <button type="button" class="admin-button" :disabled="submitting" @click="closeDialog">{{ dialog.kind === 'detail' || dialog.kind === 'ranking' || dialog.kind === 'levels' ? '关闭' : '取消' }}</button>
        <button v-if="dialog.kind === 'form'" class="admin-button is-primary" type="submit" form="admin-user-form" :disabled="submitting || dialogLoading">{{ submitting ? '正在保存…' : '保存' }}</button>
        <button v-if="dialog.kind === 'state' || dialog.kind === 'delete'" class="admin-button is-primary" type="button" :disabled="submitting" @click="confirmChange">
          {{ submitting ? '正在提交…' : dialog.kind === 'delete' ? '确认删除' : dialog.status === 'banned' ? (banConfirm ? '确认封禁' : '下一步') : '确认变更' }}
        </button>
      </template>
    </AdminDialog>

    <!-- 重置密码（走真实接口，演示模式不可用） -->
    <div v-if="resetUser" class="user-reset-mask" @click.self="closeReset">
      <section class="user-reset-card" role="dialog" aria-modal="true" aria-label="重置用户密码">
        <header>
          <h2>重置密码 · {{ resetUser.name }}</h2>
          <button type="button" class="user-reset-close" aria-label="关闭" @click="closeReset">×</button>
        </header>

        <template v-if="!resetResult">
          <p class="user-reset-note">重置后原密码立即失效，用户需用新密码登录。明文只在本窗口显示一次。</p>
          <form class="user-reset-form" @submit.prevent="submitReset">
            <label>新密码
              <input v-model="resetInput" class="admin-input" type="text" maxlength="32"
                     placeholder="留空则自动生成 10 位随机密码" :disabled="resetting" />
            </label>
          </form>
          <p v-if="resetError" class="admin-form-error" role="alert">{{ resetError }}</p>
          <div class="user-reset-actions">
            <button type="button" class="admin-button" :disabled="resetting" @click="closeReset">取消</button>
            <button type="button" class="admin-button is-primary" :disabled="resetting" @click="submitReset">
              {{ resetting ? '正在重置…' : '确认重置' }}
            </button>
          </div>
        </template>

        <template v-else>
          <p class="user-reset-note">重置成功，请通过安全渠道告知用户，并提醒其登录后自行修改。</p>
          <div class="user-reset-result">{{ resetResult }}</div>
          <div class="user-reset-actions">
            <button type="button" class="admin-button is-primary" @click="closeReset">我已记下</button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.user-management { display: grid; grid-template-columns: minmax(0, 3.2fr) minmax(250px, 1fr); align-items: start; gap: 12px; }
.user-main, .user-insights { display: grid; min-width: 0; gap: 12px; }
.user-main :deep(.admin-stats) { margin-bottom: 7px; }
.user-main :deep(.admin-stat) { min-height: 100px; padding-inline: clamp(10px, .9vw, 16px); gap: 10px; }
.user-main :deep(.stat-icon) { width: clamp(36px, 3.3vw, 56px); font-size: clamp(22px, 2.2vw, 34px); }
.user-main :deep(.stat-label) { font-size: 13px; white-space: nowrap; }
.user-main :deep(.stat-foot) { font-size: 11px; white-space: nowrap; }
.users-list { min-width: 0; }
.user-tabs { display: flex; overflow: auto; border-bottom: 1px solid var(--gw-border); padding-inline: 10px; gap: clamp(14px, 2vw, 30px); }
.user-tabs button { padding: 13px 5px 11px; border: 0; border-bottom: 3px solid transparent; background: none; color: var(--gw-text); cursor: pointer; white-space: nowrap; font-weight: 650; }
.user-tabs button span { font-weight: 450; }
.user-tabs button.is-current { color: var(--gw-accent); border-color: var(--gw-accent); }
.user-tabs button:disabled { color: var(--admin-muted); cursor: default; }
.users-toolbar { justify-content: space-between; }
.users-toolbar, .users-filters { padding: 10px 12px; }
.user-search { display: grid; grid-template-columns: 1fr; align-items: center; flex: 1 1 220px; max-width: 480px; }
.user-search > * { grid-area: 1 / 1; }
.user-search > span { z-index: 1; justify-self: start; margin-inline-start: 13px; color: var(--gw-muted); pointer-events: none; }
.user-search .admin-input { width: 100%; padding-inline-start: 37px; }
.user-table-controls { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.user-table-controls :deep(.admin-button) { white-space: nowrap; }
.user-menu { position: relative; }
.user-menu:not([open]) .user-menu-options { display: none; }
.user-table-controls summary::-webkit-details-marker { display: none; }
.user-table-controls summary { list-style: none; }
.user-menu-options { position: absolute; z-index: 5; top: calc(100% + 5px); right: 0; min-width: max-content; padding: 5px; border: 1px solid var(--gw-border); border-radius: 9px; display: grid; gap: 2px; background: var(--gw-surface-raised); box-shadow: var(--gw-shadow); }
.user-menu-options button, .user-menu-options small { border: 0; padding: 7px 10px; border-radius: 5px; background: none; text-align: left; color: var(--gw-text); }
.user-menu-options button:not(:disabled) { cursor: pointer; }
.user-menu-options button:disabled { color: var(--gw-subtle); }
.user-menu-options button:hover:not(:disabled) { background: var(--gw-surface-hover); }
.users-filters { padding-top: 0; font-size: 12px; gap: 8px; }
.users-filters .admin-select, .user-insight .admin-select { height: 32px; min-height: 32px; padding-inline: 7px; font-size: 12px; }
.users-filters label, .user-date-filter { display: flex; align-items: center; gap: 6px; white-space: nowrap; font-weight: 650; }
.user-date-controls { display: flex; align-items: center; padding-inline: 6px; gap: 4px; border: 1px solid var(--gw-border); border-radius: 8px; background: var(--gw-surface); color: var(--gw-muted); }
.user-date-controls input { min-width: 0; width: 88px; border: 0; background: transparent; color: var(--gw-text); height: 30px; padding: 0; }
.user-date-controls input::-webkit-calendar-picker-indicator { width: 10px; margin: 0; }
.user-feedback { margin: 0 12px 10px; color: var(--gw-success); }
.user-feedback.is-error { color: var(--gw-danger); }
.users-table-scroll { max-height: 335px; }
.users-table { width: 100%; min-width: 765px; font-size: 12px; }
.users-table :deep(td), .users-table :deep(th) { padding-inline: 7px; white-space: nowrap; }
.users-table :deep(td) { padding-block: 5px; }
.users-table th { position: sticky; top: 0; z-index: 1; background: var(--gw-surface-raised); }
.users-table :deep(.admin-tag) { padding-inline: 6px; }
.users-table input[type='checkbox'] { width: 15px; height: 15px; vertical-align: middle; accent-color: var(--gw-accent); }
.user-identity { display: flex; gap: 8px; align-items: center; }
.user-identity .admin-row-title, .user-identity .admin-row-subtitle { display: block; max-width: 112px; overflow: hidden; text-overflow: ellipsis; }
.user-avatar { flex: none; width: 33px; height: 33px; border-radius: 50%; display: inline-grid; place-items: center; object-fit: cover; }
.user-avatar-fallback { background: var(--gw-accent-tint); color: var(--gw-accent); }
.user-standard-tag { background: var(--gw-surface-raised); color: var(--gw-muted); }
.user-state, .user-row-actions { display: flex; gap: 4px; align-items: center; }
.user-state.status-banned { color: var(--gw-danger); }
.user-state.status-pending { color: var(--gw-warning); }
.user-row-actions { gap: 0; }
.user-row-actions .admin-icon-button { width: 27px; height: 28px; font-size: 15px; }
.user-insight { padding: 12px 14px; }
.user-insight header { display: flex; align-items: center; justify-content: space-between; gap: 7px; }
.user-insight h2 { margin: 0; color: var(--gw-text); font-size: 16px; font-weight: 750; }
.user-insight h2 small { font-weight: 500; font-size: 12px; }
.user-link { border: 0; padding: 3px; background: none; color: var(--gw-accent); cursor: pointer; white-space: nowrap; font-weight: 650; }
.user-link:hover { text-decoration: underline; }
.user-insight .user-trend { width: 100%; height: 166px; }
.user-distribution { display: flex; gap: 12px; align-items: center; padding-top: 12px; }
.user-donut { display: grid; place-items: center; width: clamp(81px, 7.5vw, 116px); aspect-ratio: 1; flex: none; border-radius: 50%; }
.user-donut > * { grid-area: 1 / 1; }
.user-donut :deep(.admin-chart) { width: 100%; height: 100%; }
.user-donut > span { display: grid; align-content: center; justify-items: center; pointer-events: none; font-size: 11px; }
.user-donut strong { font-size: 17px; line-height: 1.1; }
.user-distribution ul, .user-ranking { margin: 0; padding: 0; list-style: none; }
.user-distribution ul { display: grid; gap: 6px; flex: 1; }
.user-distribution li { display: grid; grid-template-columns: 8px 1fr auto; gap: 6px; align-items: center; font-size: 12px; white-space: nowrap; }
.user-legend-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.user-ranking { margin-top: 8px; }
.user-ranking li { display: flex; gap: 7px; align-items: center; min-height: 42px; border-top: 1px solid var(--gw-border); padding-block: 4px; }
.user-ranking li:first-child { border-top: 0; }
.user-ranking strong { overflow-wrap: anywhere; }
.user-ranking small { margin-left: auto; color: var(--gw-muted); white-space: nowrap; font-size: 11px; }
.user-rank { display: inline-grid; place-items: center; flex: none; width: 20px; height: 20px; border-radius: 6px; background: var(--gw-accent-tint); color: var(--gw-accent); font-weight: 700; font-size: 12px; }
.user-rank[data-rank='1'] { background: var(--gw-warning-tint); color: var(--gw-warning); }
.user-rank[data-rank='2'] { background: var(--gw-border); color: var(--gw-muted); }
.user-rank[data-rank='3'] { background: var(--gw-warning-tint); color: var(--gw-warning); }
.user-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.user-form label { display: grid; gap: 5px; }
.user-form :deep(input), .user-form :deep(select) { width: 100%; }
.user-ban-reason { grid-column: 1 / -1; }
.user-ban-reason textarea { height: auto; min-height: 72px; padding-block: 8px; resize: vertical; }
.user-details { display: grid; grid-template-columns: minmax(66px, auto) 1fr; gap: 8px 14px; }
.user-details dt { color: var(--gw-muted); }
.user-details dd { margin: 0; overflow-wrap: anywhere; }
.user-import { display: grid; gap: 15px; }

@media (min-width: 1550px) { .users-table-scroll { max-height: 364px; } }
@media (max-width: 1260px) { .user-management { grid-template-columns: minmax(0, 1fr); } .user-insights { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 950px) { .user-insights { grid-template-columns: repeat(2, minmax(0, 1fr)); } }

/* 重置密码弹窗 */
.user-reset-mask { position: fixed; inset: 0; z-index: 80; display: flex; align-items: center; justify-content: center; padding: 20px; background: #0f172a73; }
.user-reset-card { width: 100%; max-width: 420px; padding: 18px 20px 16px; border-radius: 12px; background: var(--gw-surface-raised); box-shadow: var(--gw-shadow); }
.user-reset-card header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.user-reset-card h2 { margin: 0; font-size: 15px; color: var(--gw-text); }
.user-reset-close { width: 24px; height: 24px; border: 0; border-radius: 50%; background: none; color: var(--gw-subtle); font-size: 18px; line-height: 20px; cursor: pointer; }
.user-reset-close:hover { background: var(--gw-surface-raised); color: var(--gw-muted); }
.user-reset-note { margin: 0 0 12px; font-size: 12px; line-height: 1.7; color: var(--gw-muted); }
.user-reset-form label { display: grid; gap: 6px; font-size: 12px; color: var(--gw-muted); }
.user-reset-result { margin: 4px 0 14px; padding: 14px; border-radius: 8px; background: var(--gw-surface-raised); font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 18px; letter-spacing: 1px; text-align: center; color: var(--gw-text); user-select: all; }
.user-reset-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px; }
</style>
