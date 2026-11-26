<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background-color: var(--gw-nav); color: #fff">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; font-size: 16px; font-weight: bold; border-bottom: 1px solid var(--gw-line); letter-spacing: 1px">
        <el-icon style="margin-right: 8px; color: var(--gw-accent)" size="20"><Cpu /></el-icon>
        AI 游戏攻略知识库
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="legacy-nav"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>工作台与统计</span>
        </el-menu-item>
        <el-menu-item index="/guides">
          <el-icon><Document /></el-icon>
          <span>攻略知识库管理</span>
        </el-menu-item>
        <el-menu-item index="/basic">
          <el-icon><Coin /></el-icon>
          <span>基础数据管理</span>
        </el-menu-item>
        <el-menu-item index="/ai-chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 战术顾问助手</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background: var(--gw-nav); border-bottom: 1px solid var(--gw-line); display: flex; justify-content: space-between; align-items: center; padding: 0 24px">
        <span style="font-size: 16px; font-weight: 600; color: var(--gw-text)">{{ $route.meta.title }}</span>
        <div style="display: flex; align-items: center; gap: 14px">
          <el-tag :type="userInfo.role === 'ADMIN' ? 'danger' : 'success'" effect="light">
            {{ userInfo.role === 'ADMIN' ? '管理员' : '认证玩家' }}
          </el-tag>
          <span style="color: var(--gw-muted); font-size: 14px">{{ userInfo.nickname || '指挥官' }}</span>
          <el-button type="info" link @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main style="padding: 20px; overflow-y: auto">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const userInfo = ref(JSON.parse(localStorage.getItem('kb_user') || '{}'))

const handleLogout = async () => {
  // 先让后端把 Redis 里的 token 删掉，否则本地清空后旧 token 仍然可用 12 小时
  try {
    await request.post('/api/user/logout')
  } catch (e) {
    // 网络异常也要让用户退出，不阻塞
  }
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  ElMessage.success('已安全退出')
  router.push('/login')
}
</script>
<style scoped>
.legacy-nav {
  --el-menu-bg-color: var(--gw-nav);
  --el-menu-text-color: var(--gw-muted);
  --el-menu-hover-text-color: var(--gw-text);
  --el-menu-hover-bg-color: var(--gw-surface-hover);
  --el-menu-active-color: var(--gw-accent);
  border-right: none;
}
</style>
