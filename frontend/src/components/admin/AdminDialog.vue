<script setup lang="ts">
import { onBeforeUnmount, onMounted, useId, useTemplateRef } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'

defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const headingId = useId()

onMounted(() => dialog.value?.showModal())
onBeforeUnmount(() => dialog.value?.close())
</script>

<template>
  <dialog ref="dialog" class="admin-dialog" :aria-labelledby="headingId" @cancel.prevent="emit('close')">
    <header>
      <h2 :id="headingId">{{ title }}</h2>
      <button class="admin-icon-button" type="button" aria-label="关闭弹窗" autofocus @click="emit('close')"><CloseOutlined /></button>
    </header>
    <div class="dialog-body"><slot /></div>
    <footer v-if="$slots.footer"><slot name="footer" /></footer>
  </dialog>
</template>

<style scoped>
.admin-dialog {
  width: min(570px, calc(100% - 28px));
  max-height: calc(100dvh - 28px);
  padding: 0;
  color: var(--admin-text);
  background: var(--gw-surface);
  border: 1px solid var(--admin-line);
  border-radius: 17px;
  box-shadow: 0 16px 70px #18122d45;
}
.admin-dialog::backdrop { background: #221a4190; }
header, footer { display: flex; align-items: center; gap: 10px; padding: 14px 19px; }
header { justify-content: space-between; border-bottom: 1px solid var(--admin-line); }
h2 { font-size: 18px; font-weight: 700; margin: 0; }
.dialog-body { padding: 18px 19px; }
footer { justify-content: flex-end; flex-wrap: wrap; border-top: 1px solid var(--admin-line); }
</style>
