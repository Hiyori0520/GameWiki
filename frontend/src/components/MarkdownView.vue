<template>
  <div class="markdown-body" v-html="html"></div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  content: { type: String, default: '' }
})

// marked 渲染 + DOMPurify 清洗，避免攻略正文里的脚本注入
const html = computed(() => DOMPurify.sanitize(marked.parse(props.content || '')))
</script>

<style scoped>
.markdown-body {
  font-size: 15px;
  line-height: 1.9;
  color: var(--gw-muted);
  word-break: break-word;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 24px 0 12px;
  font-weight: 600;
  color: var(--gw-text);
  line-height: 1.4;
}
.markdown-body :deep(h1) { font-size: 22px; }
.markdown-body :deep(h2) { font-size: 19px; border-left: 4px solid var(--gw-accent); padding-left: 10px; }
.markdown-body :deep(h3) { font-size: 16px; }
.markdown-body :deep(p) { margin: 10px 0; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 10px 0; padding-left: 24px; }
.markdown-body :deep(li) { margin: 6px 0; }
.markdown-body :deep(blockquote) {
  margin: 14px 0;
  padding: 8px 14px;
  border-left: 4px solid var(--gw-link);
  background: var(--gw-surface-raised);
  color: var(--gw-muted);
}
.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--gw-surface-raised);
  font-size: 13px;
  color: var(--gw-accent);
}
.markdown-body :deep(pre) {
  padding: 14px;
  border-radius: 8px;
  background: var(--gw-nav);
  overflow-x: auto;
}
.markdown-body :deep(pre code) {
  background: transparent;
  color: var(--gw-muted);
  padding: 0;
}
.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 14px 0;
  font-size: 14px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--gw-line);
  padding: 8px 10px;
  text-align: left;
}
.markdown-body :deep(th) { background: var(--gw-surface); }
.markdown-body :deep(a) { color: var(--gw-link); }
.markdown-body :deep(img) { max-width: 100%; border-radius: 8px; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid var(--gw-line); margin: 20px 0; }
</style>
