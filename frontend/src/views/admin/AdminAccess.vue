<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import '../../layouts/admin.css'

const route = useRoute()
const message = computed(() => {
  if (route.query.reason === '401') return '请在后台认证服务登录后重试。'
  if (route.query.reason === '403') return '当前账号没有访问此页面的权限。'
  return '后台认证服务暂时无法验证管理员身份，请稍后重试。'
})
const next = computed(() => {
  const path = route.query.next
  return typeof path === 'string' && path.startsWith('/admin/') && !path.startsWith('/admin/access')
    ? path
    : '/admin/dashboard'
})
</script>

<template>
  <main class="admin-shell access-shell">
    <section class="admin-panel access-panel" aria-labelledby="admin-access-title">
      <h1 id="admin-access-title">管理后台访问受限</h1>
      <p role="status">{{ message }}</p>
      <div class="admin-toolbar">
        <RouterLink class="admin-button is-primary" :to="next">重试</RouterLink>
        <RouterLink class="admin-button" to="/">返回 GameWiki</RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
.access-shell { display: grid; place-items: center; padding: 20px; }
.access-panel { width: min(100%, 480px); padding: 24px; }
.access-panel :is(h1, p) { margin: 0 0 16px; }
</style>
