<script setup lang="ts">
import { computed } from 'vue'
import { AppstoreOutlined, RightOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons-vue'
import { adminModules } from '../../admin/modules'
import { adminSession, hasAdminPermission, isAdminDemo } from '../../api/admin/client'
import adminAvatar from '../../assets/admin/avatar-admin.jpg'

const displayName = computed(() => isAdminDemo ? '演示管理员' : adminSession.value?.name || '管理员')
const accessibleModules = computed(() => adminModules.filter((item) => hasAdminPermission(`${item.id}:read`)))
const permissions = computed(() => [...new Set(adminSession.value?.permissions ?? [])].sort())
</script>

<template>
  <div class="profile-grid">
    <section class="admin-panel profile-panel" aria-labelledby="profile-account">
      <header class="profile-heading">
        <UserOutlined aria-hidden="true" />
        <h2 id="profile-account">当前账号</h2>
      </header>

      <div class="profile-identity">
        <img :src="adminAvatar" alt="" width="66" height="66" />
        <div>
          <h3>{{ displayName }}</h3>
          <span class="admin-tag">后台管理员</span>
        </div>
      </div>

      <dl class="profile-facts">
        <div>
          <dt>当前会话</dt>
          <dd>
            <span class="admin-tag" :class="isAdminDemo ? 'is-blue' : 'is-green'">
              {{ isAdminDemo ? '开发演示' : '已验证' }}
            </span>
          </dd>
        </div>
      </dl>

      <footer class="profile-actions">
        <RouterLink class="admin-button" to="/admin/settings"><SettingOutlined aria-hidden="true" /> 系统设置</RouterLink>
      </footer>
    </section>

    <section class="admin-panel profile-panel" aria-labelledby="profile-modules">
      <header class="profile-heading">
        <AppstoreOutlined aria-hidden="true" />
        <h2 id="profile-modules">可访问模块</h2>
      </header>

      <ul v-if="accessibleModules.length" class="profile-modules">
        <li v-for="item in accessibleModules" :key="item.id">
          <RouterLink class="profile-module" :to="item.path">
            <span class="profile-module-icon"><component :is="item.icon" aria-hidden="true" /></span>
            <span>{{ item.label }}</span>
            <RightOutlined class="profile-module-arrow" aria-hidden="true" />
          </RouterLink>
        </li>
      </ul>
      <p v-else class="admin-empty">当前账号还没有业务模块访问权限。</p>

      <details v-if="permissions.length" class="profile-permissions">
        <summary>权限明细 <span class="profile-permission-count">{{ permissions.length }} 项</span></summary>
        <ul>
          <li v-for="permission in permissions" :key="permission"><code class="admin-tag">{{ permission }}</code></li>
        </ul>
      </details>
    </section>
  </div>
</template>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: minmax(270px, 0.85fr) minmax(360px, 1.35fr);
  align-items: start;
  gap: 13px;
  max-width: 1120px;
}
.profile-panel { padding: 17px clamp(16px, 1.8vw, 24px); }
.profile-heading { display: flex; align-items: center; gap: 9px; margin-bottom: 15px; }
.profile-heading > .anticon { color: var(--admin-accent); font-size: 20px; }
.profile-heading h2 { margin: 0; font-size: 18px; }
.profile-identity {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 15px;
  border: 1px solid var(--admin-line);
  border-radius: 12px;
  background: linear-gradient(120deg, var(--gw-accent-tint), var(--gw-surface));
}
.profile-identity img { flex-shrink: 0; border: 2px solid #fff; border-radius: 50%; object-fit: cover; }
.profile-identity h3 { margin: 0 0 5px; font-size: 20px; overflow-wrap: anywhere; }
.profile-facts { margin: 8px 0 0; }
.profile-facts > div { display: flex; justify-content: space-between; align-items: center; gap: 10px; min-height: 43px; }
.profile-facts dt { color: var(--admin-muted); }
.profile-facts dd { margin: 0; }
.profile-actions { display: flex; justify-content: flex-end; padding-top: 12px; border-top: 1px solid var(--admin-line); }
.profile-modules { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; margin: 0; padding: 0; list-style: none; }
.profile-module {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 58px;
  padding: 0 10px;
  border: 1px solid var(--admin-line);
  border-radius: 10px;
  background: var(--gw-surface-raised);
  font-weight: 600;
}
.profile-module:hover { border-color: var(--gw-border); background: var(--gw-surface-hover); }
.profile-module-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 33px;
  height: 33px;
  border-radius: 9px;
  background: var(--gw-surface-hover);
  color: var(--admin-accent);
  font-size: 18px;
}
.profile-module-arrow { margin-left: auto; color: var(--admin-muted); font-size: 12px; }
.profile-permissions { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--admin-line); }
.profile-permissions summary { cursor: pointer; font-weight: 600; }
.profile-permission-count { margin-left: 6px; color: var(--admin-muted); font-weight: 400; }
.profile-permissions ul { display: flex; flex-wrap: wrap; gap: 6px; margin: 10px 0 0; padding: 0; list-style: none; }
.profile-permissions code { white-space: normal; overflow-wrap: anywhere; }
@media (max-width: 980px) { .profile-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 580px) { .profile-modules { grid-template-columns: minmax(0, 1fr); } }
</style>
