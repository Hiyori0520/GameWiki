<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownView from '../../components/MarkdownView.vue'
import { catalogApi, guideArticle } from '../../api/catalog'

const route = useRoute()
const router = useRouter()
const guide = ref<ReturnType<typeof guideArticle> | null>(null)
const loading = ref(true)
const siblings = ref<ReturnType<typeof guideArticle>[]>([])
const related = computed(() => siblings.value.filter(item => item.id !== guide.value?.id).slice(0, 4))
const position = computed(() => siblings.value.findIndex(item => item.id === guide.value?.id))
const prevGuide = computed(() => position.value > 0 ? siblings.value[position.value - 1] : null)
const nextGuide = computed(() => position.value >= 0 ? siblings.value[position.value + 1] : null)
const progress = ref(0)
const activeSection = ref('')
const fontSize = ref<'small' | 'normal' | 'large'>('normal')
const fonts = ['small', 'normal', 'large'] as const
const collected = ref(false)
const liked = ref(false)
const following = ref(false)
const busy = ref(false)
const likeCount = computed(() => guide.value?.likes ?? 0)
const toast = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined

function pad(value: number) { return String(value).padStart(2, '0') }
function updateScrollState() {
  const doc = document.documentElement
  const total = doc.scrollHeight - doc.clientHeight
  progress.value = total > 0 ? Math.min(100, Math.max(0, (doc.scrollTop / total) * 100)) : 0
  const sections = guide.value?.sections ?? []
  let current = sections[0]?.id ?? ''
  for (const section of sections) {
    const element = document.getElementById(section.id)
    if (element && element.getBoundingClientRect().top <= 150) current = section.id
  }
  activeSection.value = current
}
function scrollToSection(id: string) {
  const element = document.getElementById(id)
  if (!element) return
  window.scrollTo({ top: element.getBoundingClientRect().top + window.scrollY - 100, behavior: 'smooth' })
  activeSection.value = id
}
function fontStep(delta: number) {
  fontSize.value = fonts[Math.max(0, Math.min(fonts.length - 1, fonts.indexOf(fontSize.value) + delta))]
}
function showToast(message: string) {
  toast.value = message
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value = '' }, 2000)
}

watch(() => route.params.id, async (value, _, onCleanup) => {
  const id = Number(value)
  const abort = new AbortController()
  onCleanup(() => abort.abort())
  loading.value = true
  guide.value = null
  siblings.value = []
  fontSize.value = 'normal'
  window.scrollTo({ top: 0 })
  try {
    if (!Number.isSafeInteger(id) || id < 1) return
    const row = await catalogApi.guide(id, abort.signal)
    if (abort.signal.aborted) return
    guide.value = guideArticle(row)
    liked.value = !!row.liked
    collected.value = !!row.collected
    following.value = !!row.following
    await nextTick()
    updateScrollState()
    const matches = await catalogApi.guides({ gameId: row.gameId, pageSize: 100 }, abort.signal)
    if (abort.signal.aborted) return
    siblings.value = matches.records.map(guideArticle)
    await catalogApi.visit('guide', id)
    if (guide.value?.id === id) guide.value.views++
  } catch { /* API 客户端负责显示错误反馈。 */ }
  finally { if (!abort.signal.aborted) loading.value = false }
}, { immediate: true })

async function updateReaction(type: 'like' | 'favorite' | 'follow') {
  const current = guide.value
  if (!current || busy.value) return
  busy.value = true
  try {
    if (type === 'like') {
      const result = await catalogApi.like(current.id, !liked.value)
      if (guide.value?.id === current.id) {
        liked.value = result.active
        guide.value.likes = result.likes
      }
    } else if (type === 'favorite') {
      const result = await catalogApi.favorite('guide', current.id, !collected.value)
      if (guide.value?.id === current.id) collected.value = result.active
    } else {
      const result = await catalogApi.follow(current.authorId, !following.value)
      if (guide.value?.id === current.id) following.value = result.active
    }
  } catch { /* 保留服务端已确认的状态。 */ }
  finally { busy.value = false }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    showToast('链接已复制到剪贴板')
  } catch { showToast('复制失败，请手动复制地址栏链接') }
}

