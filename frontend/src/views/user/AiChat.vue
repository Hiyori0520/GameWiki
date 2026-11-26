<template>
  <div class="chat-page">
    <!-- 左侧：历史会话 -->
    <aside class="chat-side">
      <div class="side-head">
        <span class="side-title-text">历史对话</span>
        <el-button size="small" type="primary" plain @click="newChat">新对话</el-button>
      </div>

      <div v-loading="loadingSessions" class="side-list">
        <div
          v-for="s in sessions"
          :key="s.sessionId"
          class="side-item"
          :class="{ active: s.sessionId === activeId }"
          @click="openSession(s)"
        >
          <div class="side-item-title">{{ s.title }}</div>
          <div class="side-item-meta">{{ s.lastAt }} · {{ s.messageCount }} 条</div>
          <span class="side-del" title="删除该会话" @click.stop="removeSession(s)">×</span>
        </div>
        <div v-if="!loadingSessions && !sessions.length" class="side-empty">
          还没有历史对话<br />提问后会自动记录
        </div>
      </div>
    </aside>

    <!-- 右侧：对话区 -->
    <div class="chat-main">
      <header class="chat-header">
        <div class="left">
          <span class="logo">GameWiki</span>
          <span class="sep">/</span>
          <span class="title">AI 攻略助手</span>
          <el-tag size="small" type="success" effect="plain">检索本地攻略库</el-tag>
        </div>
        <div class="right">
          <el-button size="small" text @click="goBack">返回</el-button>
        </div>
      </header>

      <main ref="listRef" class="chat-body">
        <div v-for="(msg, idx) in messages" :key="idx" :class="['row', msg.role]">
          <div class="bubble">
            <div class="role">{{ msg.role === 'user' ? nickname : 'AI 攻略助手' }}</div>
            <MarkdownView v-if="msg.role === 'ai'" :content="msg.content" />
            <div v-else class="plain">{{ msg.content }}</div>
          </div>
        </div>

        <div v-if="waiting" class="row ai">
          <div class="bubble thinking">正在检索本地攻略库，请稍候…</div>
        </div>
      </main>

      <footer class="chat-footer">
        <div class="quick">
          <span>试试：</span>
          <el-button
            v-for="q in quickQuestions"
            :key="q"
            size="small"
            round
            @click="ask(q)"
          >{{ q }}</el-button>
        </div>
        <div class="input-row">
          <el-input
            v-model="question"
            size="large"
            placeholder="输入游戏打法、BOSS 机制或装备数值问题，回车发送"
            @keyup.enter="send"
          />
          <el-button type="primary" size="large" :loading="thinking" @click="send">发送</el-button>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import MarkdownView from '../../components/MarkdownView.vue'

const GREETING = '你好，我是 AI 攻略助手。我接入了本地攻略库与装备数值库，可以回答载具机动、BOSS 打法、武器面板这类问题。'
const STORAGE_KEY = 'kb_ai_session'
// 流式请求走 fetch，需要拼完整地址；未配置时走同源（开发环境由 vite proxy 转发）
const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

const router = useRouter()
const listRef = ref(null)
const question = ref('')
const thinking = ref(false)
// 首个字符到达前显示"稍候"，开始吐字后收起
const waiting = ref(false)
const sessionId = ref('')
const nickname = ref('我')

const sessions = ref([])
const activeId = ref('')
const loadingSessions = ref(false)

const messages = ref([{ role: 'ai', content: GREETING }])

const quickQuestions = [
  '攻击机被咬尾怎么摆脱',
  '黑暗之魂3 前期加点顺序',
  '洛斯里克骑士直剑面板'
]

onMounted(async () => {
  const user = JSON.parse(localStorage.getItem('kb_user') || '{}')
  if (user.nickname) nickname.value = user.nickname

  await loadSessions()

  // 刷新后自动续上上次的会话
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && sessions.value.some((s) => s.sessionId === saved)) {
    await openSession({ sessionId: saved })
  }
})

