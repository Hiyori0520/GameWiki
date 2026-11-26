<script setup lang="ts">
import { computed } from 'vue'
import type { UserDetail, UserLevelRule } from '../../../types/admin/user'

const props = defineProps<{ rules: UserLevelRule[]; user?: UserDetail }>()
const rows = computed(() => [...props.rules].sort((left, right) => left.level - right.level))
const currentRule = computed(() => rows.value.find(rule => rule.level === props.user?.level))
const formatExperience = new Intl.NumberFormat('zh-CN').format
</script>

<template>
  <div class="user-level-panel">
    <dl v-if="user" class="user-level-current">
      <dt>当前等级</dt><dd>Lv.{{ user.level }} {{ currentRule?.name }}</dd>
      <dt>当前经验值</dt><dd>{{ user.experience == null ? '—' : formatExperience(user.experience) }}</dd>
    </dl>
    <div v-if="rows.length" class="admin-table-scroll" tabindex="0" aria-label="用户等级规则">
      <table class="admin-table">
        <thead><tr><th scope="col">等级</th><th scope="col">等级名称</th><th scope="col">所需经验</th><th scope="col">等级规则</th></tr></thead>
        <tbody>
          <tr v-for="rule in rows" :key="rule.level">
            <td>Lv.{{ rule.level }}</td><td>{{ rule.name }}</td><td>{{ formatExperience(rule.requiredExperience) }}</td><td>{{ rule.rule }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="admin-empty">暂无等级规则</p>
  </div>
</template>

<style scoped>
.user-level-current { display: grid; grid-template-columns: max-content 1fr; gap: 7px 14px; margin: 0 0 12px; }
.user-level-current dt { color: var(--admin-muted); }
.user-level-current dd { margin: 0; }
.user-level-panel :is(th, td):first-child { white-space: nowrap; }
</style>
