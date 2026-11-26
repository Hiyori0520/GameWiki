<script setup lang="ts">
import { computed, ref, shallowRef, watchEffect } from 'vue'
import {
  AlertOutlined, AppstoreOutlined, BarChartOutlined, BookOutlined, ClockCircleOutlined, CloudServerOutlined,
  DatabaseOutlined, DownloadOutlined, FileTextOutlined, FireOutlined, HddOutlined, HistoryOutlined,
  PieChartOutlined, RightOutlined, SettingOutlined, TeamOutlined, ThunderboltOutlined, UploadOutlined, UserAddOutlined,
} from '@ant-design/icons-vue'
import AdminChart from '../../components/admin/AdminChart.vue'
import AdminDialog from '../../components/admin/AdminDialog.vue'
import AdminGameIcon from '../../components/admin/AdminGameIcon.vue'
import AdminStats from '../../components/admin/AdminStats.vue'
import { games } from '../../admin/games'
import { guides } from '../../admin/guides'
import { adminDashboardPeriods, adminPreferences } from '../../admin/preferences'
import { users } from '../../admin/users'
import { demoActivity } from '../../admin/storage'
import type { ECOption } from '../../admin/chartOptions'
import { adminErrorMessage, hasAdminPermission, isAdminDemo } from '../../api/admin/client'
import {
  dashboardApi,
  type DashboardContent, type DashboardHealth, type DashboardLog, type DashboardPeriod,
  type DashboardRecentItem, type DashboardSummary, type DashboardTrend,
} from '../../api/admin/dashboard'

const period = ref<DashboardPeriod>(adminPreferences.value.dashboardPeriod)
const revision = ref(0)

function useDashboardData<T>(load: (signal: AbortSignal) => Promise<T> | undefined) {
  const data = shallowRef<T>()
  const loading = ref(false)
  const error = ref('')
  watchEffect((onCleanup) => {
    if (isAdminDemo) return
    void revision.value
    const controller = new AbortController()
    onCleanup(() => controller.abort())
    const request = load(controller.signal)
    data.value = undefined
    error.value = ''
    loading.value = Boolean(request)
    if (!request) return
    void request.then((value) => {
      if (controller.signal.aborted) return
      if (value == null) throw new Error('后台接口未返回数据。')
      data.value = value
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) error.value = adminErrorMessage(reason)
    }).finally(() => {
      if (!controller.signal.aborted) loading.value = false
    })
  })
  return { data, loading, error }
}

const allLatestTabs = [
  { id: 'games', label: '最新游戏', icon: AdminGameIcon },
  { id: 'guides', label: '最新攻略', icon: BookOutlined },
  { id: 'users', label: '最新用户', icon: TeamOutlined },
] as const
const latestTabs = computed(() => allLatestTabs.filter((tab) => hasAdminPermission(`${tab.id}:read`)))
const selectedLatestTab = ref<DashboardContent>('games')
const latestTab = computed(() =>
  latestTabs.value.find((tab) => tab.id === selectedLatestTab.value) ?? latestTabs.value[0],
)
const summaryRequest = useDashboardData(dashboardApi.summary)
const trendRequest = useDashboardData((signal) => dashboardApi.trends(period.value, signal))
const distributionRequest = useDashboardData(dashboardApi.distribution)
const recentRequest = useDashboardData((signal) => latestTab.value && dashboardApi.recent(latestTab.value.id, signal))
const logsRequest = useDashboardData(dashboardApi.logs)
const healthRequest = useDashboardData(dashboardApi.health)

const gameRows = games.records
const guideRows = computed(() => guides.records.value.filter((guide) => guide.revisionOf === undefined))
const userRows = users.records

function recentCutoff(days: number) {
  const cutoff = new Date()
  cutoff.setHours(0, 0, 0, 0)
  cutoff.setDate(cutoff.getDate() - days + 1)
  return cutoff.getTime()
}