function openGuide(id: number) { router.push(`/guides/${id}`) }
function openGame() { if (guide.value) router.push(`/games/${guide.value.gameId}`) }
function goLibrary() { router.push('/games') }
function askAi() {
  if (!guide.value) return
  router.push({ path: '/ai/chat', query: { q: `《${guide.value.gameName}》的攻略「${guide.value.title}」，我想进一步了解：` } })
}
onMounted(() => window.addEventListener('scroll', updateScrollState, { passive: true }))
onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateScrollState)
  if (toastTimer) clearTimeout(toastTimer)
})
</script>

<template>
  <div class="guide-detail">
    <div class="progress-track">
      <div
        class="progress-bar"
        :style="{ width: `${progress}%`, opacity: progress > 0.5 ? 1 : 0 }"
      ></div>
    </div>

    <!-- 面包屑 -->
    <nav class="breadcrumb">
      <a @click="router.push('/home')">首页</a>
      <span class="sep">/</span>
      <a @click="goLibrary">游戏库</a>
      <span class="sep">/</span>
      <a v-if="guide" @click="openGame">{{ guide.gameName }}</a>
      <span v-if="guide" class="sep">/</span>
      <span class="current">{{ guide ? guide.category : '攻略详情' }}</span>
    </nav>

    <!-- 攻略不存在 -->
    <section v-if="loading" class="empty-state" role="status">正在加载攻略…</section>
    <section v-else-if="!guide" class="empty-state">
      <h3>没有找到这篇攻略</h3>
      <p>链接可能已失效，或攻略已被作者删除。</p>
      <button class="primary-btn" @click="goLibrary">去游戏库看看</button>
    </section>

    <template v-else>
      <!-- 文章头部 -->
      <header class="article-hero">
        <div class="hero-cover" :class="guide.gameCover" :style="guide.gameImage ? { backgroundImage: 'linear-gradient(0deg,#15152999,transparent), url(' + JSON.stringify(guide.gameImage) + ')', backgroundSize: 'cover', backgroundPosition: 'center' } : undefined" @click="openGame">
          <span class="cover-symbol">{{ guide.gameSymbol }}</span>
        </div>

        <div class="hero-main">
          <div class="hero-tags">
            <span class="chip">{{ guide.category }}</span>
            <span class="game-link" @click="openGame">{{ guide.gameName }}</span>
          </div>

          <h1>{{ guide.title }}</h1>
          <p class="summary">{{ guide.summary }}</p>

          <div class="meta-row">
            <div class="author">
              <span class="avatar">{{ guide.author.slice(0, 1) }}</span>
              <div class="author-text">
                <strong>{{ guide.author }}</strong>
                <span>攻略作者</span>
              </div>
            </div>
            <span class="meta-item">发布于 {{ guide.date }}</span>
            <span class="meta-item">更新于 {{ guide.updated }}</span>
            <span class="meta-item">{{ guide.readMin }} 分钟读完</span>
            <span class="meta-item">{{ guide.views }} 次阅读</span>
          </div>
        </div>
      </header>

      <!-- 操作栏 -->
      <div class="action-bar">
        <div class="action-left">
          <button class="primary-btn" :class="{ collected }" :disabled="busy" @click="updateReaction('favorite')">
            {{ collected ? '已收藏' : '收藏攻略' }}
          </button>
          <button class="outline-btn" :class="{ liked }" :disabled="busy" @click="updateReaction('like')">
            {{ liked ? '已点赞' : '点赞' }} · {{ likeCount }}
          </button>
          <button class="ghost-btn" @click="copyLink">复制链接</button>
        </div>

        <div class="font-control">
          <span class="font-label">字号</span>
          <button :disabled="fontSize === 'small'" @click="fontStep(-1)">A−</button>
          <button :disabled="fontSize === 'large'" @click="fontStep(1)">A+</button>
        </div>
      </div>

      <div class="layout">
        <!-- 正文 -->
        <article class="article" :class="`font-${fontSize}`">
          <div class="article-body">
            <section
              v-for="(section, index) in guide.sections"
              :id="section.id"
              :key="section.id"
              class="section-block"
            >
              <h2>
                <span class="section-index">{{ pad(index + 1) }}</span>
                {{ section.heading }}
              </h2>

              <MarkdownView :content="section.body" />
            </section>

            <div class="tag-row">
              <span v-for="tag in guide.tags" :key="tag" class="tag">#{{ tag }}</span>
            </div>

            <div class="article-end">
              <p>本文由社区作者整理，如有补充欢迎在评论区交流。</p>
            </div>
          </div>

          <!-- 上下篇 -->
          <nav class="pager">
            <button v-if="prevGuide" class="pager-item" @click="openGuide(prevGuide.id)">
              <span class="pager-label">上一篇</span>
              <strong>{{ prevGuide.title }}</strong>
            </button>
            <span v-else class="pager-placeholder">已经是第一篇了</span>

            <button v-if="nextGuide" class="pager-item next" @click="openGuide(nextGuide.id)">
              <span class="pager-label">下一篇</span>
              <strong>{{ nextGuide.title }}</strong>
            </button>
            <span v-else class="pager-placeholder">已经是最后一篇了</span>
          </nav>
        </article>

        <!-- 侧栏 -->
        <aside class="sidebar">
          <!-- 目录 -->
          <section class="side-card toc">
            <div class="side-head">
              <h3>章节目录</h3>
              <span class="read-percent">{{ Math.round(progress) }}%</span>
            </div>
            <ul>
              <li
                v-for="(section, index) in guide.sections"
                :key="section.id"
                :class="{ active: activeSection === section.id }"
              >
                <a @click.prevent="scrollToSection(section.id)">
                  <span class="toc-index">{{ pad(index + 1) }}</span>
                  {{ section.heading }}
                </a>
              </li>
            </ul>
          </section>

          <!-- 作者 -->
          <section class="side-card author-card">
            <span class="side-label">作者</span>
            <div class="author-head">
              <span class="avatar large">{{ guide.author.slice(0, 1) }}</span>
              <div>
                <strong>{{ guide.author }}</strong>
                <p>攻略作者</p>
              </div>
            </div>
            <div class="author-stats">
              <div>
                <strong>{{ siblings.length + 1 }}</strong>
                <span>相关攻略</span>
              </div>
              <div>
                <strong>{{ guide.likes }}</strong>
                <span>获赞</span>
              </div>
            </div>
            <button class="outline-btn full" :disabled="busy" @click="updateReaction('follow')">
              {{ following ? '已关注' : '关注作者' }}
            </button>
          </section>

          <!-- AI 辅助 -->
          <section class="side-card ai-card">
            <span class="side-label">AI 辅助</span>
            <h3>这篇攻略没讲清楚？</h3>
            <p>把这篇攻略交给 AI 助手，可以针对你的进度追问细节。</p>
            <button class="ai-btn" @click="askAi">向 AI 提问</button>
          </section>

          <!-- 相关攻略 -->
          <section class="side-card">
            <div class="side-head">
              <h3>相关攻略</h3>
            </div>
            <ul class="related-list">
              <li v-for="item in related" :key="item.id" @click="openGuide(item.id)">
                <span class="related-cat">{{ item.category }}</span>
                <strong>{{ item.title }}</strong>
                <span class="related-meta">{{ item.author }} · {{ item.views }} 阅读</span>
              </li>
            </ul>
          </section>
        </aside>
      </div>
    </template>

    <transition name="toast-fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<style scoped>
