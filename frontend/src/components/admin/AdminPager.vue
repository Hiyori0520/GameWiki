<script setup lang="ts">
import { computed } from 'vue'
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { adminPageSizes } from '../../admin/preferences'

const props = defineProps<{ total: number }>()
const page = defineModel<number>('page', { required: true })
const size = defineModel<number>('size', { required: true })
const pages = computed(() => Math.max(1, Math.ceil(props.total / size.value)))
const displayed = computed(() => {
  if (pages.value <= 5) return Array.from({ length: pages.value }, (_, index) => index + 1)
  const middle = Math.min(Math.max(page.value, 3), pages.value - 2)
  return [1, ...(middle > 3 ? ['before'] : []), middle - 1, middle, middle + 1, ...(middle < pages.value - 2 ? ['after'] : []), pages.value]
})
</script>

<template>
  <footer class="admin-pagination">
    <div class="pagination-count">
      <span>共 {{ total.toLocaleString('zh-CN') }} 条记录</span>
      <label>每页
        <select v-model.number="size" class="admin-select" aria-label="每页条数" @change="page = 1">
          <option v-for="option in adminPageSizes" :key="option" :value="option">{{ option }} 条</option>
        </select>
      </label>
    </div>
    <nav class="pagination-pages" aria-label="分页">
      <button class="admin-button" type="button" aria-label="上一页" :disabled="page <= 1" @click="page--"><LeftOutlined /></button>
      <template v-for="item in displayed" :key="item">
        <button
          v-if="typeof item === 'number'"
          class="admin-button" :class="{ 'is-primary': item === page }" type="button"
          :aria-label="`第 ${item} 页`" :aria-current="item === page ? 'page' : undefined" @click="page = item"
        >{{ item }}</button>
        <span v-else aria-hidden="true">…</span>
      </template>
      <button class="admin-button" type="button" aria-label="下一页" :disabled="page >= pages" @click="page++"><RightOutlined /></button>
    </nav>
  </footer>
</template>

<style scoped>
.admin-pagination { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; padding: 17px 15px; color: var(--admin-muted); }
.pagination-count, .pagination-count label, .pagination-pages { display: flex; align-items: center; gap: 9px; }
.pagination-pages .admin-button { min-width: 36px; padding-inline: 8px; }
</style>