const demoPendingGuides = computed(() =>
  guides.records.value.filter((guide) => guide.status === 'pending'),
)
const summary = computed<DashboardSummary | undefined>(() => {
  if (!isAdminDemo) return summaryRequest.data.value
  const cutoff = recentCutoff(7)
  const gamesAdded = gameRows.value.filter(({ createdAt }) => Date.parse(createdAt) >= cutoff).length
  const guidesAdded = guideRows.value.filter(({ createdAt }) => Date.parse(createdAt) >= cutoff).length
  return {
    totals: {
      games: gameRows.value.length,
      guides: guideRows.value.length,
      users: userRows.value.length,
      content: gameRows.value.length + guideRows.value.length,
    },
    change7d: {
      games: gamesAdded,
      guides: guidesAdded,
      users: userRows.value.filter(({ registeredAt }) => Date.parse(registeredAt) >= cutoff).length,
      content: gamesAdded + guidesAdded,
    },
    pendingGuides: demoPendingGuides.value.length,
  }
})
const statCards = [
  { metric: 'games', label: '游戏总数', icon: AdminGameIcon, tone: 'violet' },
  { metric: 'guides', label: '攻略总数', icon: BookOutlined, tone: 'indigo' },
  { metric: 'users', label: '用户总数', icon: TeamOutlined, tone: 'blue' },
  { metric: 'content', label: '总内容量', icon: FileTextOutlined, tone: 'orange' },
] as const
const totals = computed(() => statCards.map((item) => {
  const change = summary.value?.change7d?.[item.metric]
  return {
    ...item,
    value: summary.value?.totals?.[item.metric] ?? '—',
    change: change === undefined ? undefined : (change > 0 ? '+' : '') + change.toLocaleString('zh-CN'),
    note: change === undefined ? undefined : '近 7 天',
  }
}))