.article :deep(.markdown-body) { color: inherit; font-size: inherit; }
.article :deep(.markdown-body code) { overflow-wrap: anywhere; }

.guide-detail {
  color: var(--gw-text);
  padding-bottom: 40px;
  background: var(--gw-bg);
  min-height: 100vh;
}

/* 阅读进度 */
.progress-track {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: transparent;
  z-index: 50;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--gw-primary), var(--gw-violet));
  transition: width 0.1s linear;
}

/* 面包屑 */
.breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
  color: var(--gw-subtle);
  margin-bottom: 24px;
}

.breadcrumb a {
  color: var(--gw-link);
  cursor: pointer;
}

.breadcrumb a:hover {
  color: var(--gw-link);
}

.breadcrumb .sep {
  color: var(--gw-border);
}

.breadcrumb .current {
  color: var(--gw-muted);
}

/* 头部 */
.article-hero {
  display: flex;
  gap: 24px;
  padding: 26px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--gw-surface-raised), var(--gw-surface));
  border: 1px solid var(--gw-border);
}

.hero-cover {
  flex: 0 0 96px;
  height: 96px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: inset 0 0 30px #00000055;
  transition: transform 0.2s ease;
}

.hero-cover:hover {
  transform: translateY(-2px);
}

.cover-symbol {
  font-size: 36px;
  color: #ffffffdd;
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.hero-tags {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.chip {
  padding: 4px 10px;
  border-radius: 5px;
  background: var(--gw-surface-raised);
  color: var(--gw-link);
  font-size: 12px;
}

.game-link {
  color: var(--gw-link);
  font-size: 13px;
  cursor: pointer;
}

.game-link:hover {
  text-decoration: underline;
}

.hero-main h1 {
  font-size: clamp(24px, 2.4vw, 34px);
  line-height: 1.35;
  margin: 0 0 12px;
  color: var(--gw-text);
}

.summary {
  color: var(--gw-muted);
  line-height: 1.85;
  margin: 0 0 18px;
  max-width: 720px;
}

.meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 12px;
  color: var(--gw-subtle);
}

.author {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 16px;
  border-right: 1px solid var(--gw-line);
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--gw-primary), var(--gw-violet));
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}

