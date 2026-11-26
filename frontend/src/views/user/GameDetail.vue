<script setup lang="ts">
import { ref, computed, watch, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { catalogApi, gameCard, guideArticle, displayDate } from '../../api/catalog'

const route = useRoute()
const router = useRouter()
const game = ref<ReturnType<typeof gameCard> | null>(null)
const guides = ref<ReturnType<typeof guideArticle>[]>([])
const relatedGames = ref<ReturnType<typeof gameCard>[]>([])
const loading = ref(true)
const collected = ref(false)
const busy = ref(false)
const activeGuideCategory = ref('全部')
const guideCategories = computed(() => ['全部', ...new Set(guides.value.map(item => item.category))])
const filteredGuides = computed(() => activeGuideCategory.value === '全部' ? guides.value : guides.value.filter(item => item.category === activeGuideCategory.value))
const compose = ref<HTMLDialogElement>()
const submitting = ref(false)
const draft = reactive({ title: '', description: '', body: '' })

watch(() => route.params.id, async (value, _, onCleanup) => {
  const id = Number(value)
  const abort = new AbortController()
  onCleanup(() => abort.abort())
  loading.value = true
  game.value = null
  guides.value = []
  relatedGames.value = []
  activeGuideCategory.value = '全部'
  compose.value?.close()
  try {
    if (!Number.isSafeInteger(id) || id < 1) return
    const row = await catalogApi.game(id, abort.signal)
    if (abort.signal.aborted) return
    game.value = gameCard(row)
    collected.value = !!row.collected
    const [matches, related] = await Promise.all([
      catalogApi.guides({ gameId: id, pageSize: 100 }, abort.signal),
      catalogApi.games({ category: row.categories[0] ?? '', pageSize: 4 }, abort.signal),
    ])
    if (abort.signal.aborted) return
    guides.value = matches.records.map(guideArticle)
    relatedGames.value = related.records.filter(item => item.id !== id).slice(0, 3).map(gameCard)
    await catalogApi.visit('game', id)
  } catch {
    // API 客户端负责显示错误反馈。
  } finally {
    if (!abort.signal.aborted) loading.value = false
  }
}, { immediate: true })

async function toggleCollect() {
  const id = game.value?.id
  if (!id || busy.value) return
  busy.value = true
  try {
    const result = await catalogApi.favorite('game', id, !collected.value)
    if (game.value?.id === id) collected.value = result.active
  } catch { /* 保留原状态 */ } finally { busy.value = false }
}

async function submitGuide() {
  const gameId = game.value?.id
  if (!gameId || submitting.value) return
  submitting.value = true
  try {
    await catalogApi.submit({ ...draft, gameId, category: '新手入门' })
    Object.assign(draft, { title: '', description: '', body: '' })
    compose.value?.close()
    ElMessage.success('攻略已提交，等待审核')
  } catch { /* 保留草稿 */ } finally { submitting.value = false }
}

function goBack() { router.push('/games') }
function openGuide(id: number) { router.push(`/guides/${id}`) }
function openGame(id: number) { router.push(`/games/${id}`) }
</script>

<template>
  <div class="game-detail">

    <dialog ref="compose" class="panel compose-guide">
      <form class="compose-form" @submit.prevent="submitGuide">
        <h2>写攻略</h2>
        <label>标题 <input v-model="draft.title" required maxlength="150" /></label>
        <label>摘要 <input v-model="draft.description" maxlength="300" /></label>
        <label>攻略正文 <textarea v-model="draft.body" rows="8" required maxlength="120000" /></label>
        <div class="actions">
          <button type="button" class="ghost-btn" @click="compose?.close()">取消</button>
          <button type="submit" class="primary-btn" :disabled="submitting">{{ submitting ? '提交中…' : '提交审核' }}</button>
        </div>
      </form>
    </dialog>

    <!-- 面包屑 -->
    <nav class="breadcrumb">
      <a @click="router.push('/home')">首页</a>
      <span class="sep">/</span>
      <a @click="router.push('/games')">游戏库</a>
      <span class="sep">/</span>
      <span class="current">{{ game?.name ?? (loading ? '加载中…' : '未找到') }}</span>
    </nav>

    <!-- 找不到游戏 -->
    <section v-if="loading" class="empty-state" role="status">正在加载游戏…</section>
    <section v-else-if="!game" class="empty-state">
      <h3>没有找到这款游戏</h3>
      <p>链接可能已失效，或该游戏尚未收录。</p>
      <button @click="goBack">返回游戏库</button>
    </section>

    <template v-else>

      <!-- 顶部信息区 -->
      <section class="hero">
        <div class="hero-cover" :class="game.cover" :style="game.image ? { backgroundImage: 'linear-gradient(0deg,#151529aa,transparent), url(' + JSON.stringify(game.image) + ')', backgroundSize: 'cover', backgroundPosition: 'center' } : undefined">
          <span class="cover-symbol">{{ game.symbol }}</span>
          <span class="cover-name">{{ game.name }}</span>
        </div>

        <div class="hero-info">
          <span class="eyebrow">GAME PROFILE</span>

          <h1>{{ game.name }}</h1>

          <p class="category">{{ game.category }}</p>

          <p class="description">{{ game.description }}</p>

          <div class="tag-row">
            <span v-for="tag in game.tags" :key="tag" class="tag">
              {{ tag }}
            </span>
          </div>

          <div class="stats-row">
            <div class="stat">
              <span class="stat-label">用户评分</span>
              <span class="stat-value rating">{{ game.rating?.toFixed(1) ?? '—' }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">攻略数量</span>
              <span class="stat-value">{{ game.guideCount }} 篇</span>
            </div>
            <div class="stat">
              <span class="stat-label">平均时长</span>
              <span class="stat-value">—</span>
            </div>
            <div class="stat">
              <span class="stat-label">发行日期</span>
              <span class="stat-value">{{ displayDate(game.releaseDate) }}</span>
            </div>
          </div>

          <div class="actions">
            <button
              class="primary-btn"
              :class="{ collected }"
              :disabled="busy"
              @click="toggleCollect"
            >
              {{ collected ? '已收藏 ★' : '收藏游戏 ☆' }}
            </button>

            <button
              class="outline-btn"
              @click="compose?.showModal()"
            >
              写攻略
            </button>

            <button class="ghost-btn" @click="router.push('/ai/chat')">
              问问 AI 助手 →
            </button>
          </div>
        </div>
      </section>

      <!-- 主体：简介 + 攻略 / 侧栏信息 -->
      <div class="content-layout">

        <main class="content-main">

          <!-- 游戏简介 -->
          <section class="panel">
            <div class="panel-heading">
              <span class="section-label">ABOUT THIS GAME</span>
              <h2>游戏简介</h2>
            </div>

            <div class="intro">
              <p v-for="(para, index) in game.description.split('\n').filter(Boolean)" :key="index">
                {{ para }}
              </p>
            </div>
          </section>

          <!-- 相关攻略 -->
          <section class="panel">
            <div class="panel-heading">
              <span class="section-label">RELATED GUIDES</span>
              <h2>相关攻略</h2>
            </div>

            <div class="guide-filter">
              <button
                v-for="item in guideCategories"
                :key="item"
                class="filter-tag"
                :class="{ active: activeGuideCategory === item }"
                @click="activeGuideCategory = item"
              >
                {{ item }}
              </button>
            </div>

            <div v-if="filteredGuides.length" class="guide-list">
              <div
                v-for="guide in filteredGuides"
                :key="guide.id"
                class="guide-item"
                @click="openGuide(guide.id)"
              >
                <div class="guide-icon">▤</div>

                <div class="guide-content">
                  <span class="guide-category">{{ guide.category }}</span>
                  <h3>{{ guide.title }}</h3>
                  <p>
                    {{ guide.author }}
                    <span class="dot">·</span>
                    {{ guide.date }}
                    <span class="dot">·</span>
                    {{ guide.views }} 次浏览
                  </p>
                </div>

                <div class="guide-arrow">→</div>
              </div>
            </div>

            <div v-else class="guide-empty">
              该分类下暂时还没有攻略，换个分类看看。
            </div>
          </section>
        </main>

        <!-- 侧栏 -->
        <aside class="content-side">

          <!-- 游戏信息 -->
          <section class="panel">
            <div class="panel-heading compact">
              <h3>游戏信息</h3>
            </div>

            <dl class="info-list">
              <div class="info-row">
                <dt>开发商</dt>
                <dd>{{ game.developer || '—' }}</dd>
              </div>
              <div class="info-row">
                <dt>发行商</dt>
                <dd>{{ game.company || '—' }}</dd>
              </div>
              <div class="info-row">
                <dt>发行日期</dt>
                <dd>{{ displayDate(game.releaseDate) }}</dd>
              </div>
              <div class="info-row">
                <dt>游戏类型</dt>
                <dd>{{ game.category }}</dd>
              </div>
              <div class="info-row">
                <dt>支持平台</dt>
                <dd>{{ game.platforms.join(' / ') }}</dd>
              </div>
              <div class="info-row">
                <dt>支持语言</dt>
                <dd>—</dd>
              </div>
            </dl>
          </section>

          <!-- 相关推荐 -->
          <section class="panel">
            <div class="panel-heading compact">
              <h3>相关推荐</h3>
            </div>

            <div class="related-list">
              <div
                v-for="item in relatedGames"
                :key="item.id"
                class="related-item"
                @click="openGame(item.id)"
              >
                <div class="related-cover" :class="item.cover" :style="item.image ? { backgroundImage: 'linear-gradient(0deg,#151529aa,transparent), url(' + JSON.stringify(item.image) + ')', backgroundSize: 'cover', backgroundPosition: 'center' } : undefined">
                  <span>{{ item.symbol }}</span>
                </div>

                <div class="related-info">
                  <h4>{{ item.name }}</h4>
                  <p>{{ item.category }} · {{ item.guideCount }} 篇攻略</p>
                </div>
              </div>
            </div>
          </section>

          <!-- 返回 -->
          <button class="back-btn" @click="goBack">← 返回游戏库</button>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.compose-guide { color: inherit; width: min(560px, 92vw); }
.compose-guide::backdrop { background: #14142a80; }
.compose-form { display: grid; gap: 14px; }
.compose-form label { display: grid; gap: 6px; }
.compose-form input, .compose-form textarea { padding: 9px; font: inherit; color: #181934; border: 1px solid var(--gw-accent); border-radius: 6px; }

.game-detail {
  color: var(--gw-text);
  background: var(--gw-bg);
  min-height: 100vh;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--gw-subtle);
  margin-bottom: 22px;
}

.breadcrumb a {
  color: var(--gw-subtle);
  cursor: pointer;
}

.breadcrumb a:hover {
  color: var(--gw-link);
}

.breadcrumb .sep {
  color: var(--gw-border-strong);
}

.breadcrumb .current {
  color: var(--gw-muted);
}

/* 顶部信息区 */
.hero {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 42px;
  padding: 34px;
  border: 1px solid var(--gw-line);
  border-radius: 20px;
  background:
    radial-gradient(ellipse at 88% 12%, #2b4680 0%, transparent 52%),
    linear-gradient(125deg, var(--gw-surface), var(--gw-nav));
}

.hero-cover {
  height: 400px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  box-shadow: 0 18px 40px #00000055;
}

/* 封面质感：顶部高光 + 四周压暗 */
.hero-cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 30% 18%, #ffffff26, transparent 55%),
    radial-gradient(circle at 50% 120%, #00000059, transparent 60%);
  box-shadow: inset 0 0 60px #0000004d;
  pointer-events: none;
}

.cover-symbol {
  font-size: 120px;
  color: #ffffff8c;
  text-shadow: 0 0 50px #ffffff45;
  position: relative;
  z-index: 1;
}

.cover-name {
  position: absolute;
  bottom: 22px;
  font-size: 20px;
  font-weight: 700;
  text-shadow: 0 2px 12px #000000aa;
  z-index: 1;
}

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

.hero-info {
  padding-top: 6px;
}

.eyebrow,
.section-label {
  color: var(--gw-link);
  font-size: 12px;
  letter-spacing: 2px;
}

.hero-info h1 {
  font-size: clamp(28px, 3vw, 40px);
  margin: 14px 0 10px;
  line-height: 1.3;
}

.category {
  color: var(--gw-link);
  font-size: 14px;
  margin: 0;
}

.description {
  color: var(--gw-muted);
  line-height: 1.9;
  margin: 16px 0 0;
  max-width: 620px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.tag {
  padding: 5px 12px;
  border-radius: 6px;
  background: var(--gw-surface-hover);
  color: var(--gw-muted);
  font-size: 12px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 28px 0;
  padding: 20px 0;
  border-top: 1px solid var(--gw-line);
  border-bottom: 1px solid var(--gw-line);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-label {
  color: var(--gw-subtle);
  font-size: 12px;
}

.stat-value {
  font-size: 16px;
  color: var(--gw-muted);
  font-weight: 600;
}

.stat-value.rating {
  font-size: 24px;
  color: #ffc46b;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

button {
  cursor: pointer;
  border: none;
  font-size: 14px;
  font-family: inherit;
}

.primary-btn {
  padding: 12px 24px;
  color: white;
  border-radius: 9px;
  background: var(--gw-action-gradient);
}

.primary-btn.collected {
  background: linear-gradient(120deg, #d99b2b, #c2703a);
}

.outline-btn {
  padding: 12px 24px;
  color: var(--gw-muted);
  background: transparent;
  border: 1px solid var(--gw-border-strong);
  border-radius: 9px;
}

.outline-btn:hover {
  border-color: var(--gw-link);
  color: var(--gw-link);
}

.ghost-btn {
  padding: 12px 8px;
  background: transparent;
  color: var(--gw-link);
}

.ghost-btn:hover {
  color: var(--gw-link);
}

/* 主体布局 */
.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 26px;
  margin-top: 26px;
}

.content-main,
.content-side {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.panel {
  background: var(--gw-surface);
  border: 1px solid var(--gw-border);
  border-radius: 14px;
  padding: 26px;
}

.panel-heading {
  margin-bottom: 20px;
}

.panel-heading.compact {
  margin-bottom: 16px;
}

.panel-heading h2 {
  font-size: 22px;
  margin: 10px 0 0;
}

.panel-heading h3 {
  font-size: 16px;
  margin: 0;
}

.intro p {
  color: var(--gw-muted);
  line-height: 2;
  font-size: 14px;
  margin: 0 0 14px;
}

.intro p:last-child {
  margin-bottom: 0;
}

/* 攻略筛选 */
.guide-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}

.filter-tag {
  border: 1px solid var(--gw-border);
  background: var(--gw-surface-raised);
  color: var(--gw-muted);
  border-radius: 7px;
  padding: 7px 14px;
  font-size: 12px;
}

.filter-tag.active {
  background: #365a9a;
  border-color: #6c96ed;
  color: white;
}

/* 攻略列表 */
.guide-list {
  display: grid;
  gap: 10px;
}

.guide-item {
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 18px;
  border-radius: 11px;
  border: 1px solid var(--gw-border);
  background: var(--gw-nav);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.guide-item:hover {
  border-color: #5e8cff;
  background: var(--gw-surface);
}

.guide-icon {
  background: #253c61;
  border-radius: 10px;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--gw-link);
  font-size: 26px;
  flex-shrink: 0;
}

.guide-content {
  flex: 1;
  min-width: 0;
}

.guide-category {
  font-size: 12px;
  color: var(--gw-link);
}

.guide-content h3 {
  margin: 6px 0;
  font-size: 15px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guide-content p {
  color: var(--gw-subtle);
  font-size: 12px;
  margin: 0;
}

.dot {
  margin: 0 7px;
}

.guide-arrow {
  color: var(--gw-link);
  font-size: 20px;
  flex-shrink: 0;
}

.guide-empty {
  padding: 30px;
  text-align: center;
  color: var(--gw-subtle);
  font-size: 13px;
  border: 1px dashed var(--gw-border);
  border-radius: 11px;
}

/* 侧栏信息表 */
.info-list {
  margin: 0;
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 11px 0;
  border-bottom: 1px dashed var(--gw-line);
  font-size: 13px;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row dt {
  color: var(--gw-subtle);
  flex-shrink: 0;
}

.info-row dd {
  margin: 0;
  color: var(--gw-muted);
  text-align: right;
}

/* 相关推荐 */
.related-list {
  display: grid;
  gap: 12px;
}

.related-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px;
  border-radius: 10px;
  border: 1px solid var(--gw-border);
  background: var(--gw-nav);
  cursor: pointer;
  transition: border-color 0.2s;
}

.related-item:hover {
  border-color: #5e8cff;
}

.related-cover {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #ffffff99;
  flex-shrink: 0;
}

.related-info {
  min-width: 0;
}

.related-info h4 {
  margin: 0 0 6px;
  font-size: 14px;
}

.related-info p {
  margin: 0;
  font-size: 12px;
  color: var(--gw-subtle);
}

.back-btn {
  padding: 12px;
  border-radius: 10px;
  background: transparent;
  border: 1px solid var(--gw-border);
  color: var(--gw-muted);
}

.back-btn:hover {
  border-color: var(--gw-link);
  color: var(--gw-link);
}

/* 空状态 */
.empty-state {
  text-align: center;
  background: var(--gw-surface);
  padding: 70px 20px;
  border-radius: 16px;
  border: 1px solid var(--gw-border);
}

.empty-state p {
  color: var(--gw-muted);
}

.empty-state button {
  margin-top: 14px;
  padding: 12px 26px;
  border-radius: 9px;
  background: #407bfa;
  color: white;
}

@media (max-width: 1000px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 26px;
  }

  .hero-cover {
    height: 280px;
  }

  .content-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero {
    padding: 22px;
  }

  .hero-cover {
    height: 220px;
  }

  .cover-symbol {
    font-size: 80px;
  }

  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .panel {
    padding: 20px;
  }

  .guide-icon {
    display: none;
  }

  .guide-content h3 {
    white-space: normal;
  }
}
</style>
