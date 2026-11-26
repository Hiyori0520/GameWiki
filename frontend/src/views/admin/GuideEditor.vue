<script setup lang="ts">
import { Select } from 'ant-design-vue'
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminErrorMessage, hasAdminPermission } from '../../api/admin/client'
import {
  changeGuide, getGuide, getGuideTaxonomy, safeGuideImage, saveGuide, uploadGuideImage, validateGuideDraft,
} from '../../api/admin/guide'
import GuideGameSelect from '../../components/admin/GuideGameSelect.vue'
import { guideStatusLabels, type GuideDraft, type GuideGameOption, type GuideRecord } from '../../types/admin/guide'

const GuideMarkdown = defineAsyncComponent(() => import('../../components/admin/GuideMarkdown.vue'))
const route = useRoute()
const router = useRouter()
const canWrite = computed(() => hasAdminPermission('guides:write'))
const previous = ref<GuideRecord>()
const game = ref<GuideGameOption>()
const categories = ref<string[]>([])
const tags = ref<string[]>([])
const textarea = ref<HTMLTextAreaElement>()
const coverInput = ref<HTMLInputElement>()
const imageInput = ref<HTMLInputElement>()
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const loadError = ref('')
const error = ref('')
const info = ref('')
const retry = ref(0)
const preview = ref(false)
let activeUpload: AbortController | undefined
let routeVersion = 0

const emptyDraft = (): GuideDraft => ({
  title: '', description: '', gameId: 0, category: '', tags: [], cover: '', body: '', spoiler: false,
})
const draft = ref(emptyDraft())
const readOnly = computed(() => !canWrite.value || previous.value?.status === 'pending')
const locked = computed(() => readOnly.value || saving.value || uploading.value)
const categoryNames = computed(() => [...new Set([
  ...categories.value, ...(draft.value.category ? [draft.value.category] : []),
])])
const tagOptions = computed(() => [...new Set([...tags.value, ...draft.value.tags])]
  .map(name => ({ value: name, label: name })))
const safeCover = computed(() => safeGuideImage(draft.value.cover))

watch([() => route.params.id, retry], async ([rawId], _, onCleanup) => {
  const id = rawId === undefined ? undefined : Number(rawId)
  if (id !== undefined && previous.value?.id === id && !loadError.value && !loading.value) return
  routeVersion += 1
  activeUpload?.abort()
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  previous.value = undefined
  game.value = undefined
  draft.value = emptyDraft()
  loadError.value = ''
  error.value = ''
  info.value = ''
  preview.value = false
  loading.value = true
  try {
    if (!canWrite.value) throw new Error('当前账号没有编辑攻略的权限。')
    if (id !== undefined && (!Number.isSafeInteger(id) || id < 1)) throw new Error('攻略编号无效。')
    const [loadedCategories, loadedTags, record] = await Promise.all([
      getGuideTaxonomy('category', controller.signal), getGuideTaxonomy('tag', controller.signal),
      id ? getGuide(id, controller.signal, true) : Promise.resolve(undefined),
    ])
    if (controller.signal.aborted) return
    categories.value = loadedCategories.map(item => item.name)
    tags.value = loadedTags.map(item => item.name)
    if (!record) return
    previous.value = record
    game.value = { id: record.gameId, name: record.gameName }
    draft.value = {
      title: record.title, description: record.description, gameId: record.gameId, category: record.category,
      tags: [...record.tags], cover: record.cover, body: record.body ?? record.description, spoiler: record.spoiler ?? false,
    }
  } catch (failure) {
    if (!controller.signal.aborted) loadError.value = adminErrorMessage(failure)
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}, { immediate: true })

onBeforeUnmount(() => { routeVersion += 1; activeUpload?.abort() })

function insertText(snippet: string) {
  const input = textarea.value
  if (!input) return
  const start = input.selectionStart
  draft.value.body = draft.value.body.slice(0, start) + snippet + draft.value.body.slice(input.selectionEnd)
  void nextTick(() => {
    input.focus()
    input.setSelectionRange(start + snippet.length, start + snippet.length)
  })
}

async function uploadImage(event: Event, inline: boolean) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || locked.value) return
  const controller = new AbortController()
  activeUpload = controller
  error.value = ''
  uploading.value = true
  try {
    const url = await uploadGuideImage(file, inline, controller.signal)
    if (controller.signal.aborted) return
    if (inline) insertText(`\n\n![攻略图片](<${url}>)\n\n`)
    else draft.value.cover = url
  } catch (failure) {
    if (!controller.signal.aborted) error.value = adminErrorMessage(failure)
  } finally {
    if (activeUpload === controller) uploading.value = false
  }
}