.avatar.large {
  width: 44px;
  height: 44px;
  font-size: 18px;
}

.author-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.author-text strong {
  color: var(--gw-muted);
  font-size: 13px;
}

.author-text span {
  color: var(--gw-subtle);
  font-size: 11px;
}

/* 操作栏 */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px;
  margin: 20px 0 28px;
  padding: 14px 18px;
  border-radius: 12px;
  background: var(--gw-surface);
  border: 1px solid var(--gw-line);
}

.action-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

button {
  cursor: pointer;
  border: none;
  font-family: inherit;
  font-size: 13px;
}

.primary-btn {
  padding: 9px 20px;
  border-radius: 8px;
  color: #fff;
  background: var(--gw-action-gradient);
}

.primary-btn.collected {
  background: linear-gradient(120deg, #d99b2b, #c2703a);
}

.outline-btn {
  padding: 9px 18px;
  border-radius: 8px;
  color: var(--gw-muted);
  background: transparent;
  border: 1px solid var(--gw-border);
}

.outline-btn:hover {
  border-color: var(--gw-link);
  color: var(--gw-link);
}

.outline-btn.liked {
  border-color: var(--gw-link);
  color: var(--gw-link);
  background: var(--gw-surface-raised);
}

.outline-btn.full {
  width: 100%;
  margin-top: 14px;
}

.ghost-btn {
  padding: 9px 8px;
  background: transparent;
  color: var(--gw-link);
}

.ghost-btn:hover {
  color: var(--gw-link);
}

.font-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.font-label {
  font-size: 12px;
  color: var(--gw-subtle);
}

.font-control button {
  width: 34px;
  height: 30px;
  border-radius: 6px;
  background: var(--gw-surface-raised);
  color: var(--gw-muted);
  border: 1px solid var(--gw-border);
}

.font-control button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 布局 */
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 28px;
  align-items: start;
}

