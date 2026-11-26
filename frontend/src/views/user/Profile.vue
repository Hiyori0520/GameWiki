<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../utils/request'
import { catalogApi, gameCard, guideArticle, displayDate, type HistoryEntry, type PlayerProfile } from '../../api/catalog'

const router = useRouter()
const profile = reactive<PlayerProfile>({
  id: 0, nickname: '', username: '', level: 1, title: '', email: '', phone: '',
  location: '', bio: '', joinDate: '', avatar: '', contributions: 0, receivedLikes: 0,
})
const draft = reactive({ nickname: '', email: '', phone: '', location: '', bio: '' })
const editing = ref(false)
const savedTip = ref(false)
const saving = ref(false)
const favorites = ref<ReturnType<typeof gameCard>[]>([])
const favoriteGuides = ref<ReturnType<typeof guideArticle>[]>([])
const history = ref<HistoryEntry[]>([])
const activeTab = ref<'info' | 'favorites' | 'history'>('info')
const favTab = ref<'guide' | 'game'>('guide')
const showClearConfirm = ref(false)

// 修改密码
const showPassword = ref(false)
const pwdSaving = ref(false)
const pwdDone = ref(false)
const pwdError = ref('')
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const stats = [
  { label: '我的收藏', key: 'favorites' }, { label: '浏览记录', key: 'history' },
  { label: '发布攻略', key: 'guides' }, { label: '获得点赞', key: 'likes' },
] as const
const statValues = computed(() => ({
  favorites: favorites.value.length + favoriteGuides.value.length,
  history: history.value.length,
  guides: profile.contributions,
  likes: profile.receivedLikes,
}))
const tabs = computed(() => [
  { key: 'info' as const, label: '个人资料' },
  { key: 'favorites' as const, label: `我的收藏 (${statValues.value.favorites})` },
  { key: 'history' as const, label: `浏览历史 (${statValues.value.history})` },
])

onMounted(async () => {
  const [details, saved, visited] = await Promise.allSettled([catalogApi.profile(), catalogApi.favorites(), catalogApi.history()])
  if (details.status === 'fulfilled') Object.assign(profile, details.value)
  if (saved.status === 'fulfilled') {
    favorites.value = saved.value.games.map(gameCard)
    favoriteGuides.value = saved.value.guides.map(guideArticle)
  }
  if (visited.status === 'fulfilled') history.value = visited.value
})

