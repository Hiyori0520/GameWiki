<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined, BellOutlined, BookOutlined, DownOutlined,
  FullscreenExitOutlined, FullscreenOutlined, HomeOutlined, LogoutOutlined, MenuOutlined, SearchOutlined,
  SettingOutlined, UserOutlined,
} from '@ant-design/icons-vue'
import adminAvatar from '../assets/admin/avatar-admin.jpg'
import AdminGameIcon from '../components/admin/AdminGameIcon.vue'
import { adminModules } from '../admin/modules'
import { adminPreferences } from '../admin/preferences'
import { adminSession, adminSessionExpired, hasAdminPermission, isAdminDemo } from '../api/admin/client'
import request from '../utils/request'
import './admin.css'

const route = useRoute()
const router = useRouter()
const sectionBreadcrumbs = {
  dashboard: [],
  games: [
    { label: '游戏列表', to: '/admin/games' },
    { label: '添加游戏', to: '/admin/games/new' },
    { label: '游戏分类', to: { path: '/admin/games', query: { focus: 'category' } } },
    { label: '游戏标签', to: { path: '/admin/games', query: { focus: 'tag' } } },
    { label: '平台筛选', to: { path: '/admin/games', query: { focus: 'platform' } } },
  ],
  guides: [
    { label: '攻略列表', to: '/admin/guides' },
    { label: '攻略分类', to: '/admin/guides/categories' },
    { label: '攻略标签', to: { path: '/admin/guides', query: { focus: 'tag' } } },
    { label: '攻略审核', to: '/admin/guides/reviews' },
  ],
  users: [
    { label: '用户列表', to: '/admin/users' },
    { label: '用户等级', to: '/admin/users/levels' },
    { label: '权限角色', to: { path: '/admin/users', query: { focus: 'role' } } },
  ],
  settings: [],
  profile: [],
} as const

const settingsSection = { id: 'settings', label: '系统设置', path: '/admin/settings', icon: SettingOutlined } as const
const profileSection = { id: 'profile', label: '管理员信息', path: '/admin/profile', icon: UserOutlined } as const
const sections = [...adminModules, settingsSection, profileSection]
const descriptions = {
  dashboard: '你好，管理员！欢迎回到 GameWiki 后台管理系统。',
  games: '管理网站中的游戏信息，包括封面、分类、标签、平台与发布状态。',
  guides: '管理网站中的游戏攻略、流程指南、角色路线与成就收集等内容。',
  users: '管理与维护平台用户，查看用户信息、等级、角色与账号状态。',
  settings: '设置后台侧栏、管理列表和总览图表的默认显示方式。',
  profile: '查看当前管理员账号和可访问的后台模块。',
}

const section = computed(() => sections.find((item) => item.id === route.meta.adminSection) ?? adminModules[0])
const visibleNavigation = computed(() => adminModules.filter((item) => hasAdminPermission(`${item.id}:read`)))
const searchTargets = computed(() => visibleNavigation.value.filter((item) => item.id !== 'dashboard'))
const home = computed(() => visibleNavigation.value[0]?.path ?? '/')
const canReviewGuides = computed(() => hasAdminPermission('guides:read') && hasAdminPermission('guides:review'))
const isDashboard = computed(() => section.value.id === 'dashboard')
const title = computed(() => typeof route.meta.adminPage === 'string' ? route.meta.adminPage : section.value.label)
const leaf = computed(() => {
  const breadcrumbs = sectionBreadcrumbs[section.value.id]
  return route.meta.adminPage ?? breadcrumbs.find((item) => router.resolve(item.to).fullPath === route.fullPath)?.label ?? breadcrumbs[0]?.label
})
const search = ref('')
const popup = ref<'search' | 'notifications' | 'account' | null>(null)
const compact = ref(adminPreferences.value.sidebarCollapsed)
const fullscreen = ref(false)
const fullscreenError = ref(false)
const signingOut = ref(false)

watch(() => adminPreferences.value.sidebarCollapsed, (collapsed) => { compact.value = collapsed })

watch(() => route.fullPath, () => {
  search.value = typeof route.query.q === 'string' ? route.query.q : ''
  popup.value = null
}, { immediate: true })

watch(adminSessionExpired, async (expired) => {
  if (!expired) return
  const next = route.fullPath
  await nextTick()
  if (adminSessionExpired.value && route.fullPath === next && route.meta.admin && route.name !== 'admin-access') {
    void router.replace({ name: 'admin-access', query: { reason: '401', next } })
  }
})

function updateFullscreen() {
  fullscreen.value = Boolean(document.fullscreenElement)
}
onMounted(() => {
  updateFullscreen()
  document.addEventListener('fullscreenchange', updateFullscreen)
})
onUnmounted(() => document.removeEventListener('fullscreenchange', updateFullscreen))

function searchIn(path: string) {
  const q = search.value.trim()
  if (!q) return
  popup.value = null
  void router.push({ path, query: { q } })
}

function submitSearch() {
  const target = searchTargets.value.find((item) => item.id === section.value.id)?.path ?? searchTargets.value[0]?.path
  if (target) searchIn(target)
}

function closePopupOnBlur(event: FocusEvent, kind: typeof popup.value) {
  if (popup.value !== kind || !(event.currentTarget instanceof HTMLElement)) return
  if (!event.currentTarget.contains(event.relatedTarget as Node | null)) popup.value = null
}