const scrollToBottom = async () => {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

const loadSessions = async () => {
  loadingSessions.value = true
  try {
    const res = await request.get('/api/ai/sessions')
    sessions.value = res.data || []
  } finally {
    loadingSessions.value = false
  }
}

const openSession = async (session) => {
  if (thinking.value) return
  try {
    const res = await request.get(`/api/ai/sessions/${session.sessionId}`)
    messages.value = (res.data || []).map((m) => ({ role: m.role, content: m.content }))
    sessionId.value = session.sessionId
    activeId.value = session.sessionId
    localStorage.setItem(STORAGE_KEY, session.sessionId)
    scrollToBottom()
  } catch (e) {
    // 会话已被删除时，退回新对话状态
    newChat()
  }
}

const newChat = () => {
  sessionId.value = ''
  activeId.value = ''
  messages.value = [{ role: 'ai', content: GREETING }]
  localStorage.removeItem(STORAGE_KEY)
}

const removeSession = async (session) => {
  try {
    await ElMessageBox.confirm(`确定删除「${session.title}」这段对话？`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await request.delete(`/api/ai/sessions/${session.sessionId}`)
  ElMessage.success('已删除')
  if (activeId.value === session.sessionId) {
    newChat()
  }
  await loadSessions()
}

const send = async () => {
  const q = question.value.trim()
  if (!q || thinking.value) return
  await ask(q)
}

const ask = async (q) => {
  if (thinking.value) return

  messages.value.push({ role: 'user', content: q })
  // 先把 AI 气泡占位，后续按流式往里追加，避免一条一条蹦新气泡
  const reply = reactive({ role: 'ai', content: '' })
  messages.value.push(reply)
  question.value = ''
  thinking.value = true
  waiting.value = true
  scrollToBottom()

  const isNewSession = !sessionId.value
  let finished = false

  try {
    const res = await fetch(`${API_BASE}/api/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: localStorage.getItem('kb_token') || ''
      },
      body: JSON.stringify({ sessionId: sessionId.value || null, question: q })
    })
    if (!res.ok || !res.body) throw new Error(`AI 服务返回 ${res.status}`)

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 以空行分帧；最后一段可能不完整，留到下一轮
      const frames = buffer.split('\n\n')
      buffer = frames.pop() || ''

      for (const frame of frames) {
        const eventMatch = /^event:(.*)$/m.exec(frame)
        const event = eventMatch ? eventMatch[1].trim() : ''
        const data = frame.split('\n')
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5))
          .join('')
        if (!data) continue

        const text = JSON.parse(data)
        if (event === 'session') {
          sessionId.value = text
          activeId.value = text
          localStorage.setItem(STORAGE_KEY, text)
        } else if (event === 'chunk') {
          waiting.value = false
          reply.content += text
          scrollToBottom()
        } else if (event === 'error') {
          throw new Error(text)
        } else if (event === 'done') {
          finished = true
        }
      }
    }

    if (!reply.content) throw new Error('模型没有返回内容')
    if (!finished) finished = true
  } catch (e) {
    if (!reply.content) {
      reply.content = `这次没能取到回答：${e.message || '请稍后再试'}`
    }
  } finally {
    thinking.value = false
    waiting.value = false
    // 新会话首问后刷新列表，让标题和条数及时出现
    if (isNewSession && sessionId.value) await loadSessions()
    scrollToBottom()
  }
}

const goBack = () => {
  if (window.history.length > 1) router.back()
  else router.push('/')
}
</script>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  background: var(--gw-bg);
}

/* ===== 左侧会话列表 ===== */
.chat-side {
  width: 248px;
  flex: none;
  display: flex;
  flex-direction: column;
  background: var(--gw-surface);
  border-right: 1px solid var(--gw-line);
}
.side-head {
  height: 56px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--gw-line);
}
.side-title-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--gw-text);
}
.side-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.side-item {
  position: relative;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s;
}
.side-item:hover {
  background: var(--gw-surface-raised);
}
.side-item.active {
  background: var(--gw-surface-hover);
}
.side-item-title {
  font-size: 13px;
  color: var(--gw-text);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  padding-right: 16px;
}
.side-item-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--gw-subtle);
}
.side-del {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 16px;
  height: 16px;
  line-height: 14px;
  text-align: center;
  border-radius: 50%;
  color: var(--gw-subtle);
  font-size: 14px;
  visibility: hidden;
}
.side-item:hover .side-del {
  visibility: visible;
}
.side-del:hover {
  background: var(--gw-danger-tint);
  color: var(--gw-danger);
}
.side-empty {
  padding: 30px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--gw-subtle);
  line-height: 1.8;
}

/* ===== 右侧对话区 ===== */
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: var(--gw-surface);
  border-bottom: 1px solid var(--gw-line);
  flex: none;
}
.chat-header .left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-header .logo {
  font-weight: 700;
  color: var(--gw-accent);
}
.chat-header .sep {
  color: var(--gw-subtle);
}
.chat-header .title {
  font-size: 15px;
  font-weight: 600;
  color: var(--gw-text);
  margin-right: 6px;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  max-width: 900px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}
.row {
  display: flex;
  margin-bottom: 18px;
}
.row.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 78%;
  background: var(--gw-surface);
  border: 1px solid var(--gw-line);
  border-radius: 12px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px #080e203d;
}
.row.user .bubble {
  background: var(--gw-primary);
  border-color: var(--gw-primary);
}
.row.user .plain {
  color: #fff;
  white-space: pre-wrap;
  line-height: 1.7;
}
.role {
  font-size: 12px;
  color: var(--gw-subtle);
  margin-bottom: 6px;
}
.row.user .role {
  color: rgba(255, 255, 255, 0.8);
  text-align: right;
}
.thinking {
  color: var(--gw-muted);
  font-size: 14px;
}
.chat-footer {
  flex: none;
  background: var(--gw-surface);
  border-top: 1px solid var(--gw-line);
  padding: 12px 24px 18px;
}
.quick {
  max-width: 900px;
  margin: 0 auto 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--gw-muted);
}
.input-row {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  gap: 12px;
}
</style>
