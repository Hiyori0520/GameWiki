<script setup lang="ts">
import DOMPurify from 'dompurify'
import { Marked } from 'marked'
import { computed, ref, useId } from 'vue'
import { safeGuideImage } from '../../api/admin/guide'

const props = defineProps<{ source: string; withContents?: boolean }>()
const preview = ref<HTMLElement>()
const id = useId()
const markdown = new Marked({ gfm: true, renderer: { html: () => '' } })
const documentContent = computed(() => {
  const markup = markdown.parse(props.source, { async: false })
  const fragment = DOMPurify.sanitize(markup, {
    RETURN_DOM_FRAGMENT: true,
    ALLOWED_TAGS: [
      'a', 'blockquote', 'br', 'code', 'del', 'em', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr',
      'img', 'li', 'ol', 'p', 'pre', 'strong', 'table', 'tbody', 'td', 'th', 'thead', 'tr', 'ul',
    ],
    ALLOWED_ATTR: ['alt', 'href', 'src', 'start', 'title'],
    ALLOW_DATA_ATTR: false,
  })
  for (const image of fragment.querySelectorAll('img')) {
    const safe = safeGuideImage(image.getAttribute('src') ?? undefined)
    if (safe) {
      image.src = safe
      image.loading = 'lazy'
    } else {
      image.remove()
    }
  }
  const headings = Array.from(fragment.querySelectorAll('h1, h2, h3, h4, h5, h6'), (heading, index) => {
    heading.id = `${id}-heading-${index + 1}`
    return { id: heading.id, label: heading.textContent, level: Number(heading.tagName.slice(1)) }
  })
  const container = document.createElement('div')
  container.append(fragment)
  return { html: container.innerHTML, headings }
})

function goToHeading(headingId: string) {
  preview.value?.querySelector(`#${CSS.escape(headingId)}`)?.scrollIntoView({ block: 'nearest' })
}
</script>

<template>
  <article class="guide-markdown">
    <details v-if="withContents && documentContent.headings.length" class="guide-contents" open>
      <summary>目录</summary>
      <ol>
        <li v-for="heading in documentContent.headings" :key="heading.id"
          :style="{ marginInlineStart: `${(heading.level - 1) * 0.65}rem` }">
          <button type="button" @click="goToHeading(heading.id)">{{ heading.label }}</button>
        </li>
      </ol>
    </details>
    <div v-if="documentContent.html" ref="preview" class="guide-markdown-body" v-html="documentContent.html" />
    <p v-else class="guide-markdown-empty">暂无正文。</p>
  </article>
</template>

<style scoped>
.guide-markdown { min-width: 0; overflow-wrap: anywhere; }
.guide-contents { padding: 8px 12px; border: 1px solid var(--admin-line); border-radius: 9px; }
.guide-contents summary { cursor: pointer; font-weight: 650; }
.guide-contents ol { list-style: none; margin: 5px 0 0; padding: 0; }
.guide-contents button { padding: 1px; border: 0; background: none; color: var(--admin-accent); text-align: left; cursor: pointer; }
.guide-markdown-empty { color: var(--admin-muted); }
.guide-markdown-body :deep(h1), .guide-markdown-body :deep(h2), .guide-markdown-body :deep(h3) { line-height: 1.35; }
.guide-markdown-body :deep(p) { margin: 0.6em 0; }
.guide-markdown-body :deep(a) { color: var(--admin-accent); text-decoration: underline; }
.guide-markdown-body :deep(img) { max-width: 100%; height: auto; border-radius: 7px; }
.guide-markdown-body :deep(pre) { padding: 8px; border-radius: 7px; background: var(--gw-accent-tint); white-space: pre-wrap; }
.guide-markdown-body :deep(blockquote) { margin-inline: 0; padding-inline: 12px; border-inline-start: 3px solid var(--admin-line); }
.guide-markdown-body :deep(table) { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
.guide-markdown-body :deep(td), .guide-markdown-body :deep(th) { padding: 4px 8px; border: 1px solid var(--admin-line); }
</style>
