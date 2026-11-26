<script setup lang="ts">
import { Select } from 'ant-design-vue'
import { computed, ref, watch } from 'vue'
import { adminErrorMessage } from '../../api/admin/client'
import { searchGuideGames } from '../../api/admin/guide'
import type { GuideGameOption } from '../../types/admin/guide'

const props = defineProps<{ selected?: GuideGameOption; disabled?: boolean; placeholder: string }>()
const value = defineModel<number>({ required: true })
const emit = defineEmits<{ selectGame: [game: GuideGameOption | undefined] }>()
const keyword = ref('')
const games = ref<GuideGameOption[]>([])
const loading = ref(false)
const error = ref('')
const current = ref<GuideGameOption>()
const options = computed(() => {
  const available = [...games.value]
  const selected = current.value ?? props.selected
  if (selected && !available.some(game => game.id === selected.id)) available.unshift(selected)
  return available.map(game => ({ value: game.id, label: game.name }))
})

watch(keyword, (query, _, onCleanup) => {
  const controller = new AbortController()
  loading.value = true
  error.value = ''
  const timer = window.setTimeout(async () => {
    try {
      const found = await searchGuideGames(query, controller.signal)
      if (!controller.signal.aborted) games.value = found
    } catch (failure) {
      if (!controller.signal.aborted) {
        games.value = []
        error.value = adminErrorMessage(failure)
      }
    } finally {
      if (!controller.signal.aborted) loading.value = false
    }
  }, query ? 250 : 0)
  onCleanup(() => { window.clearTimeout(timer); controller.abort() })
}, { immediate: true })

watch(() => props.selected, () => { current.value = undefined })

function change(next: unknown) {
  const id = typeof next === 'number' ? next : 0
  current.value = games.value.find(game => game.id === id)
  value.value = id
  emit('selectGame', current.value ?? (props.selected?.id === id ? props.selected : undefined))
}
</script>

<template>
  <Select class="guide-game-select" :value="value || undefined" :placeholder="placeholder" :aria-label="placeholder"
    :options="options" :disabled="disabled" :loading="loading" show-search allow-clear :filter-option="false"
    :dropdown-match-select-width="false" @search="keyword = $event" @change="change">
    <template #notFoundContent>{{ error || (loading ? '正在查询…' : '没有符合条件的游戏') }}</template>
  </Select>
</template>

<style scoped>
.guide-game-select { min-width: 104px; }
.guide-game-select :deep(.ant-select-selector) {
  border-color: var(--admin-line) !important;
  min-height: 36px;
  align-items: center;
  border-radius: 9px !important;
  background: var(--gw-surface) !important;
}
.guide-game-select :deep(.ant-select-selection-placeholder) { color: var(--admin-muted); }
</style>
