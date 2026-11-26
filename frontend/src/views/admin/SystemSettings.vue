<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  BarChartOutlined, CheckCircleOutlined, MenuFoldOutlined, MenuUnfoldOutlined,
  SaveOutlined, SettingOutlined, TableOutlined, UndoOutlined,
} from '@ant-design/icons-vue'
import {
  adminDashboardPeriods, adminPageSizes, adminPreferences, defaultAdminPreferences,
  saveAdminPreferences, type AdminPreferences,
} from '../../admin/preferences'

const draft = reactive({ ...adminPreferences.value })
const feedback = ref('')
const saveError = ref('')
const saving = ref(false)

function matchesDraft(preferences: Readonly<AdminPreferences>) {
  return draft.sidebarCollapsed === preferences.sidebarCollapsed
    && draft.pageSize === preferences.pageSize
    && draft.dashboardPeriod === preferences.dashboardPeriod
}

const hasChanges = computed(() => !matchesDraft(adminPreferences.value))
const isDefault = computed(() => matchesDraft(defaultAdminPreferences))

watch(draft, () => {
  feedback.value = ''
  saveError.value = ''
}, { flush: 'sync' })

function restoreDefaults() {
  Object.assign(draft, defaultAdminPreferences)
  feedback.value = hasChanges.value ? '已恢复默认值，保存后生效。' : '已恢复默认值。'
}

async function saveSettings() {
  if (!hasChanges.value || saving.value) return
  saving.value = true
  try {
    await saveAdminPreferences(draft)
    saveError.value = ''
    feedback.value = '设置已保存。'
  } catch {
    feedback.value = ''
    saveError.value = '设置保存失败，请重试。'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="admin-panel settings-panel" aria-labelledby="settings-title" @submit.prevent="saveSettings">
    <header class="settings-heading">
      <span class="settings-heading-icon"><SettingOutlined aria-hidden="true" /></span>
      <div>
        <h2 id="settings-title">后台偏好</h2>
        <p>这些设置随管理员账号保存。</p>
      </div>
    </header>

    <div class="settings-rows">
      <div class="settings-row" role="group" aria-labelledby="sidebar-setting" aria-describedby="sidebar-setting-description">
        <span class="settings-row-icon"><MenuFoldOutlined aria-hidden="true" /></span>
        <div class="settings-copy">
          <strong id="sidebar-setting">默认侧栏状态</strong>
          <p id="sidebar-setting-description">进入后台时应用，顶栏按钮可临时切换。</p>
        </div>
        <div class="settings-control sidebar-options">
          <label class="settings-option" :class="{ 'is-selected': !draft.sidebarCollapsed }">
            <input v-model="draft.sidebarCollapsed" type="radio" name="sidebar-state" :value="false" />
            <MenuUnfoldOutlined aria-hidden="true" /> 展开
          </label>
          <label class="settings-option" :class="{ 'is-selected': draft.sidebarCollapsed }">
            <input v-model="draft.sidebarCollapsed" type="radio" name="sidebar-state" :value="true" />
            <MenuFoldOutlined aria-hidden="true" /> 折叠
          </label>
        </div>
      </div>

      <label class="settings-row" for="admin-default-page-size">
        <span class="settings-row-icon"><TableOutlined aria-hidden="true" /></span>
        <span class="settings-copy">
          <strong id="page-size-setting">默认每页条数</strong>
          <span id="page-size-setting-description">应用于游戏、攻略和用户列表。</span>
        </span>
        <select
          id="admin-default-page-size" v-model.number="draft.pageSize" class="admin-select settings-control"
          aria-labelledby="page-size-setting" aria-describedby="page-size-setting-description"
        >
          <option v-for="size in adminPageSizes" :key="size" :value="size">{{ size }} 条</option>
        </select>
      </label>

      <label class="settings-row" for="admin-default-trend-period">
        <span class="settings-row-icon"><BarChartOutlined aria-hidden="true" /></span>
        <span class="settings-copy">
          <strong id="trend-period-setting">总览默认时间范围</strong>
          <span id="trend-period-setting-description">进入后台总览时应用于平台数据趋势。</span>
        </span>
        <select
          id="admin-default-trend-period" v-model.number="draft.dashboardPeriod" class="admin-select settings-control"
          aria-labelledby="trend-period-setting" aria-describedby="trend-period-setting-description"
        >
          <option v-for="days in adminDashboardPeriods" :key="days" :value="days">近 {{ days }} 天</option>
        </select>
      </label>
    </div>

    <footer class="settings-footer">
      <div class="settings-feedback">
        <p v-if="saveError" class="admin-form-error" role="alert">{{ saveError }}</p>
        <p v-else-if="feedback" role="status"><CheckCircleOutlined aria-hidden="true" /> {{ feedback }}</p>
        <p v-else-if="hasChanges">有未保存的修改</p>
      </div>
      <div class="settings-actions">
        <button type="button" class="admin-button" :disabled="isDefault" @click="restoreDefaults">
          <UndoOutlined aria-hidden="true" /> 恢复默认
        </button>
        <button type="submit" class="admin-button is-primary" :disabled="!hasChanges">
          <SaveOutlined aria-hidden="true" /> 保存设置
        </button>
      </div>
    </footer>
  </form>
</template>

<style scoped>
.settings-panel { max-width: 1120px; }
.settings-heading, .settings-footer { padding: 18px clamp(16px, 2vw, 26px); }
.settings-heading { display: flex; align-items: center; gap: 13px; border-bottom: 1px solid var(--admin-line); }
.settings-heading-icon, .settings-row-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  color: var(--admin-accent);
  background: var(--gw-surface-hover);
}
.settings-heading-icon { width: 45px; height: 45px; border-radius: 13px; font-size: 23px; }
.settings-heading h2 { margin: 0 0 3px; font-size: 19px; }
.settings-heading p, .settings-copy :is(p, span), .settings-feedback p { margin: 0; color: var(--admin-muted); }
.settings-rows { padding-inline: clamp(16px, 2vw, 26px); }
.settings-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) 196px;
  align-items: center;
  gap: 14px;
  min-height: 89px;
  padding-block: 13px;
}
.settings-row + .settings-row { border-top: 1px solid var(--admin-line); }
.settings-row-icon { width: 36px; height: 36px; border-radius: 10px; font-size: 17px; }
.settings-copy strong { display: block; margin-bottom: 3px; font-size: 14px; }
.settings-copy :is(p, span), .settings-heading p { font-size: 13px; }
.settings-control { width: 100%; }
.sidebar-options { display: flex; gap: 6px; }
.settings-option {
  position: relative;
  display: flex;
  flex: 1 1 0;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 35px;
  border: 1px solid var(--admin-line);
  border-radius: 8px;
  background: var(--gw-surface-raised);
  cursor: pointer;
}
.settings-option input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.settings-option.is-selected { border-color: var(--gw-border); color: var(--admin-accent); background: var(--gw-surface-hover); }
.settings-option:has(input:focus-visible) { outline: 2px solid var(--gw-accent); outline-offset: 2px; }
.settings-footer { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; border-top: 1px solid var(--admin-line); }
.settings-feedback p[role='status'] { color: var(--gw-success); }
.settings-feedback .admin-form-error { color: var(--gw-danger); }
.settings-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-inline-start: auto; }
@media (max-width: 700px) {
  .settings-row { grid-template-columns: auto minmax(0, 1fr); }
  .settings-control { grid-column: 2; justify-self: start; max-width: 196px; }
}
</style>
