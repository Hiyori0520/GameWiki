import { createRouter, createWebHistory } from 'vue-router'

const gameManage = () => import('../views/admin/GameManage.vue')

// 无需登录即可访问的页面
const PUBLIC_PATHS = ['/login', '/auth']

const routes = [
  // ===== 用户端（玩家）=====
  {
    path: '/home',
    name: 'Home',
    component: () => import('../views/user/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/games',
    name: 'GameList',
    component: () => import('../views/user/GameList.vue'),
    meta: { title: '游戏库' }
  },
  {
    path: '/auth',
    name: 'Auth',
    component: () => import('../views/user/Auth.vue'),
    meta: { title: '登录注册' }
  },
  {
    path: '/ai/chat',
    name: 'UserAiChat',
    component: () => import('../views/user/AiChat.vue'),
    meta: { title: 'AI 攻略助手' }
  },
  {
    path: '/games/:id',
    name: 'GameDetail',
    component: () => import('../views/user/GameDetail.vue'),
    meta: { title: '游戏详情' }
  },
  {
    path: '/guides/:id',
    name: 'GuideDetail',
    component: () => import('../views/user/GuideDetail.vue'),
    meta: { title: '攻略详情' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/user/Profile.vue'),
    meta: { title: '个人中心' }
  },

  // ===== 旧版管理端（保留，逐步被 /admin 取代）=====
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    // 根路径先鉴权再分流：未登录去登录页，管理员进后台，普通玩家进首页
    redirect: () => {
      const token = localStorage.getItem('kb_token')
      if (!token) return '/auth'
      try {
        const user = JSON.parse(localStorage.getItem('kb_user') || '{}')
        return user.role === 'ADMIN' ? '/admin' : '/home'
      } catch (e) {
        return '/home'
      }
    },
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台与统计' } },
      { path: 'guides', name: 'Guides', component: () => import('../views/GuideManage.vue'), meta: { title: '攻略知识库管理' } },
      { path: 'basic', name: 'Basic', component: () => import('../views/BasicData.vue'), meta: { title: '基础数据字典' } },
      { path: 'ai-chat', name: 'AiChat', component: () => import('../views/AiAdvisor.vue'), meta: { title: 'AI 战术顾问' } }
    ]
  },

  // ===== 新版管理端（D 的四模块）=====
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { admin: true },
    children: [
      { path: '', redirect: { name: 'admin-dashboard' } },
      { path: 'dashboard', name: 'admin-dashboard', meta: { adminSection: 'dashboard', adminPermission: 'dashboard:read' }, component: () => import('../views/admin/Dashboard.vue') },
      { path: 'games', name: 'admin-games', meta: { adminSection: 'games', adminPermission: 'games:read' }, component: gameManage },
      { path: 'games/new', name: 'admin-game-new', meta: { adminSection: 'games', adminPage: '新增游戏', adminPermission: 'games:write' }, component: gameManage },
      { path: 'games/import', name: 'admin-game-import', meta: { adminSection: 'games', adminPage: '导入游戏', adminPermission: 'games:import' }, component: gameManage },
      { path: 'games/:id/edit', name: 'admin-game-edit', meta: { adminSection: 'games', adminPage: '编辑游戏', adminPermission: 'games:write' }, component: gameManage },
      { path: 'games/:id', name: 'admin-game-detail', meta: { adminSection: 'games', adminPage: '游戏详情', adminPermission: 'games:read' }, component: gameManage },
      { path: 'guides', name: 'admin-guides', meta: { adminSection: 'guides', adminPermission: 'guides:read' }, component: () => import('../views/admin/GuideManage.vue') },
      { path: 'guides/new', name: 'admin-guide-new', meta: { adminSection: 'guides', adminPage: '新建攻略', adminPermission: 'guides:write' }, component: () => import('../views/admin/GuideEditor.vue') },
      { path: 'guides/:id/edit', name: 'admin-guide-edit', meta: { adminSection: 'guides', adminPage: '编辑攻略', adminPermission: 'guides:write' }, component: () => import('../views/admin/GuideEditor.vue') },
      { path: 'guides/reviews', name: 'admin-guide-reviews', meta: { adminSection: 'guides', adminPage: '攻略审核', adminPermission: 'guides:review' }, component: () => import('../views/admin/GuideManage.vue') },
      { path: 'guides/categories', name: 'admin-guide-categories', meta: { adminSection: 'guides', adminPage: '攻略分类', adminPermission: 'guides:categories' }, component: () => import('../views/admin/GuideManage.vue') },
      { path: 'users', name: 'admin-users', meta: { adminSection: 'users', adminPermission: 'users:read' }, component: () => import('../views/admin/UserManage.vue') },
      { path: 'users/levels', name: 'admin-user-levels', meta: { adminSection: 'users', adminPage: '用户等级', adminPermission: 'users:read' }, component: () => import('../views/admin/UserManage.vue') },
      { path: 'users/:id', name: 'admin-user-detail', meta: { adminSection: 'users', adminPage: '用户详情', adminPermission: 'users:read' }, component: () => import('../views/admin/UserManage.vue') },
      { path: 'settings', name: 'admin-settings', meta: { adminSection: 'settings' }, component: () => import('../views/admin/SystemSettings.vue') },
      { path: 'profile', name: 'admin-profile', meta: { adminSection: 'profile' }, component: () => import('../views/admin/AdminProfile.vue') },
    ],
  },
  { path: '/admin/access', name: 'admin-access', meta: { admin: true }, component: () => import('../views/admin/AdminAccess.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, savedPosition) {
    if (!to.meta.admin) return
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash }
    if (to.path !== from.path) return { top: 0 }
  },
  routes
})

// 未登录时：用户端页面跳 /auth，管理端页面跳 /login
const isUserRoute = (path) =>
  ['/home', '/ai', '/games', '/guides', '/profile'].some((p) => path === p || path.startsWith(p + '/'))

router.beforeEach((to, from, next) => {
  if (PUBLIC_PATHS.includes(to.path)) {
    next()
    return
  }
  const token = localStorage.getItem('kb_token')
  if (!token) {
    next(isUserRoute(to.path) ? '/auth' : '/login')
  } else {
    next()
  }
})

// 管理端权限校验（D 模块）：无权限跳 /admin/access
router.beforeEach(async (to) => {
  if (!to.meta.admin || to.name === 'admin-access') return
  const { adminErrorStatus, hasAdminPermission, loadAdminSession } = await import('../api/admin/client')
  let reason = '403'
  try {
    await loadAdminSession()
    const { loadAdminPreferences } = await import('../admin/preferences')
    await loadAdminPreferences().catch(() => {})
    // These pages use the current admin session or account preferences only.
    if (to.name === 'admin-settings' || to.name === 'admin-profile') return
    if (typeof to.meta.adminSection === 'string' && hasAdminPermission(`${to.meta.adminSection}:read`) &&
      to.meta.adminPermission && hasAdminPermission(to.meta.adminPermission)) return
  } catch (error) {
    const status = adminErrorStatus(error)
    reason = status === 401 || status === 403 ? String(status) : 'unavailable'
  }
  return { name: 'admin-access', query: { reason, next: to.fullPath }, replace: true }
})

export default router
