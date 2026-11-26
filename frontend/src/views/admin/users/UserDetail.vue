<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { UserOutlined } from '@ant-design/icons-vue'
import { formatUserTime, maskUserEmail, userMemberships, userStatuses } from '../../../types/admin/user'
import type { UserDetail } from '../../../types/admin/user'

const props = defineProps<{ user: UserDetail; showEmail: boolean }>()
const failedAvatar = ref(false)
watch(() => props.user.avatar, () => { failedAvatar.value = false })
const email = computed(() => props.showEmail ? maskUserEmail(props.user.maskedEmail) : undefined)
</script>

<template>
  <div class="user-detail-panel">
    <header>
      <img v-if="user.avatar && !failedAvatar" :src="user.avatar" alt="" @error="failedAvatar = true" />
      <span v-else class="user-detail-avatar" aria-hidden="true"><UserOutlined /></span>
      <div><strong>{{ user.name }}</strong><div class="admin-row-subtitle">{{ user.username }}</div></div>
    </header>
    <dl>
      <dt>UID</dt><dd>{{ user.id }}</dd>
      <template v-if="email"><dt>邮箱</dt><dd>{{ email }}</dd></template>
      <dt>等级</dt><dd><RouterLink :to="{ name: 'admin-user-levels', query: { user: user.id } }" class="user-level-link">Lv.{{ user.level }}</RouterLink></dd>
      <dt>会员身份</dt><dd>{{ userMemberships[user.membership] }}</dd>
      <dt>账号状态</dt><dd>{{ userStatuses[user.status] }}</dd>
      <dt>注册时间</dt><dd>{{ formatUserTime(user.registeredAt) }}</dd>
      <dt>最近登录</dt><dd>{{ formatUserTime(user.lastLoginAt) }}</dd>
      <dt>攻略贡献</dt><dd>{{ user.guideContributions ?? '—' }}</dd>
      <dt>公开社区贡献</dt><dd>{{ user.communityContributions ?? '—' }}</dd>
    </dl>
    <h3>封禁记录</h3>
    <ul v-if="user.banHistory?.length">
      <li v-for="(entry, index) in user.banHistory" :key="index">
        <strong>{{ entry.action === 'BAN' ? '封禁' : '解封' }}</strong>
        <span>{{ entry.operator }} · {{ formatUserTime(entry.operatedAt) }}</span>
        <span v-if="entry.reason">{{ entry.reason }}</span>
        <span v-if="entry.action === 'BAN' && entry.expiresAt !== undefined">{{ entry.expiresAt ? `到期 ${formatUserTime(entry.expiresAt)}` : '永久封禁' }}</span>
      </li>
    </ul>
    <p v-else class="admin-empty">{{ user.banHistory ? '暂无封禁记录' : '封禁记录暂未提供' }}</p>
  </div>
</template>

<style scoped>
.user-detail-panel header { display: flex; align-items: center; gap: 10px; }
.user-detail-panel header img, .user-detail-avatar { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
.user-detail-avatar { display: inline-grid; place-items: center; color: var(--gw-accent); background: var(--gw-accent-tint); }
.user-detail-panel dl { display: grid; grid-template-columns: minmax(66px, auto) 1fr; gap: 7px 14px; }
.user-detail-panel dt { color: var(--admin-muted); }
.user-detail-panel dd { margin: 0; overflow-wrap: anywhere; }
.user-detail-panel h3 { margin: 15px 0 6px; font-size: 14px; }
.user-detail-panel ul { list-style: none; margin: 0; padding: 0; }
.user-detail-panel li { display: grid; gap: 4px; padding: 7px 0; border-top: 1px solid var(--admin-line); overflow-wrap: anywhere; }
.user-level-link { color: var(--gw-accent); font-weight: 650; }
</style>
