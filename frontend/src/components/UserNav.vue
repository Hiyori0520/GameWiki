<template>
  <header class="user-nav" :class="{ 'has-search': showSearch }">
    <div class="nav-left">
      <RouterLink class="brand" to="/home">GAMEWIKI</RouterLink>
      <nav class="links" aria-label="主导航">
        <RouterLink :class="{ active: isActive('/home') }" to="/home">首页</RouterLink>
        <RouterLink :class="{ active: isActive('/games') }" to="/games">游戏库</RouterLink>
        <RouterLink :class="{ active: isActive('/ai/chat') }" to="/ai/chat">AI 助手</RouterLink>
        <RouterLink :class="{ active: isActive('/profile') }" to="/profile">个人中心</RouterLink>
      </nav>
    </div>

    <form v-if="showSearch" class="nav-search" role="search" aria-label="搜索游戏库" @submit.prevent="search">
      <SearchOutlined class="search-icon" aria-hidden="true" />
      <input v-model="keyword" type="search" aria-label="游戏搜索关键词" placeholder="搜索游戏、关键词…" />
      <button type="submit">搜索</button>
    </form>

    <div class="nav-right">
      <template v-if="user.nickname">
        <span class="avatar">{{ initial }}</span>
        <span class="nickname">{{ user.nickname }}</span>
        <span class="role" :class="isAdmin ? 'admin' : 'player'">{{ isAdmin ? '管理员' : '玩家' }}</span>
        <RouterLink v-if="isAdmin" class="admin-entry" to="/admin">管理后台</RouterLink>
        <button class="logout" type="button" @click="handleLogout">退出</button>
      </template>
      <template v-else>
        <RouterLink to="/auth">登录</RouterLink>
      </template>
    </div>
  </header>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SearchOutlined } from '@ant-design/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

defineProps({ showSearch: Boolean })

const router = useRouter()
const route = useRoute()
const keyword = ref('')

const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('kb_user') || '{}')
  } catch (e) {
    return {}
  }
})
const isAdmin = computed(() => user.value.role === 'ADMIN')
const initial = computed(() => (user.value.nickname || '游').slice(0, 1))

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')
function search() {
  const term = keyword.value.trim()
  router.push({ path: '/games', query: term ? { keyword: term } : {} })
}

const handleLogout = async () => {
  try {
    await request.post('/api/user/logout')
  } catch (e) {
    // 网络异常也要让用户退出，不阻塞
  }
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  ElMessage.success('已退出登录')
  router.replace('/auth')
}
</script>

<style scoped>
.user-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 32px;
  background: var(--gw-nav);
  border-bottom: 1px solid var(--gw-border);
  backdrop-filter: blur(8px);
}
.nav-left {
  display: flex;
  align-items: center;
  gap: 28px;
}
.brand {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--gw-link);
  text-decoration: none;
  white-space: nowrap;
}
.links {
  display: flex;
  gap: 20px;
}
.links a {
  font-size: 14px;
  color: var(--gw-muted);
  text-decoration: none;
  white-space: nowrap;
  padding-bottom: 2px;
  border-bottom: 2px solid transparent;
  transition: color 0.15s;
}
.links a:hover {
  color: var(--gw-text);
}
.links a.active {
  color: var(--gw-text);
  border-bottom-color: var(--gw-accent);
}
.nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--gw-muted);
}
.avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gw-action-gradient);
  color: #fff;
  font-size: 13px;
}
.nickname {
  color: var(--gw-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.role {
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.role.admin {
  color: var(--gw-warning);
  background: var(--gw-warning-tint);
}
.role.player {
  color: var(--gw-success);
  background: var(--gw-success-tint);
}
.nav-right a,
.logout {
  color: var(--gw-link);
  text-decoration: none;
  white-space: nowrap;
}
.nav-right a:hover,
.logout:hover {
  color: var(--gw-text);
}
.admin-entry {
  padding: 0 4px;
}
.logout {
  padding: 0;
  border: 0;
  background: none;
  color: var(--gw-muted);
  font: inherit;
  cursor: pointer;
}

.user-nav.has-search {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px clamp(12px, 2vw, 26px);
  min-height: 60px;
  height: auto;
  padding: 8px var(--home-gutter, 24px);
}
.nav-search {
  display: flex;
  align-items: center;
  justify-self: center;
  width: min(100%, 26rem);
  min-height: 38px;
  box-sizing: border-box;
  padding: 3px;
  border: 1px solid var(--gw-border);
  border-radius: 10px;
  color: var(--gw-muted);
  background: var(--gw-surface);
  color-scheme: dark;
}
.nav-search:focus-within {
  border-color: var(--gw-accent);
}
.search-icon {
  flex-shrink: 0;
  margin-left: 9px;
}
.nav-search input {
  align-self: stretch;
  flex: 1;
  min-width: 0;
  padding: 0 8px;
  border: 0;
  border-radius: 5px;
  background: none;
  color: var(--gw-text);
  font: inherit;
  font-size: 14px;
}
.nav-search input::placeholder {
  color: var(--gw-muted);
  opacity: 1;
}
.nav-search button {
  min-height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 7px;
  background: var(--gw-primary);
  color: #fff;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}
.nav-search button:hover {
  background: var(--gw-primary-hover);
}
.user-nav :is(a, button, input):focus-visible {
  outline: 2px solid var(--gw-accent);
  outline-offset: 2px;
}
.nav-search input:focus-visible {
  outline-offset: -2px;
}

@media (max-width: 1050px) {
  .user-nav.has-search {
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .has-search .nav-search {
    grid-area: 2 / 1 / 3 / -1;
    justify-self: stretch;
    width: 100%;
  }
  .has-search .nav-right {
    grid-area: 1 / 2;
  }
}

@media (max-width: 760px) {
  .has-search .nav-left {
    display: contents;
  }
  .has-search .links {
    grid-area: 2 / 1 / 3 / -1;
    justify-content: space-between;
    gap: 8px;
  }
  .has-search .links a {
    display: flex;
    align-items: center;
    min-height: 30px;
    font-size: 13px;
  }
  .has-search .nav-search {
    grid-row: 3;
  }
  .has-search .nav-right {
    gap: 6px;
    min-width: 0;
  }
  .has-search .nickname {
    max-width: 70px;
  }
}

@media (max-width: 420px) {
  .has-search .brand {
    font-size: 15px;
  }
  .has-search .role {
    display: none;
  }
  .has-search .nickname {
    max-width: 54px;
  }
}
</style>