function localDay(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const trends = computed<DashboardTrend[]>(() => {
  if (!isAdminDemo) return trendRequest.data.value ?? []
  const countByDay = (records: { createdAt: string }[]) => {
    const counts = new Map<string, number>()
    for (const record of records) {
      const date = new Date(record.createdAt)
      if (!Number.isFinite(date.getTime())) continue
      const key = localDay(date)
      counts.set(key, (counts.get(key) ?? 0) + 1)
    }
    return counts
  }
  const gameCounts = countByDay(gameRows.value)
  const guideCounts = countByDay(guideRows.value)
  const firstDay = new Date(recentCutoff(period.value))
  return Array.from({ length: period.value }, (_, offset) => {
    const date = new Date(firstDay)
    date.setDate(date.getDate() + offset)
    const key = localDay(date)
    return { date: key, games: gameCounts.get(key) ?? 0, guides: guideCounts.get(key) ?? 0 }
  })
})
const trendOption = computed<ECOption>(() => {
  const axisLabel = { color: '#bac5de', fontSize: 11 }
  return {
    animationDuration: 320,
    legend: { top: 0, itemWidth: 9, itemHeight: 9, textStyle: { color: '#bac5de' }, icon: 'circle' },
    tooltip: { trigger: 'axis', renderMode: 'richText' },
    grid: { top: 34, bottom: 25, left: 47, right: 12 },
    xAxis: {
      type: 'category', data: trends.value.map(({ date }) => date.slice(5, 10)), boundaryGap: false,
      axisLabel, axisLine: { lineStyle: { color: '#2a3e63' } }, axisTick: { show: false },
    },
    yAxis: { type: 'value', min: 0, minInterval: 1, axisLabel, splitLine: { lineStyle: { color: '#2a3e63' } } },
    series: [
      { name: '新增游戏', data: trends.value.map(({ games }) => games), type: 'line', symbol: 'circle', symbolSize: 6, itemStyle: { color: '#b4aaff' }, areaStyle: { color: { type: 'linear', x: 0, x2: 0, y: 0, y2: 1, colorStops: [{ offset: 0, color: '#8f59eb79' }, { offset: 1, color: '#b99af100' }] } } },
      { name: '新增攻略', data: trends.value.map(({ guides }) => guides), type: 'line', symbol: 'circle', symbolSize: 6, itemStyle: { color: '#e968a7' }, areaStyle: { color: { type: 'linear', x: 0, x2: 0, y: 0, y2: 1, colorStops: [{ offset: 0, color: '#ee7db062' }, { offset: 1, color: '#f6aaca03' }] } } },
    ],
  }
})

const categoryColors = ['#9165dd', '#ee8abb', '#fac379', '#aea6ee', '#78ceeb', '#8bd9b8', '#b9b6d3']
const demoDistribution = computed(() => {
  const counts = new Map<string, number>()
  for (const game of gameRows.value) counts.set(game.category, (counts.get(game.category) ?? 0) + 1)
  return Array.from(counts, ([name, value]) => ({ name, value }))
})
const categories = computed(() => {
  const records = isAdminDemo ? demoDistribution.value : distributionRequest.data.value ?? []
  const sorted = records.filter(({ value }) => value > 0).sort((a, b) => b.value - a.value)
  const remaining = sorted.slice(6).reduce((sum, item) => sum + item.value, 0)
  const shown = remaining > 0 ? [...sorted.slice(0, 6), { name: '其余分类', value: remaining }] : sorted
  return shown.map((item, index) => ({ ...item, color: categoryColors[index] ?? '#b9b6d3' }))
})
const distributionTotal = computed(() => categories.value.reduce((sum, item) => sum + item.value, 0))
const distribution = computed<ECOption>(() => ({
  tooltip: { trigger: 'item', renderMode: 'richText', formatter: '{b}  {c} 款（{d}%）' },
  series: [{
    type: 'pie', radius: ['62%', '94%'], label: { show: false },
    itemStyle: { borderColor: '#182749', borderWidth: 1 },
    data: categories.value.map(({ name, value, color }) => ({ name, value, itemStyle: { color } })),
  }],
}))

const latest = computed<DashboardRecentItem[]>(() => {
  if (!isAdminDemo) return recentRequest.data.value ?? []
  const records = latestTab.value?.id === 'games'
    ? gameRows.value.map((game) => ({ id: game.id, name: game.name, secondary: game.company, image: game.cover, date: game.createdAt }))
    : latestTab.value?.id === 'guides'
      ? guideRows.value.map((guide) => ({ id: guide.id, name: guide.title, secondary: guide.author, image: guide.cover, date: guide.createdAt }))
      : userRows.value.map((user) => ({ id: user.id, name: user.name, secondary: user.username, image: user.avatar, date: user.registeredAt }))
  return records.sort((a, b) => Date.parse(b.date) - Date.parse(a.date)).slice(0, 5)
})

function contentLink(type: DashboardContent, id: string | number) {
  return type === 'guides' ? { path: '/admin/guides', query: { id: String(id) } } : `/admin/${type}/${encodeURIComponent(id)}`
}

const linkForReview = '/admin/guides/reviews'
const canReviewGuides = computed(() => hasAdminPermission('guides:read') && hasAdminPermission('guides:review'))
const displayLog = ref(false)
const operationTypes: Record<string, { label: string; color: string }> = {
  games: { label: '游戏', color: '' }, guides: { label: '攻略', color: 'is-pink' }, users: { label: '用户', color: 'is-blue' },
}
const operationHistory = computed(() => {
  const rows: DashboardLog[] = isAdminDemo
    ? demoActivity.value.map((operation, index) => ({
      id: index, time: operation.time, module: operation.collection, target: operation.label,
      action: operation.action, actor: '本地演示', result: '已保存',
    }))
    : logsRequest.data.value ?? []
  return rows.map((operation) => ({
    ...operation,
    type: operationTypes[operation.module] ?? { label: operation.module, color: 'is-orange' },
    timestamp: new Date(operation.time).toLocaleString('zh-CN', {
      month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
    }),
  }))
})

const quickLinks = computed(() => [
  { label: '新增游戏', icon: AdminGameIcon, to: '/admin/games/new', visible: hasAdminPermission('games:read') && hasAdminPermission('games:write') },
  { label: '新建攻略', icon: BookOutlined, to: '/admin/guides/new', visible: hasAdminPermission('guides:read') && hasAdminPermission('guides:write') },
  { label: '新增用户', icon: UserAddOutlined, to: { path: '/admin/users', query: { action: 'new' } }, visible: isAdminDemo },
  { label: '批量导入', icon: UploadOutlined, to: '/admin/games/import', visible: hasAdminPermission('games:read') && hasAdminPermission('games:import') },
  { label: '数据导出', icon: DownloadOutlined, to: { path: '/admin/games', query: { action: 'export' } }, visible: hasAdminPermission('games:read') && hasAdminPermission('games:export') },
  { label: '系统设置', icon: SettingOutlined, to: '/admin/settings', visible: true },
].filter((item) => item.visible))

function statusText(value?: DashboardHealth['api']) {
  if (value === 'up') return '正常'
  if (value === 'down') return '异常'
  return '暂未接入'
}
function percent(value?: number) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 100 ? value : undefined
}
const health = healthRequest.data
const systemMetrics = computed(() => [
  { title: '存储空间', value: percent(health.value?.storage), icon: HddOutlined },
  { title: 'CPU 使用率', value: percent(health.value?.cpu), icon: AppstoreOutlined },
  { title: '内存使用率', value: percent(health.value?.memory), icon: AlertOutlined },
])
const firstError = computed(() =>
  [summaryRequest, trendRequest, distributionRequest, recentRequest, logsRequest, healthRequest]
    .map(({ error }) => error.value).find(Boolean),
)
</script>