.article {
  min-width: 0;
}

.article-body {
  padding: 30px 32px;
  border-radius: 14px;
  background: var(--gw-nav);
  border: 1px solid var(--gw-line);
}

.article-body p {
  color: var(--gw-muted);
  line-height: 1.95;
  margin: 0 0 18px;
}

.font-small .article-body {
  font-size: 14px;
}

.font-normal .article-body {
  font-size: 15.5px;
}

.font-large .article-body {
  font-size: 17.5px;
}

.article-body p:last-child {
  margin-bottom: 0;
}

.section-block + .section-block {
  margin-top: 36px;
  padding-top: 30px;
  border-top: 1px solid var(--gw-line);
}

.section-block h2 {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 20px;
  color: var(--gw-text);
  margin: 0 0 18px;
  line-height: 1.5;
}

.section-index {
  font-size: 13px;
  color: #5f87c9;
  font-weight: 600;
  letter-spacing: 1px;
}

.step-list {
  margin: 0 0 20px;
  padding-left: 22px;
  color: var(--gw-muted);
  line-height: 1.9;
  font-size: 14.5px;
}

.step-list li {
  margin-bottom: 6px;
}

.tip-box {
  display: flex;
  gap: 12px;
  margin: 0 0 20px;
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--gw-surface);
  border-left: 3px solid var(--gw-link);
}

.tip-label {
  flex: 0 0 auto;
  color: var(--gw-link);
  font-size: 12px;
  padding-top: 3px;
  letter-spacing: 1px;
}

.tip-box p {
  margin: 0;
  color: var(--gw-muted);
  font-size: 14px;
  line-height: 1.8;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 30px;
  padding-top: 22px;
  border-top: 1px solid var(--gw-line);
}

.tag {
  padding: 5px 12px;
  border-radius: 6px;
  background: var(--gw-surface-raised);
  color: var(--gw-link);
  font-size: 12px;
}

.article-end {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed var(--gw-surface-hover);
}

.article-end p {
  margin: 0;
  color: var(--gw-subtle);
  font-size: 12.5px;
}

/* 上下篇 */
.pager {
  display: flex;
  gap: 16px;
  margin-top: 20px;
}

.pager-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 18px;
  border-radius: 12px;
  background: var(--gw-surface);
  border: 1px solid var(--gw-line);
  text-align: left;
  color: var(--gw-muted);
}

.pager-item:hover {
  border-color: #3d5b85;
  background: var(--gw-surface);
}

.pager-item.next {
  text-align: right;
}

.pager-label {
  font-size: 11px;
  color: var(--gw-subtle);
  letter-spacing: 1px;
}

.pager-item strong {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.6;
}

.pager-placeholder {
  flex: 1;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px dashed var(--gw-line);
  color: var(--gw-subtle);
  font-size: 12.5px;
  display: flex;
  align-items: center;
}

.pager-placeholder:last-child {
  justify-content: flex-end;
}

/* 侧栏 */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 24px;
}

.side-card {
  padding: 18px;
  border-radius: 12px;
  background: var(--gw-surface);
  border: 1px solid var(--gw-line);
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.side-head h3 {
  margin: 0;
  font-size: 14px;
  color: var(--gw-muted);
}

.read-percent {
  font-size: 12px;
  color: var(--gw-link);
}

.side-label {
  display: block;
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--gw-subtle);
  margin-bottom: 10px;
}

.toc ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.toc a {
  display: flex;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 7px;
  color: var(--gw-subtle);
  font-size: 13px;
  line-height: 1.6;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.15s ease;
}

.toc a:hover {
  background: var(--gw-surface);
  color: var(--gw-muted);
}

.toc li.active a {
  background: #20385a;
  color: var(--gw-link);
}

