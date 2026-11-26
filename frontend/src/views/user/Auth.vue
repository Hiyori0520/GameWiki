<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="always">
      <div class="auth-head">
        <h2>GameWiki 游戏攻略知识库</h2>
        <p>登录后可收藏攻略、查看浏览记录、向 AI 提问</p>
      </div>

      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-position="top" @submit.prevent>
            <el-form-item label="账号">
              <el-input v-model="loginForm.username" placeholder="请输入账号" size="large" :prefix-icon="User" @keyup.enter="handleLogin" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" size="large" show-password :prefix-icon="Lock" @keyup.enter="handleLogin" />
            </el-form-item>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
              登录
            </el-button>
          </el-form>
          <div class="auth-tip">
            演示账号：<code>player1</code> / <code>gamewiki2026</code>
          </div>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-position="top" @submit.prevent>
            <el-form-item label="账号">
              <el-input v-model="regForm.username" placeholder="3-20 个字符" size="large" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="regForm.nickname" placeholder="不填则自动生成" size="large" :prefix-icon="Medal" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="regForm.password" type="password" placeholder="至少 6 位" size="large" show-password :prefix-icon="Lock" @keyup.enter="handleRegister" />
            </el-form-item>
            <el-button type="success" size="large" class="submit-btn" :loading="loading" @click="handleRegister">
              注册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="auth-foot">
        <router-link to="/ai/chat">先去看看 AI 助手</router-link>
        <span class="split">·</span>
        <router-link to="/login">管理后台入口</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Medal } from '@element-plus/icons-vue'
import request from '../../utils/request'

const router = useRouter()
const route = useRoute()
const tab = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', nickname: '', password: '' })

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/api/user/login', loginForm)
    localStorage.setItem('kb_token', res.data.token)
    localStorage.setItem('kb_user', JSON.stringify(res.data.userInfo))
    ElMessage.success('登录成功')
    // 优先返回登录前的目标页面，否则管理员进后台，玩家进首页
    const role = res.data.userInfo?.role
    router.replace(route.query.redirect || (role === 'ADMIN' ? '/admin' : '/home'))
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!regForm.username || !regForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await request.post('/api/user/register', regForm)
    ElMessage.success('注册成功，请登录')
    loginForm.username = regForm.username
    loginForm.password = regForm.password
    tab.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--gw-nav) 0%, var(--gw-surface-hover) 100%);
  padding: 20px;
  box-sizing: border-box;
}
.auth-card {
  width: 420px;
  border-radius: 14px;
  padding: 8px 12px 4px;
}
.auth-head {
  text-align: center;
  margin-bottom: 8px;
}
.auth-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: var(--gw-text);
}
.auth-head p {
  margin: 0;
  font-size: 13px;
  color: var(--gw-muted);
}
.submit-btn {
  width: 100%;
  margin-top: 4px;
}
.auth-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--gw-subtle);
  text-align: center;
}
.auth-tip code {
  background: var(--gw-surface-raised);
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--gw-muted);
}
.auth-foot {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--gw-line);
  text-align: center;
  font-size: 13px;
}
.auth-foot a {
  color: var(--gw-accent);
  text-decoration: none;
}
.auth-foot .split {
  margin: 0 8px;
  color: var(--gw-subtle);
}
</style>
