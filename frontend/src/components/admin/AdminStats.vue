<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  items: {
    label: string
    value: string | number
    change?: string
    note?: string
    tone?: 'violet' | 'blue' | 'orange' | 'pink' | 'green' | 'indigo'
    icon: Component
  }[]
}>()
</script>

<template>
  <section class="admin-stats" :class="{ 'is-dense': items.length > 4 }" aria-label="统计指标">
    <article v-for="item in items" :key="item.label" class="admin-stat" :class="`tone-${item.tone ?? 'violet'}`">
      <div class="stat-icon" aria-hidden="true"><component :is="item.icon" /></div>
      <div class="stat-content">
        <div class="stat-label">{{ item.label }}</div>
        <strong>{{ typeof item.value === 'number' ? item.value.toLocaleString('zh-CN') : item.value }}</strong>
        <div v-if="item.change || item.note" class="stat-foot">
          <span v-if="item.change" :class="{ 'negative-change': item.change.startsWith('−') || item.change.startsWith('-') }">{{ item.change }}</span>
          {{ item.note }}
        </div>
      </div>
      <component :is="item.icon" class="stat-watermark" aria-hidden="true" />
    </article>
  </section>
</template>

<style scoped>
.admin-stats { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.admin-stat {
  --tone: var(--gw-accent);
  --tint: var(--gw-accent-tint);
  position: relative;
  isolation: isolate;
  flex: 1 1 145px;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: clamp(9px, 1.05vw, 17px);
  min-height: 108px;
  padding: 14px clamp(11px, 1.25vw, 20px);
  overflow: hidden;
  border: 1px solid var(--gw-border);
  border-radius: 16px;
  background: linear-gradient(125deg, transparent, var(--tint)), var(--gw-surface);
  box-shadow: 0 2px 5px #080e2033;
}
.tone-blue { --tone: var(--gw-link); --tint: var(--gw-info-tint); }
.tone-orange { --tone: var(--gw-warning); --tint: var(--gw-warning-tint); }
.tone-pink { --tone: var(--gw-danger); --tint: var(--gw-danger-tint); }
.tone-green { --tone: var(--gw-success); --tint: var(--gw-success-tint); }
.tone-indigo { --tone: var(--gw-link); --tint: var(--gw-accent-tint); }
.stat-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: clamp(42px, 4.2vw, 65px);
  aspect-ratio: 1;
  border-radius: 50%;
  background: color-mix(in srgb, var(--tone) 10%, transparent);
  color: var(--tone);
  font-size: clamp(24px, 2.5vw, 38px);
}
.stat-label { font-weight: 660; }
.stat-content strong { display: block; font-size: clamp(23px, 1.7vw, 30px); line-height: 1.25; white-space: nowrap; font-variant-numeric: tabular-nums; }
.is-dense .admin-stat { padding-inline: clamp(10px, 1.1vw, 17px); }
.is-dense .stat-icon { width: clamp(37px, 3.6vw, 56px); font-size: clamp(23px, 2.35vw, 35px); }
.is-dense .stat-content strong { font-size: clamp(21px, 1.6vw, 27px); }
.stat-foot { font-size: 12px; color: var(--admin-muted); margin-top: 4px; }
.stat-foot span { color: var(--gw-success); font-weight: 600; }
.stat-foot .negative-change { color: var(--gw-danger); }
.stat-watermark { position: absolute; z-index: -1; right: 6px; bottom: 3px; font-size: 66px; color: var(--tone); opacity: .18; }
</style>