async function save(submitForReview: boolean) {
  if (locked.value || loading.value) return
  error.value = ''
  info.value = ''
  saving.value = true
  const version = routeVersion
  let saved: GuideRecord | undefined
  try {
    const input = validateGuideDraft(draft.value, submitForReview)
    if (game.value?.id !== input.gameId) throw new Error('请重新选择所属游戏。')
    saved = await saveGuide(input, game.value, previous.value)
    if (routeVersion !== version) return
    previous.value = saved
    if (String(saved.id) !== route.params.id) await router.replace(`/admin/guides/${saved.id}/edit`)
    if (routeVersion !== version) return
    if (submitForReview) {
      await changeGuide(saved, 'submit-review')
      if (routeVersion === version) {
        await router.push({ path: '/admin/guides', query: { status: 'pending', id: String(saved.id) } })
      }
    } else {
      info.value = saved.revisionOf ? '新版本已保存为草稿，原版本仍可查看。' : '草稿已保存。'
    }
  } catch (failure) {
    if (routeVersion === version) {
      error.value = saved && submitForReview
        ? `草稿已保存，提交审核失败。 ${adminErrorMessage(failure)}`
        : adminErrorMessage(failure)
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="admin-panel guide-editor" :aria-busy="loading">
    <header class="guide-editor-heading">
      <RouterLink class="admin-button" to="/admin/guides">返回攻略列表</RouterLink>
      <span v-if="previous" class="admin-tag">{{ guideStatusLabels[previous.status] }}</span>
      <span v-if="previous?.revisionOf" class="admin-tag is-blue">攻略新版本</span>
    </header>
    <div v-if="loading" class="admin-empty" role="status">正在加载攻略编辑器…</div>
    <div v-else-if="loadError" class="admin-empty" role="alert">
      <p>{{ loadError }}</p>
      <button v-if="canWrite" type="button" class="admin-button" @click="retry++">重试</button>
    </div>
    <form v-else class="admin-modal-form guide-editor-form" @submit.prevent="save(false)">
      <p v-if="previous?.status === 'published'" class="guide-editor-note">修改会保存为待提交的新版本，原版本继续展示。</p>
      <p v-if="previous?.status === 'pending'" class="guide-editor-note">攻略等待审核，审核结束后可以继续处理。</p>
      <p v-if="previous?.reviewReason" class="guide-editor-note">审核意见 · {{ previous.reviewReason }}</p>
      <div class="guide-editor-fields">
        <label>攻略标题
          <input v-model="draft.title" class="admin-input" maxlength="150" required :disabled="locked" />
        </label>
        <label>所属游戏
          <GuideGameSelect v-model="draft.gameId" :selected="game" :disabled="locked" placeholder="搜索并选择所属游戏"
            @select-game="game = $event" />
        </label>
        <label>攻略分类
          <select v-model="draft.category" class="admin-select" required :disabled="locked">
            <option value="" disabled>选择分类</option>
            <option v-for="name in categoryNames" :key="name" :value="name">{{ name }}</option>
          </select>
        </label>
        <label>攻略标签
          <Select v-model:value="draft.tags" mode="multiple" :options="tagOptions" :disabled="locked"
            placeholder="选择标签" aria-label="攻略标签" />
        </label>
      </div>
      <label>攻略简介
        <textarea v-model="draft.description" class="admin-input" maxlength="1000" rows="2" :disabled="locked" />
      </label>
      <div class="guide-editor-cover">
        <div>
          <label>攻略封面地址
            <input v-model="draft.cover" class="admin-input" :disabled="locked" placeholder="https://… 或上传封面" />
          </label>
          <button class="admin-button" type="button" :disabled="locked" @click="coverInput?.click()">上传封面</button>
        </div>
        <img v-if="safeCover" :key="safeCover" :src="safeCover" alt="攻略封面预览" @error="($event.target as HTMLImageElement).hidden = true" />
        <input ref="coverInput" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="uploadImage($event, false)" />
      </div>
      <label class="guide-editor-spoiler"><input v-model="draft.spoiler" type="checkbox" :disabled="locked" /> 包含剧透</label>
      <div class="guide-editor-content">
        <div class="guide-editor-toolbar">
          <button type="button" class="admin-button" :class="{ 'is-primary': !preview }" :aria-pressed="!preview"
            :disabled="uploading" @click="preview = false">Markdown 编辑</button>
          <button type="button" class="admin-button" :class="{ 'is-primary': preview }" :aria-pressed="preview"
            :disabled="uploading" @click="preview = true">预览</button>
          <template v-if="!preview">
            <button type="button" class="admin-button" :disabled="locked" @click="insertText('\n\n## 小节标题\n\n')">插入标题</button>
            <button type="button" class="admin-button" :disabled="locked" @click="insertText('\n\n- 列表项\n')">插入列表</button>
            <button type="button" class="admin-button" :disabled="locked" @click="insertText('\n\n> 引用内容\n')">插入引用</button>
            <button type="button" class="admin-button" :disabled="locked"
              @click="insertText('\n\n| 项目 | 说明 |\n| --- | --- |\n| 内容 | 说明 |\n')">插入表格</button>
            <button type="button" class="admin-button" :disabled="locked" @click="imageInput?.click()">上传图片</button>
            <input ref="imageInput" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="uploadImage($event, true)" />
          </template>
        </div>
        <GuideMarkdown v-if="preview" class="guide-editor-preview" :source="draft.body" with-contents />
        <textarea v-else ref="textarea" v-model="draft.body" class="admin-input guide-editor-textarea"
          aria-label="攻略正文" rows="14" :disabled="locked" />
      </div>
      <p v-if="error" class="admin-form-error" role="alert">{{ error }}</p>
      <p v-if="info || uploading" class="guide-editor-note" role="status">{{ uploading ? '正在上传图片…' : info }}</p>
      <footer class="guide-editor-buttons">
        <button type="submit" class="admin-button" :disabled="locked">{{ saving ? '保存中…' : '保存草稿' }}</button>
        <button type="button" class="admin-button is-primary" :disabled="locked" @click="save(true)">保存并提交审核</button>
      </footer>
    </form>
  </section>
</template>

<style scoped>
.guide-editor { padding: clamp(14px, 1.6vw, 24px); }
.guide-editor-heading, .guide-editor-toolbar, .guide-editor-buttons { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.guide-editor-form { margin-top: 14px; }
.guide-editor-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.guide-editor-cover { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; }
.guide-editor-cover > div { display: flex; align-items: end; flex: 1 1 430px; gap: 8px; }
.guide-editor-cover label { flex: 1; min-width: 0; }
.guide-editor-cover img { width: 122px; height: 58px; border-radius: 8px; object-fit: cover; }
.guide-editor-form .guide-editor-spoiler { display: flex; align-items: center; gap: 8px; justify-self: start; }
.guide-editor-content { display: grid; gap: 8px; }
.guide-editor-textarea { width: 100%; resize: vertical; font-family: ui-monospace, Consolas, monospace; }
.guide-editor-preview { padding: 14px; border: 1px solid var(--admin-line); border-radius: 9px; }
.guide-editor-buttons { justify-content: flex-end; }
.guide-editor-note { color: var(--admin-muted); margin: 0; }
@media (max-width: 790px) { .guide-editor-fields { grid-template-columns: minmax(0, 1fr); } }
</style>