function startEdit() {
  Object.assign(draft, { nickname: profile.nickname, email: profile.email, phone: profile.phone, location: profile.location, bio: profile.bio })
  savedTip.value = false
  editing.value = true
}
function cancelEdit() { editing.value = false }
async function saveProfile() {
  if (saving.value || !draft.nickname.trim() || !profile.id) return
  saving.value = true
  try {
    Object.assign(profile, await catalogApi.saveProfile(draft))
    const stored = JSON.parse(localStorage.getItem('kb_user') || '{}')
    localStorage.setItem('kb_user', JSON.stringify({ ...stored, nickname: profile.nickname }))
    savedTip.value = true
    editing.value = false
  } catch { /* 保留输入，便于保存重试。 */ }
  finally { saving.value = false }
}
async function removeFavorite(id: number) {
  try {
    await catalogApi.favorite('game', id, false)
    favorites.value = favorites.value.filter(item => item.id !== id)
  } catch { /* 保留收藏状态。 */ }
}
async function removeGuideFavorite(id: number) {
  try {
    await catalogApi.favorite('guide', id, false)
    favoriteGuides.value = favoriteGuides.value.filter(item => item.id !== id)
  } catch { /* 保留收藏状态。 */ }
}
async function clearHistory() {
  try {
    await catalogApi.clearHistory()
    history.value = []
    showClearConfirm.value = false
  } catch { /* 保留历史记录。 */ }
}
async function logout() {
  try { await request.post('/api/user/logout', undefined, { timeout: 5000 }) }
  catch { /* 仍允许退出本机登录。 */ }
  finally {
    localStorage.removeItem('kb_token')
    localStorage.removeItem('kb_user')
    router.replace('/auth')
  }
}
function openPassword() {
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
  pwdError.value = ''
  pwdDone.value = false
  showPassword.value = true
}
function closePassword() {
  if (pwdSaving.value) return
  showPassword.value = false
}
async function submitPassword() {
  if (pwdSaving.value) return
  if (!pwdForm.oldPassword) { pwdError.value = '请输入原密码'; return }
  if (pwdForm.newPassword.length < 6 || pwdForm.newPassword.length > 32) {
    pwdError.value = '新密码长度需在 6-32 位之间'; return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) { pwdError.value = '两次输入的新密码不一致'; return }
  if (pwdForm.newPassword === pwdForm.oldPassword) { pwdError.value = '新密码不能与原密码相同'; return }

  pwdError.value = ''
  pwdSaving.value = true
  try {
    await request.put('/api/user/password', {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    pwdDone.value = true
  } catch {
    // 原密码错误等提示由 request.js 统一弹出，这里保留输入便于重试
  } finally {
    pwdSaving.value = false
  }
}
function relogin() {
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  router.replace('/auth')
}
function openGuide(id: number) { router.push(`/guides/${id}`) }function openGame(id: number) { router.push(`/games/${id}`) }
function openHistory(item: HistoryEntry) {
  if (item.type === 'game') openGame(item.targetId)
  else openGuide(item.targetId)
}
function goLibrary() { router.push('/games') }
</script>

<template>
  <div class="profile-page">

    <!-- 顶部用户卡片 -->
    <section class="profile-hero">
      <div class="hero-deco">◈</div>

      <div class="hero-main">
        <div class="avatar">
          <img v-if="profile.avatar" :src="profile.avatar" alt="" />
          <span v-else>{{ (profile.nickname || profile.username || '游').slice(0, 2) }}</span>
        </div>

        <div class="identity">
          <span class="eyebrow">PLAYER PROFILE</span>
          <h1>{{ profile.nickname }}</h1>
          <p class="handle">
            @{{ profile.username }}
            <span class="dot">·</span>
            Lv.{{ profile.level }}
            <span class="dot">·</span>
            {{ profile.title }}
          </p>
          <p class="bio">{{ profile.bio }}</p>

          <div class="hero-actions">
            <button class="primary-btn" @click="startEdit">
              编辑资料
            </button>
            <button class="outline-btn" @click="goLibrary">
              去游戏库
            </button>
            <button class="ghost-btn" @click="openPassword">
              修改密码
            </button>
            <button class="ghost-btn" @click="logout">
              退出登录
            </button>
          </div>
        </div>
      </div>

      <div class="stat-strip">
        <div v-for="item in stats" :key="item.key" class="stat">
          <span class="stat-value">{{ statValues[item.key] }}</span>
          <span class="stat-label">{{ item.label }}</span>
        </div>
      </div>
    </section>

    <!-- 标签切换 -->
    <nav class="tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <!-- 个人资料 -->
    <section v-if="activeTab === 'info'" class="panel">
      <div class="panel-heading">
        <div>
          <span class="section-label">ACCOUNT INFO</span>
          <h2>个人资料</h2>
        </div>

        <div class="heading-actions" v-if="!editing">
          <button class="outline-btn small" @click="startEdit">修改</button>
        </div>

        <div class="heading-actions" v-else>
          <button class="ghost-btn small" @click="cancelEdit">取消</button>
          <button class="primary-btn small" @click="saveProfile">保存</button>
        </div>
      </div>

      <!-- 只读展示 -->
      <div v-if="!editing" class="info-grid">
        <div class="info-item">
          <span class="info-label">昵称</span>
          <span class="info-value">{{ profile.nickname }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">登录账号</span>
          <span class="info-value">{{ profile.username }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">邮箱</span>
          <span class="info-value">{{ profile.email }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">手机号</span>
          <span class="info-value">{{ profile.phone }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">所在地</span>
          <span class="info-value">{{ profile.location }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">注册时间</span>
          <span class="info-value">{{ displayDate(profile.joinDate) }}</span>
        </div>
        <div class="info-item full">
          <span class="info-label">个人简介</span>
          <span class="info-value">{{ profile.bio || '这个人很懒，什么都没写。' }}</span>
        </div>
      </div>

      <!-- 编辑表单 -->
      <form v-else class="edit-form" @submit.prevent="saveProfile">
        <div class="field">
          <label>昵称 <span class="required">*</span></label>
          <input v-model="draft.nickname" type="text" placeholder="请输入昵称" />
          <span v-if="!draft.nickname.trim()" class="error">
            昵称不能为空
          </span>
        </div>

        <div class="field">
          <label>登录账号</label>
          <input :value="profile.username" type="text" disabled />
          <span class="hint">账号创建后不可修改</span>
        </div>

        <div class="field">
          <label>邮箱</label>
          <input v-model="draft.email" type="email" placeholder="用于接收通知" />
        </div>

        <div class="field">
          <label>手机号</label>
          <input v-model="draft.phone" type="text" placeholder="请输入手机号" />
        </div>

        <div class="field">
          <label>所在地</label>
          <input v-model="draft.location" type="text" placeholder="例如：江西 · 南昌" />
        </div>

        <div class="field full">
          <label>个人简介</label>
          <textarea
            v-model="draft.bio"
            rows="3"
            maxlength="80"
            placeholder="简单介绍一下自己"
          ></textarea>
          <span class="hint">{{ draft.bio.length }} / 80</span>
        </div>
      </form>
    </section>

    <!-- 我的收藏 -->
    <section v-else-if="activeTab === 'favorites'" class="panel">
      <div class="panel-heading">
        <div>
          <span class="section-label">MY COLLECTIONS</span>
          <h2>我的收藏</h2>
          <p class="panel-desc">
            共收藏 {{ favoriteGuides.length }} 篇攻略、{{ favorites.length }} 款游戏
          </p>
        </div>

        <div class="fav-switch">
          <button
            :class="{ active: favTab === 'guide' }"
            @click="favTab = 'guide'"
          >
            攻略 {{ favoriteGuides.length }}
          </button>
          <button
            :class="{ active: favTab === 'game' }"
            @click="favTab = 'game'"
          >
            游戏 {{ favorites.length }}
          </button>
        </div>
      </div>

      <!-- 收藏的攻略 -->
      <template v-if="favTab === 'guide'">
        <div v-if="favoriteGuides.length" class="guide-fav-list">
          <article
            v-for="item in favoriteGuides"
            :key="item.id"
            class="guide-fav-card"
            @click="openGuide(item.id)"
          >
            <div class="guide-fav-cover" :class="item.gameCover" :style="item.gameImage ? { backgroundImage: 'url(' + JSON.stringify(item.gameImage) + ')', backgroundSize: 'cover', backgroundPosition: 'center' } : undefined">
              <span>{{ item.gameSymbol }}</span>
            </div>

            <div class="guide-fav-body">
              <div class="guide-fav-top">
                <span class="guide-fav-cat">{{ item.category }}</span>
                <span class="guide-fav-game">{{ item.gameName }}</span>
              </div>
              <h3>{{ item.title }}</h3>
              <p class="guide-fav-meta">
                {{ item.author }} · {{ item.readMin }} 分钟读完 · 更新于 {{ item.updated }}
              </p>
            </div>

            <div class="guide-fav-side">
              <span class="guide-fav-views">{{ item.views }} 阅读</span>
              <button class="remove-btn" @click.stop="removeGuideFavorite(item.id)">
                取消收藏
              </button>
            </div>
          </article>
        </div>

        <div v-else class="empty-state">
          <h3>还没有收藏任何攻略</h3>
          <p>在攻略详情页点「收藏攻略」，之后就能在这里快速找到。</p>
          <button class="primary-btn" @click="goLibrary">去游戏库看看</button>
        </div>
      </template>

      <!-- 收藏的游戏 -->
      <template v-else>
        <div v-if="favorites.length" class="fav-grid">
          <article
            v-for="game in favorites"
            :key="game.id"
            class="fav-card"
            @click="openGame(game.id)"
          >
            <div class="fav-cover" :class="game.cover" :style="game.image ? { backgroundImage: 'url(' + JSON.stringify(game.image) + ')', backgroundSize: 'cover', backgroundPosition: 'center' } : undefined">
              <span class="cover-symbol">{{ game.symbol }}</span>
              <span class="cover-name">{{ game.name }}</span>
            </div>

            <div class="fav-body">
              <h3>{{ game.name }}</h3>
              <p class="fav-category">{{ game.category }}</p>

              <div class="fav-tags">
                <span v-for="tag in game.tags" :key="tag">{{ tag }}</span>
              </div>

              <div class="fav-footer">
                <span class="fav-date">收藏于 {{ game.collectedAt }}</span>
                <button class="remove-btn" @click.stop="removeFavorite(game.id)">
                  取消收藏
                </button>
              </div>
            </div>
          </article>
        </div>

        <div v-else class="empty-state">
          <h3>还没有收藏任何游戏</h3>
          <p>在游戏库中找到喜欢的游戏，点开详情页即可收藏。</p>
          <button class="primary-btn" @click="goLibrary">去游戏库看看</button>
        </div>
      </template>
    </section>

    <!-- 浏览历史 -->
    <section v-else class="panel">
      <div class="panel-heading">
        <div>
          <span class="section-label">BROWSING HISTORY</span>
          <h2>浏览历史</h2>
          <p class="panel-desc">最近浏览的游戏与攻略</p>
        </div>

        <div class="heading-actions" v-if="history.length">
          <button
            v-if="!showClearConfirm"
            class="ghost-btn small"
            @click="showClearConfirm = true"
          >
            清空历史
          </button>

          <template v-else>
            <span class="confirm-text">确定清空？</span>
            <button class="ghost-btn small" @click="showClearConfirm = false">
              取消
            </button>
            <button class="danger-btn small" @click="clearHistory">
              确定
            </button>
          </template>
        </div>
      </div>

      <div v-if="history.length" class="history-list">
        <div
          v-for="item in history"
          :key="item.id"
          class="history-item"
          @click="openHistory(item)"
        >
          <div class="history-icon" :class="{ guide: item.type === 'guide' }">
            {{ item.type === 'game' ? '◈' : '▤' }}
          </div>

          <div class="history-content">
            <div class="history-title-row">
              <span class="type-badge" :class="item.type">
                {{ item.type === 'game' ? '游戏' : '攻略' }}
              </span>
              <h3>{{ item.title }}</h3>
            </div>
            <p>{{ item.subtitle }}</p>
          </div>

          <span class="history-time">{{ item.time.replace('T', ' ').slice(0, 16) }}</span>
          <span class="history-arrow">→</span>
        </div>
      </div>

      <div v-else class="empty-state">
        <h3>暂无浏览记录</h3>
        <p>浏览过的游戏和攻略会显示在这里。</p>
        <button class="primary-btn" @click="goLibrary">去逛逛</button>
      </div>
    </section>

    <!-- 修改密码 -->
    <div v-if="showPassword" class="modal-mask" @click.self="closePassword">
      <div class="modal-card">
        <div class="modal-head">
          <h3>修改密码</h3>
          <button class="modal-close" @click="closePassword">×</button>
        </div>

        <form v-if="!pwdDone" class="edit-form" @submit.prevent="submitPassword">
          <div class="field">
            <label>原密码 <span class="required">*</span></label>
            <input v-model="pwdForm.oldPassword" type="password" autocomplete="current-password" placeholder="请输入当前密码" />
          </div>
          <div class="field">
            <label>新密码 <span class="required">*</span></label>
            <input v-model="pwdForm.newPassword" type="password" autocomplete="new-password" placeholder="6-32 位字符" />
          </div>
          <div class="field">
            <label>确认新密码 <span class="required">*</span></label>
            <input v-model="pwdForm.confirm" type="password" autocomplete="new-password" placeholder="再输入一次新密码" />
            <span v-if="pwdError" class="error">{{ pwdError }}</span>
          </div>

          <p class="modal-tip">修改成功后需要重新登录。</p>

          <div class="modal-actions">
            <button type="button" class="ghost-btn small" @click="closePassword">取消</button>
            <button type="submit" class="primary-btn small" :disabled="pwdSaving">
              {{ pwdSaving ? '提交中…' : '确认修改' }}
            </button>
          </div>
        </form>

        <div v-else class="modal-done">
          <p>密码已修改成功，请使用新密码重新登录。</p>
          <button class="primary-btn" @click="relogin">去登录</button>
        </div>
      </div>
    </div>

    <!-- 保存提示 -->
    <transition name="toast">
      <div v-if="savedTip" class="toast">
        资料已保存（当前为本地数据，接口接入后同步到后端）
      </div>
    </transition>
  </div>
</template>

<style scoped>
.avatar img { width: 100%; height: 100%; border-radius: inherit; object-fit: cover; }
.profile-page {
  color: var(--gw-text);
  background: var(--gw-bg);
  min-height: 100vh;
}

/* 顶部用户卡片 */
.profile-hero {
  position: relative;
  overflow: hidden;
  padding: 34px;
  border: 1px solid var(--gw-line);
  border-radius: 20px;
  background:
    radial-gradient(ellipse at 88% 10%, #2b4680 0%, transparent 52%),
    linear-gradient(125deg, var(--gw-surface), var(--gw-nav));
}

/* 右上角装饰符号，填补留白 */
.hero-deco {
  position: absolute;
  right: 40px;
  top: 50%;
  transform: translateY(-60%);
  font-size: 220px;
  line-height: 1;
  color: #6f95ff;
  opacity: 0.07;
  pointer-events: none;
  user-select: none;
}

.hero-main {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 32px;
}

.avatar {
  width: 96px;
  height: 96px;
  border-radius: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #ffffff;
  background: linear-gradient(145deg, var(--gw-primary), var(--gw-violet));
  box-shadow: 0 12px 28px #0000004d, inset 0 0 24px #ffffff26;
}

.identity {
  flex: 1;
  min-width: 0;
}

.eyebrow,
.section-label {
  color: var(--gw-link);
  font-size: 12px;
  letter-spacing: 2px;
}

.identity h1 {
  font-size: clamp(24px, 2.6vw, 32px);
  margin: 12px 0 8px;
}

.handle {
  color: var(--gw-subtle);
  font-size: 13px;
  margin: 0;
}

.bio {
  color: var(--gw-muted);
  font-size: 14px;
  margin: 14px 0 0;
  line-height: 1.8;
}

.dot {
  margin: 0 8px;
  color: var(--gw-border-strong);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

button {
  cursor: pointer;
  border: none;
  font-size: 14px;
  font-family: inherit;
}

.primary-btn {
  padding: 11px 22px;
  color: white;
  border-radius: 9px;
  background: var(--gw-action-gradient);
}

.primary-btn.small {
  padding: 8px 16px;
  font-size: 13px;
}

.outline-btn {
  padding: 11px 22px;
  color: var(--gw-muted);
  background: transparent;
  border: 1px solid var(--gw-border-strong);
  border-radius: 9px;
}

.outline-btn.small {
  padding: 8px 16px;
  font-size: 13px;
}

.outline-btn:hover {
  border-color: var(--gw-link);
  color: var(--gw-link);
}

.ghost-btn {
  padding: 11px 12px;
  background: transparent;
  color: var(--gw-link);
}

.ghost-btn.small {
  padding: 8px 12px;
  font-size: 13px;
}

.danger-btn {
  padding: 8px 16px;
  border-radius: 8px;
  background: #7a2f3a;
  color: #ffd7dd;
  border: 1px solid #a14552;
}

.danger-btn.small {
  font-size: 13px;
}

/* 统计条 */
.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-top: 30px;
  padding-top: 24px;
  border-top: 1px solid var(--gw-line);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--gw-muted);
}

.stat-label {
  color: var(--gw-subtle);
  font-size: 12px;
}

/* 标签栏 */
.tabs {
  display: flex;
  gap: 8px;
  margin: 24px 0 22px;
  border-bottom: 1px solid var(--gw-line);
}

.tab {
  padding: 12px 20px;
  background: transparent;
  color: var(--gw-subtle);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tab:hover {
  color: var(--gw-link);
}

.tab.active {
  color: var(--gw-link);
  border-bottom-color: var(--gw-link);
}

/* 面板 */
.panel {
  background: var(--gw-surface);
  border: 1px solid var(--gw-border);
  border-radius: 14px;
  padding: 26px;
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
}

.panel-heading h2 {
  font-size: 21px;
  margin: 10px 0 0;
}

.panel-desc {
  color: var(--gw-subtle);
  font-size: 13px;
  margin: 8px 0 0;
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.confirm-text {
  color: #ffb3bd;
  font-size: 13px;
}

/* 资料展示 */
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  background: var(--gw-line);
  border: 1px solid var(--gw-line);
  border-radius: 11px;
  overflow: hidden;
}

.info-item {
  background: var(--gw-nav);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.info-item.full {
  grid-column: 1 / -1;
}

.info-label {
  color: var(--gw-subtle);
  font-size: 12px;
}

.info-value {
  color: var(--gw-muted);
  font-size: 14px;
  word-break: break-all;
}

/* 编辑表单 */
.edit-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field.full {
  grid-column: 1 / -1;
}

.field label {
  font-size: 13px;
  color: var(--gw-muted);
}

.required {
  color: #ff8a9a;
}

.field input,
.field textarea {
  background: var(--gw-nav);
  border: 1px solid var(--gw-border);
  border-radius: 9px;
  padding: 11px 13px;
  color: var(--gw-text);
  font-size: 14px;
  font-family: inherit;
  outline: none;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  border-color: #5d8ce8;
}

.field input:disabled {
  color: var(--gw-subtle);
  background: var(--gw-nav);
  cursor: not-allowed;
}

.hint {
  color: var(--gw-subtle);
  font-size: 12px;
}

.error {
  color: #ff8a9a;
  font-size: 12px;
}

/* 收藏 */
.fav-switch {
  display: flex;
  gap: 6px;
  padding: 4px;
  border-radius: 9px;
  background: var(--gw-nav);
  border: 1px solid var(--gw-line);
}

.fav-switch button {
  padding: 7px 14px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--gw-subtle);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.fav-switch button:hover {
  color: var(--gw-muted);
}

.fav-switch button.active {
  background: #24405f;
  color: var(--gw-link);
}

/* 收藏的攻略 */
.guide-fav-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.guide-fav-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 12px;
  background: var(--gw-nav);
  border: 1px solid var(--gw-line);
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.guide-fav-card:hover {
  border-color: #3d5b85;
  transform: translateY(-1px);
}

.guide-fav-cover {
  flex: 0 0 58px;
  width: 58px;
  height: 58px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #ffffffdd;
  box-shadow: inset 0 0 18px #00000055;
}

.guide-fav-body {
  flex: 1;
  min-width: 0;
}

.guide-fav-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 7px;
}

.guide-fav-cat {
  padding: 3px 9px;
  border-radius: 5px;
  background: var(--gw-surface-hover);
  color: var(--gw-link);
  font-size: 11px;
}

.guide-fav-game {
  color: var(--gw-link);
  font-size: 12px;
}

.guide-fav-body h3 {
  margin: 0 0 7px;
  font-size: 15.5px;
  font-weight: 600;
  color: var(--gw-text);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guide-fav-meta {
  margin: 0;
  font-size: 12px;
  color: var(--gw-subtle);
}

.guide-fav-side {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.guide-fav-views {
  font-size: 12px;
  color: var(--gw-subtle);
}

.fav-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.fav-card {
  background: var(--gw-nav);
  border: 1px solid var(--gw-border);
  border-radius: 13px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s;
}

.fav-card:hover {
  transform: translateY(-4px);
  border-color: #6992fa;
}

.fav-cover {
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.cover-symbol {
  font-size: 54px;
  color: #ffffff8c;
}

.cover-name {
  position: absolute;
  bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  text-shadow: 0 2px 10px #000000aa;
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

.fav-body {
  padding: 15px 16px;
}

.fav-body h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.fav-category {
  color: var(--gw-link);
  font-size: 12px;
  margin: 0;
}

.fav-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 12px 0;
}

.fav-tags span {
  padding: 3px 8px;
  border-radius: 5px;
  background: var(--gw-surface-hover);
  color: var(--gw-muted);
  font-size: 11px;
}

.fav-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  border-top: 1px solid var(--gw-border);
  padding-top: 11px;
}

.fav-date {
  color: var(--gw-subtle);
  font-size: 11px;
}

.remove-btn {
  background: transparent;
  color: #ff9aa6;
  font-size: 12px;
  padding: 0;
}

.remove-btn:hover {
  color: #ffbcc5;
  text-decoration: underline;
}

/* 浏览历史 */
.history-list {
  display: grid;
  gap: 10px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  border-radius: 11px;
  border: 1px solid var(--gw-border);
  background: var(--gw-nav);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.history-item:hover {
  border-color: #5e8cff;
  background: var(--gw-surface);
}

.history-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  background: var(--gw-surface-hover);
  color: var(--gw-link);
}

.history-icon.guide {
  background: #2a2f52;
  color: var(--gw-accent);
}

.history-content {
  flex: 1;
  min-width: 0;
}

.history-title-row {
  display: flex;
  align-items: center;
  gap: 9px;
}

.history-title-row h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  background: #1f3f6b;
  color: var(--gw-link);
}

.type-badge.guide {
  background: #33305e;
  color: var(--gw-accent);
}

.history-content p {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--gw-subtle);
}

.history-time {
  color: var(--gw-subtle);
  font-size: 12px;
  flex-shrink: 0;
}

.history-arrow {
  color: var(--gw-link);
  font-size: 18px;
  flex-shrink: 0;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  border: 1px dashed var(--gw-border);
  border-radius: 13px;
}

.empty-state h3 {
  margin: 0 0 10px;
  font-size: 17px;
}

.empty-state p {
  color: var(--gw-muted);
  font-size: 13px;
  margin: 0 0 18px;
}

/* 保存提示 */
.toast {
  position: fixed;
  left: 50%;
  bottom: 40px;
  transform: translateX(-50%);
  padding: 12px 22px;
  border-radius: 10px;
  background: var(--gw-surface-raised);
  border: 1px solid var(--gw-border-strong);
  color: var(--gw-muted);
  font-size: 13px;
  box-shadow: 0 12px 30px #00000066;
  z-index: 50;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}

@media (max-width: 1000px) {
  .fav-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .profile-hero {
    padding: 22px;
  }

  .hero-main {
    flex-direction: column;
    gap: 20px;
  }

  .avatar {
    width: 74px;
    height: 74px;
    border-radius: 20px;
    font-size: 25px;
  }

  .stat-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .tabs {
    overflow-x: auto;
  }

  .tab {
    padding: 12px 14px;
    white-space: nowrap;
  }

  .panel {
    padding: 20px;
  }

  .panel-heading {
    flex-direction: column;
  }

  .info-grid,
  .edit-form {
    grid-template-columns: 1fr;
  }

  .fav-grid {
    grid-template-columns: 1fr;
  }

  .history-time {
    display: none;
  }

  .history-title-row h3 {
    white-space: normal;
  }

  .guide-fav-card {
    align-items: flex-start;
    gap: 12px;
    padding: 14px;
  }

  .guide-fav-cover {
    flex: 0 0 44px;
    width: 44px;
    height: 44px;
    font-size: 18px;
  }

  .guide-fav-body h3 {
    white-space: normal;
  }

  .guide-fav-side {
    gap: 6px;
  }
}

/* ===== 修改密码弹窗 ===== */
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(4, 8, 16, 0.72);
  backdrop-filter: blur(3px);
}
.modal-card {
  width: 100%;
  max-width: 420px;
  padding: 22px 24px 20px;
  border-radius: 16px;
  background: var(--gw-surface);
  border: 1px solid var(--gw-border);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.modal-head h3 {
  margin: 0;
  font-size: 17px;
  color: var(--gw-text);
}
.modal-close {
  width: 26px;
  height: 26px;
  line-height: 22px;
  text-align: center;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--gw-subtle);
  font-size: 18px;
  cursor: pointer;
}
.modal-close:hover {
  background: var(--gw-line);
  color: var(--gw-text);
}
.modal-card .field {
  margin-bottom: 14px;
}
.modal-tip {
  margin: 4px 0 16px;
  font-size: 12px;
  color: var(--gw-subtle);
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.modal-done {
  text-align: center;
  padding: 8px 0 4px;
}
.modal-done p {
  margin: 0 0 18px;
  color: var(--gw-muted);
  line-height: 1.7;
}
</style>