.toc-index {
  color: #5b7292;
  font-size: 12px;
}

.toc li.active .toc-index {
  color: var(--gw-link);
}

.author-card .side-label {
  margin-bottom: 12px;
}

.author-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-head strong {
  font-size: 14px;
  color: var(--gw-muted);
}

.author-head p {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: var(--gw-subtle);
}

.author-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--gw-surface-hover);
}

.author-stats div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.author-stats strong {
  font-size: 16px;
  color: var(--gw-muted);
}

.author-stats span {
  font-size: 11px;
  color: var(--gw-subtle);
}

.ai-card {
  background: linear-gradient(150deg, var(--gw-surface-hover), #2a2450);
  border-color: #3a3a6b;
}

.ai-card h3 {
  margin: 0 0 8px;
  font-size: 15px;
  color: var(--gw-text);
}

.ai-card p {
  margin: 0 0 14px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--gw-muted);
}

.ai-btn {
  width: 100%;
  padding: 10px;
  border-radius: 8px;
  color: #fff;
  background: linear-gradient(120deg, var(--gw-primary), #8b53d8);
}

.ai-btn:hover {
  filter: brightness(1.08);
}

.related-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.related-list li {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--gw-line);
  cursor: pointer;
}

.related-list li:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.related-cat {
  font-size: 11px;
  color: var(--gw-link);
}

.related-list strong {
  font-size: 13px;
  font-weight: 500;
  color: var(--gw-muted);
  line-height: 1.6;
}

.related-list li:hover strong {
  color: var(--gw-link);
}

.related-meta {
  font-size: 11px;
  color: var(--gw-subtle);
}

/* 空状态 */
.empty-state {
  padding: 70px 24px;
  text-align: center;
  border-radius: 14px;
  background: var(--gw-surface);
  border: 1px dashed var(--gw-line);
}

.empty-state h3 {
  margin: 0 0 10px;
  font-size: 20px;
  color: var(--gw-muted);
}

.empty-state p {
  margin: 0 0 22px;
  color: var(--gw-subtle);
  font-size: 14px;
}

/* 封面配色 */
.blue {
  background: linear-gradient(145deg, #182953, #4d73c4);
}

.purple {
  background: linear-gradient(145deg, #31214c, #7754ae);
}

.orange {
  background: linear-gradient(145deg, #562a22, #aa583d);
}

.teal {
  background: linear-gradient(145deg, #164951, #298b95);
}

.indigo {
  background: linear-gradient(145deg, #232d64, #626fb4);
}

.sky {
  background: linear-gradient(145deg, #164363, #5a93c3);
}

.gray {
  background: linear-gradient(145deg, #313a4d, var(--gw-subtle));
}

.green {
  background: linear-gradient(145deg, #22432e, #4e9567);
}

/* 提示条 */
.toast {
  position: fixed;
  left: 50%;
  bottom: 40px;
  transform: translateX(-50%);
  padding: 10px 22px;
  border-radius: 24px;
  background: var(--gw-line);
  border: 1px solid var(--gw-border-strong);
  color: var(--gw-muted);
  font-size: 13px;
  box-shadow: 0 8px 24px #00000055;
  z-index: 60;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}

/* 响应式 */
@media (max-width: 1080px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
  }

  .toc {
    display: none;
  }
}

@media (max-width: 720px) {
  .article-hero {
    flex-direction: column;
    gap: 16px;
    padding: 20px;
  }

  .hero-cover {
    flex: 0 0 64px;
    width: 64px;
    height: 64px;
  }

  .cover-symbol {
    font-size: 26px;
  }

  .article-body {
    padding: 22px 18px;
  }

  .author {
    border-right: none;
    padding-right: 0;
  }

  .pager {
    flex-direction: column;
  }

  .pager-placeholder:last-child {
    justify-content: flex-start;
  }

  .action-bar {
    padding: 12px 14px;
  }
}
</style>
