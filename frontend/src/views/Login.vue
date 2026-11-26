<template>
  <div class="login-wrapper">
    <el-card class="login-box" shadow="always">
      <div style="text-align: center; margin-bottom: 24px">
        <el-icon size="38" color="var(--gw-accent)"><Cpu /></el-icon>
        <h2 style="margin: 10px 0 4px; color: var(--gw-text)">AI 智能游戏攻略知识库系统</h2>
        <p style="margin: 0; font-size: 13px; color: var(--gw-muted)">Spring Boot 3 + Redis + Spring AI 智能体</p>
      </div>

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="账号登录" name="login">
          <el-form :model="loginForm" style="margin-top: 16px">
            <el-form-item>
              <el-input v-model="loginForm.username" placeholder="请输入账号（默认 admin / player1）" prefix-icon="User" size="large" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码（演示账号 gamewiki2026）" prefix-icon="Lock" size="large" show-password @keyup.enter="handleLogin" />
            </el-form-item>
            <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleLogin">
              立即登录系统
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="新玩家注册" name="register">
          <el-form :model="regForm" style="margin-top: 16px">
            <el-form-item>
              <el-input v-model="regForm.username" placeholder="设置登录账号" prefix-icon="User" size="large" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="regForm.nickname" placeholder="设置战术代号（昵称）" prefix-icon="Medal" size="large" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="regForm.password" type="password" placeholder="设置登录密码" prefix-icon="Lock" size="large" show-password />
            </el-form-item>
            <el-button type="success" size="large" style="width: 100%" :loading="loading" @click="handleRegister">
              完成注册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const activeTab = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: 'admin', password: 'gamewiki2026' })
const regForm = reactive({ username: '', nickname: '', password: '' })

const handleLogin = async () => {
  loading.value = true
  try {
    const res = await request.post('/api/user/login', loginForm)
    localStorage.setItem('kb_token', res.data.token)
    localStorage.setItem('kb_user', JSON.stringify(res.data.userInfo))
    ElMessage.success('登录成功，欢迎回来！')
    // 按角色分流：管理员进后台，普通玩家进用户端，避免玩家误入统计页
    const role = res.data.userInfo?.role
    router.push(role === 'ADMIN' ? '/admin' : '/home')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  loading.value = true
  try {
    await request.post('/api/user/register', regForm)
    ElMessage.success('注册成功，请切换至登录页进入系统')
    loginForm.username = regForm.username
    loginForm.password = regForm.password
    activeTab.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, var(--gw-nav) 0%, var(--gw-surface-hover) 100%);
}
.login-box {
  width: 420px;
  border-radius: 12px;
  padding: 12px;
}
</style>