<template>
  <div v-if="firstError" class="admin-panel dashboard-panel dashboard-error" role="alert">
    <span>部分后台数据加载失败。{{ firstError }}</span>
    <button class="admin-button" type="button" @click="revision++">重试</button>
  </div>
  <AdminStats :items="totals" />

  <div class="dashboard-grid">
    <div class="dashboard-main">
      <div class="dashboard-charts">
        <section id="platform-trends" class="admin-panel dashboard-panel">
          <header class="dashboard-heading">
            <h2><BarChartOutlined /> 平台数据趋势</h2>
            <div class="dashboard-segments" aria-label="趋势区间">
              <button v-for="days in adminDashboardPeriods" :key="days" type="button" :aria-pressed="period === days" @click="period = days">近{{ days }}天</button>
            </div>
          </header>
          <AdminChart v-if="trends.length" :option="trendOption" :label="`近${period}天新增游戏、攻略趋势`" />
          <p v-else class="admin-empty">{{ trendRequest.loading.value ? '正在加载趋势…' : trendRequest.error.value || '暂无趋势数据' }}</p>
        </section>

        <section class="admin-panel dashboard-panel">
          <header class="dashboard-heading"><h2><PieChartOutlined /> 游戏分类分布</h2></header>
          <div v-if="categories.length" class="distribution-content">
            <div class="distribution-ring">
              <AdminChart :option="distribution" label="游戏分类环形图" />
              <span class="distribution-center"><strong>{{ distributionTotal }}</strong><small>游戏总数</small></span>
            </div>
            <ul class="distribution-legend">
              <li v-for="category in categories" :key="category.name">
                <i :style="{ backgroundColor: category.color }"></i><span>{{ category.name }}</span>
                <strong>{{ (category.value / distributionTotal * 100).toFixed(1) }}%</strong>
              </li>
            </ul>
          </div>
          <p v-else class="admin-empty">{{ distributionRequest.loading.value ? '正在加载分类…' : distributionRequest.error.value || '暂无游戏分类数据' }}</p>
        </section>
      </div>

      <div class="dashboard-updates">
        <section class="admin-panel dashboard-panel">
          <header class="dashboard-heading">
            <h2><FireOutlined /> 最新内容</h2>
            <div class="dashboard-segments" aria-label="内容类型">
              <button v-for="tab in latestTabs" :key="tab.id" type="button" :aria-pressed="latestTab?.id === tab.id" @click="selectedLatestTab = tab.id">{{ tab.label }}</button>
            </div>
          </header>
          <ul v-if="latestTab && latest.length" class="latest-list">
            <li v-for="item in latest" :key="item.id">
              <RouterLink :to="contentLink(latestTab.id, item.id)">
                <span class="latest-image" :class="{ 'latest-avatar': latestTab.id === 'users' }" aria-hidden="true">
                  <component :is="latestTab.icon" />
                  <img v-if="item.image" :key="item.image" :src="item.image" alt="" loading="lazy" @error="($event.target as HTMLImageElement).hidden = true" />
                </span>
                <span class="latest-copy"><strong>{{ item.name }}</strong><small>{{ item.secondary }}</small></span>
                <time :datetime="item.date" class="latest-date">{{ item.date.slice(5, 10) }}</time>
              </RouterLink>
            </li>
          </ul>
          <p v-else class="admin-empty">{{ recentRequest.loading.value ? '正在加载最新内容…' : recentRequest.error.value || '暂无可查看的最新内容' }}</p>
        </section>

        <section class="admin-panel dashboard-panel">
          <header class="dashboard-heading">
            <h2><HistoryOutlined /> 近期操作日志</h2>
            <button v-if="operationHistory.length" class="dashboard-text-button" type="button" @click="displayLog = true">查看更多 <RightOutlined /></button>
          </header>
          <ul v-if="operationHistory.length" class="operation-list">
            <li v-for="entry in operationHistory.slice(0, 5)" :key="entry.id">
              <span class="admin-tag" :class="entry.type.color">{{ entry.type.label }}</span>
              <span>{{ entry.actor }} · {{ entry.action }} {{ entry.target }}<small>{{ entry.result }}</small></span>
              <time :datetime="entry.time">{{ entry.timestamp }}</time>
            </li>
          </ul>
          <p v-else class="activity-empty"><ClockCircleOutlined /> {{ logsRequest.loading.value ? '正在加载操作日志…' : logsRequest.error.value || '暂无操作记录' }}</p>
          <RouterLink v-if="canReviewGuides" class="review-link" :to="linkForReview">
            <span><FileTextOutlined /> 待审核攻略</span>
            <strong>{{ summary?.pendingGuides === undefined ? '' : `${summary.pendingGuides} 篇` }} <RightOutlined /></strong>
          </RouterLink>
          <ul v-if="isAdminDemo && !operationHistory.length && demoPendingGuides.length" class="operation-list pending-preview">
            <li v-for="guide in demoPendingGuides.slice(0, 3)" :key="guide.id">
              <span class="admin-tag is-orange">待审核</span>
              <RouterLink :to="{ path: linkForReview, query: { id: guide.id } }">{{ guide.title }}</RouterLink>
              <small>{{ guide.author }}</small>
            </li>
          </ul>
        </section>
      </div>
    </div>

    <aside class="dashboard-aside">
      <section class="admin-panel dashboard-panel">
        <header class="dashboard-heading"><h2><ThunderboltOutlined /> 快捷操作</h2></header>
        <div class="dashboard-shortcuts">
          <RouterLink v-for="item in quickLinks" :key="item.label" :to="item.to"><component :is="item.icon" /><span>{{ item.label }}</span></RouterLink>
        </div>
      </section>

      <section class="admin-panel dashboard-panel">
        <header class="dashboard-heading"><h2><CloudServerOutlined /> 系统运行状态</h2></header>
        <ul class="system-list">
          <li><CloudServerOutlined /><strong>服务状态</strong><span class="system-state" :class="{ 'is-online': health?.api === 'up' }">{{ statusText(health?.api) }}</span></li>
          <li><DatabaseOutlined /><strong>数据库</strong><span class="system-state" :class="{ 'is-online': health?.database === 'up' }">{{ statusText(health?.database) }}</span></li>
          <li v-for="item in systemMetrics" :key="item.title">
            <component :is="item.icon" /><strong>{{ item.title }}</strong>
            <div v-if="item.value !== undefined" class="system-progress">
              <span><b>{{ item.value }}%</b></span>
              <progress :value="item.value" max="100" :aria-label="item.title" />
            </div>
            <span v-else class="system-state">暂未接入</span>
          </li>
        </ul>
        <p v-if="healthRequest.loading.value || healthRequest.error.value" class="system-caption">{{ healthRequest.loading.value ? '正在查询运行状态…' : healthRequest.error.value }}</p>
      </section>
    </aside>
  </div>

  <AdminDialog v-if="displayLog" title="操作日志" @close="displayLog = false">
    <ul v-if="operationHistory.length" class="operation-list">
      <li v-for="entry in operationHistory" :key="entry.id">
        <span class="admin-tag" :class="entry.type.color">{{ entry.type.label }}</span>
        <span>{{ entry.actor }} · {{ entry.action }} {{ entry.target }}<small>{{ entry.result }}</small></span>
        <time :datetime="entry.time">{{ entry.timestamp }}</time>
      </li>
    </ul>
    <p v-else class="admin-empty">暂无操作记录</p>
  </AdminDialog>