async function returnToLogin() {
  signingOut.value = true
  try {
    await request.post('/api/user/logout', undefined, { timeout: 5000 })
  } catch {
    // 后端暂不可用时，仍允许退出本机登录。
  } finally {
    localStorage.removeItem('kb_token')
    localStorage.removeItem('kb_user')
    window.location.replace('/login')
  }
}

async function toggleFullscreen() {
  fullscreenError.value = false
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await document.documentElement.requestFullscreen()
  } catch {
    fullscreenError.value = true
  }
}
</script>

<template>
  <div class="admin-shell" :class="{ 'is-compact': compact }" @keydown.esc="popup = null">
    <aside id="admin-sidebar" class="admin-sidebar">
      <RouterLink class="admin-brand" :to="home" aria-label="GameWiki 后台首页">
        <AdminGameIcon />
        <span><strong>GameWiki</strong><small>后台管理系统</small></span>
      </RouterLink>
      <nav class="admin-nav" aria-label="管理后台">
        <RouterLink v-for="item in visibleNavigation" :key="item.id" class="nav-row"
          :class="{ 'is-active': section.id === item.id }" :to="item.path" :title="item.label" :aria-label="item.label">
          <component :is="item.icon" class="nav-icon" /><span>{{ item.label }}</span>
        </RouterLink>
        <RouterLink class="nav-extra" :class="{ 'is-active': section.id === settingsSection.id }"
          :to="settingsSection.path" :title="settingsSection.label" :aria-label="settingsSection.label">
          <SettingOutlined class="nav-icon" /><span>{{ settingsSection.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <div class="admin-workspace">
      <header class="admin-topbar">
        <button class="admin-icon-button top-menu" type="button" aria-label="切换侧边导航" aria-controls="admin-sidebar" :aria-expanded="!compact" @click="compact = !compact"><MenuOutlined /></button>
        <nav class="admin-breadcrumb" aria-label="面包屑">
          <template v-if="!isDashboard">
            <RouterLink :to="home">首页</RouterLink><span aria-hidden="true">/</span>
            <RouterLink :to="section.path">{{ section.label }}</RouterLink>
            <template v-if="leaf"><span aria-hidden="true">/</span><strong aria-current="page">{{ leaf }}</strong></template>
          </template>
          <strong v-else aria-current="page">后台总览</strong>
        </nav>

        <form v-if="searchTargets.length" class="top-search top-popover-anchor" role="search" @submit.prevent="submitSearch" @focusout="closePopupOnBlur($event, 'search')">
          <SearchOutlined aria-hidden="true" />
          <input v-model="search" type="search" aria-label="全局搜索" placeholder="搜索游戏、攻略、用户…" @focus="popup = 'search'" @input="popup = 'search'" />
          <div v-if="popup === 'search' && search.trim()" class="admin-top-popover search-results">
            <button v-for="target in searchTargets" :key="target.id" type="button" @click="searchIn(target.path)"><component :is="target.icon" /> 在{{ target.label }}中搜索</button>
          </div>
        </form>
        <div v-if="canReviewGuides" class="top-popover-anchor top-notifications" @focusout="closePopupOnBlur($event, 'notifications')">
          <button class="admin-icon-button top-notification" type="button" aria-label="待办通知" :aria-expanded="popup === 'notifications'" @click="popup = popup === 'notifications' ? null : 'notifications'"><BellOutlined /><i aria-hidden="true"></i></button>
          <div v-if="popup === 'notifications'" class="admin-top-popover">
            <strong>待办通知</strong>
            <RouterLink to="/admin/guides/reviews"><BookOutlined /> 查看待审核攻略</RouterLink>
          </div>
        </div>
        <button class="admin-icon-button top-fullscreen" type="button" :aria-label="fullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen"><FullscreenExitOutlined v-if="fullscreen" /><FullscreenOutlined v-else /></button>
        <span v-if="fullscreenError" class="admin-announce" role="status">此浏览器暂时无法切换全屏。</span>
        <div class="top-popover-anchor" @focusout="closePopupOnBlur($event, 'account')">
          <button class="top-account" type="button" aria-label="管理员菜单" :aria-expanded="popup === 'account'" @click="popup = popup === 'account' ? null : 'account'">
            <img :src="adminAvatar" alt="" /><span>{{ adminSession?.name || '管理员' }}</span><DownOutlined />
          </button>
          <div v-if="popup === 'account'" class="admin-top-popover">
            <RouterLink :to="profileSection.path" @click="popup = null">
              <UserOutlined aria-hidden="true" /> {{ profileSection.label }}
            </RouterLink>
            <RouterLink to="/home"><HomeOutlined aria-hidden="true" /> 返回 GameWiki 首页</RouterLink>
            <button type="button" :disabled="signingOut" @click="returnToLogin">
              <LogoutOutlined aria-hidden="true" /> {{ signingOut ? '正在退出…' : '返回登录' }}
            </button>
          </div>
        </div>
      </header>

      <main id="admin-main">
        <section class="admin-hero" aria-labelledby="admin-page-title">
          <h1 id="admin-page-title"><component :is="isDashboard ? AdminGameIcon : section.icon" aria-hidden="true" />{{ title }}</h1>
          <p>{{ descriptions[section.id] }}</p>
        </section>
        <div class="admin-content">
          <aside v-if="isAdminDemo" class="admin-demo-notice"><AppstoreOutlined /> 开发演示 · 业务接口待联调 · 数据仅保存在当前浏览器</aside>
          <RouterView v-if="isAdminDemo || adminSession" />
        </div>
      </main>
    </div>
  </div>
</template>