</template>

<style scoped>
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 2.56fr) minmax(248px, 1fr); gap: 13px; }
.dashboard-main, .dashboard-aside { display: grid; min-width: 0; gap: 13px; align-content: start; }
.dashboard-charts, .dashboard-updates { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: 13px; }
.dashboard-updates { grid-template-columns: minmax(0, 1.13fr) minmax(0, 1fr); }
.dashboard-panel { padding: 11px clamp(11px, 1.1vw, 18px) 12px; }
.dashboard-error { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 13px; }
#platform-trends { scroll-margin-top: 69px; }
.dashboard-heading { min-height: 31px; margin-bottom: 10px; display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.dashboard-heading h2 { display: flex; align-items: center; gap: 9px; margin: 0; font-size: clamp(15px, 1.19vw, 20px); font-weight: 730; }
.dashboard-heading h2 .anticon { color: var(--gw-accent); font-size: 1.19em; }
.dashboard-segments { display: flex; border: 1px solid var(--admin-line); border-radius: 8px; white-space: nowrap; }
.dashboard-segments button { padding: 4px clamp(7px, .8vw, 13px); border: 0; border-radius: 7px; color: var(--admin-text); background: transparent; cursor: pointer; font-size: 12px; }
.dashboard-segments button:hover { background: var(--gw-accent-tint); }
.dashboard-segments button[aria-pressed='true'] { color: var(--gw-nav); background: var(--gw-accent); }
.distribution-content { min-height: 197px; display: grid; grid-template-columns: minmax(84px, 1fr) minmax(105px, 1fr); gap: 5px; align-items: center; }
.distribution-ring { position: relative; min-width: 0; }
.distribution-center { position: absolute; inset: 0; display: grid; align-content: center; justify-items: center; font-size: 20px; line-height: 1.15; pointer-events: none; }
.distribution-center small { font-size: 11px; margin-top: 4px; }
.distribution-legend, .latest-list, .operation-list, .system-list { margin: 0; padding: 0; list-style: none; }
.distribution-legend { display: grid; gap: 8px; font-size: 12px; }
.distribution-legend li { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 5px; }
.distribution-legend i { width: 10px; height: 10px; border-radius: 50%; }
.distribution-legend strong { font-weight: 620; }
.latest-list li + li, .operation-list li + li, .system-list li + li { border-top: 1px solid var(--gw-border); }
.latest-list a { display: flex; align-items: center; gap: 12px; min-height: 47px; padding: 3px 1px; }
.latest-list a:hover strong { color: var(--admin-accent); }
.latest-image { position: relative; display: grid; place-items: center; flex: 0 0 auto; width: clamp(66px, 7vw, 123px); height: 40px; overflow: hidden; border-radius: 6px; color: var(--gw-subtle); background: var(--gw-surface-hover); font-size: 22px; }
.latest-image img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.latest-image.latest-avatar { width: 40px; border-radius: 50%; }
.latest-copy { min-width: 0; }
.latest-list strong, .latest-list small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; line-height: 1.35; }
.latest-list small, .operation-list :is(small, time) { font-size: 12px; color: var(--admin-muted); }
.latest-date { margin-left: auto; flex: 0 0 auto; font-size: 12px; color: var(--admin-muted); }
.operation-list li { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 9px; align-items: center; min-height: 43px; }
.operation-list .admin-tag { padding: 3px 8px; }
.operation-list small { white-space: nowrap; }
.operation-list span > small { display: block; }
.dashboard-text-button { color: var(--admin-accent); border: 0; padding: 4px 0; background: transparent; font-weight: 650; cursor: pointer; white-space: nowrap; }
.dashboard-text-button:hover { text-decoration: underline; }
.activity-empty { padding: 20px 0; margin: 0; color: var(--admin-muted); }
.review-link { display: flex; justify-content: space-between; gap: 8px; margin-top: 5px; padding: 10px 8px; border-radius: 8px; background: var(--gw-surface-hover); color: var(--admin-accent); }
.review-link:hover { background: var(--gw-accent-tint); }
.pending-preview { margin-top: 7px; }
.pending-preview a:hover { color: var(--admin-accent); text-decoration: underline; }
.dashboard-shortcuts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.dashboard-shortcuts a { display: grid; place-items: center; align-content: center; min-height: 69px; gap: 3px; border: 1px solid var(--admin-line); border-radius: 10px; font-weight: 650; text-align: center; }
.dashboard-shortcuts a:hover { background: var(--gw-surface-hover); }
.dashboard-shortcuts a > :is(.anticon, svg) { font-size: 25px; color: var(--gw-accent); }
.system-list li { min-height: 48px; display: grid; grid-template-columns: auto auto minmax(0, 1fr); gap: 10px; align-items: center; }
.system-list li > .anticon { font-size: 22px; color: var(--gw-accent); }
.system-list li > strong { white-space: nowrap; }
.system-state { justify-self: end; color: var(--admin-muted); white-space: nowrap; }
.system-state::before { display: inline-block; content: ''; width: 8px; height: 8px; border-radius: 50%; background: var(--gw-subtle); margin-right: 7px; }
.system-state.is-online { color: var(--gw-success); }
.system-state.is-online::before { background: var(--gw-success); }
.system-progress { display: grid; gap: 3px; padding-block: 5px; }
.system-progress span { display: flex; justify-content: space-between; color: var(--admin-muted); font-size: 12px; }
.system-progress b { margin-left: auto; font-weight: 600; }
.system-progress progress { width: 100%; height: 8px; border: 0; appearance: none; }
.system-progress progress::-webkit-progress-bar { background: var(--gw-border); border-radius: 9px; }
.system-progress progress::-webkit-progress-value { background: var(--gw-accent); border-radius: 9px; }
.system-progress progress::-moz-progress-bar { background: var(--gw-accent); border-radius: 9px; }
.system-caption { margin: 7px 0 0; color: var(--admin-muted); font-size: 11px; }
@media (max-width: 1190px) { .dashboard-grid { grid-template-columns: minmax(0, 1fr); } .dashboard-aside { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
